package autoeq.modules.trigger;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.Set;

import autoeq.ExpressionEvaluator;
import autoeq.ThreadScoped;
import autoeq.eq.ChatListener;
import autoeq.eq.Command;
import autoeq.eq.EverquestSession;
import autoeq.eq.ExpressionRoot;
import autoeq.eq.Me;
import autoeq.eq.Module;
import autoeq.eq.Spawn;
import autoeq.eq.SpawnType;
import autoeq.ini.Section;
import autoeq.modules.rezwait.LootCorpseCommand;
import autoeq.modules.target.TargetModule;

import com.google.inject.Inject;

@ThreadScoped
public class TriggerModule implements Module {
  private final EverquestSession session;
  private final List<Trigger> triggers = new ArrayList<>();
  private final TargetModule targetModule;
  private final Set<String> activeProfiles = new HashSet<>();

  private final Map<Trigger, Long> cancelActions = new HashMap<>();
  private final Map<Pattern, Long> lastSeenTriggerTimes = new HashMap<>();

  private int zoneId = -1;

  @Inject
  public TriggerModule(EverquestSession session, TargetModule targetModule) {
    this.session = session;
    this.targetModule = targetModule;

    activeProfiles.add("--no active profile--");

    session.addChatListener(new ChatListener() {
      private final Pattern PATTERN = Pattern.compile(".*");

      @Override
      public Pattern getFilter() {
        return PATTERN;
      }

      @Override
      public void match(Matcher matcher) {
        for(Trigger trigger : triggers) {
          Matcher subMatcher = trigger.getTextPattern().matcher(matcher.group());

          if(subMatcher.matches()) {
            lastSeenTriggerTimes.put(trigger.getTextPattern(), System.currentTimeMillis());
          }

          subMatcher = trigger.getExpireTextPattern().matcher(matcher.group());

          if(subMatcher.matches()) {
            lastSeenTriggerTimes.put(trigger.getExpireTextPattern(), System.currentTimeMillis());
          }
        }
      }
    });
  }

  @Override
  public List<Command> pulse() {
    Me me = session.getMe();

    if(me.isAlive() && me.getType() == SpawnType.PC) {
      Spawn mainAssist = targetModule.getMainAssist();
      Spawn mainTarget = targetModule.getMainAssistTarget();

      /*
       * Cancel any running actions when duration expires
       */

      for(Iterator<Map.Entry<Trigger, Long>> iterator = cancelActions.entrySet().iterator(); iterator.hasNext();) {
        Entry<Trigger, Long> next = iterator.next();

        if(next.getValue() < System.currentTimeMillis()) {
          Trigger trigger = next.getKey();

          session.echo("\\ay[Trigger] \\awTimed out trigger: " + trigger.getName());
          session.doActions(trigger.getCancelActions(), null, mainTarget, mainAssist);

          iterator.remove();
        }
      }

      /*
       * Update profiles
       */

      if(!session.getActiveProfiles().equals(activeProfiles) || session.getZoneId() != zoneId) {
        activeProfiles.clear();
        activeProfiles.addAll(session.getActiveProfiles());
        triggers.clear();

        zoneId = session.getZoneId();

        for(Section section : session.getIni()) {
          if(section.getName().startsWith("Trigger.")) {
            int triggerZoneId = Integer.parseInt(section.getDefault("ZoneID", "0"));

            if(triggerZoneId == 0 || triggerZoneId == zoneId) {
              Trigger trigger = new Trigger(
                section.getName().substring(8),
                section.get("Text"),
                section.get("ExpireText"),
                section.getDefault("Blocking", "false"),
                section.getAll("Action"),
                section.getAll("ExpireAction"),
                section.getDefault("Reaction", "0"),
                section.getDefault("ExpiresAfter", "600"),
                section.getAll("Profile"),
                section.getAll("Condition")
              );

              triggers.add(trigger);

              session.echo("\\ay[Trigger] \\a-wActivated trigger: " + trigger.getName());
            }
          }
        }
      }

      /*
       * Check for active Triggers
       */

      for(Trigger trigger : triggers) {
        if(session.isProfileActive(trigger.getProfiles())) {
          if(trigger.matchesConditions(session, mainTarget, mainAssist)) {
            Long lastTriggerTime = lastSeenTriggerTimes.get(trigger.getTextPattern());

            if(lastTriggerTime != null) {
              if(System.currentTimeMillis() - lastTriggerTime >= trigger.getReactionMillis()) {
                lastSeenTriggerTimes.remove(trigger.getTextPattern());

                session.echo("\\ay[Trigger] \\awFired trigger: " + trigger.getName());

                session.doActions(trigger.getActions(), null, mainTarget, mainAssist);
                cancelActions.put(trigger, System.currentTimeMillis() + trigger.getDurationMillis());
              }
            }

            Long lastExpireTriggerTime = lastSeenTriggerTimes.remove(trigger.getExpireTextPattern());

            if(lastExpireTriggerTime != null) {
              session.echo("\\ay[Trigger] \\awExpired trigger: " + trigger.getName());

              lastSeenTriggerTimes.remove(trigger.getExpireTextPattern());

              session.doActions(trigger.getCancelActions(), null, mainTarget, mainAssist);
              cancelActions.remove(trigger);
            }
          }
        }
      }

      /*
       * Check if we need to block other modules
       */

      for(Trigger trigger : cancelActions.keySet()) {
        if(trigger.isBlocking()) {
          return new ArrayList<>(Arrays.asList(new Command[] {new LootCorpseCommand()}));
        }
      }
    }

    return Collections.emptyList();
  }

  private static class Trigger {
    private final String name;
    private final Pattern triggerText;
    private final Pattern cancelTriggerText;
    private final List<String> conditions;
    private final List<String> profiles;
    private final List<String> actions;
    private final List<String> cancelActions;
    private final long reactionMillis;
    private final long durationMillis;
    private final boolean blocking;

    public Trigger(String name, String triggerText, String cancelTriggerText, String blocking, List<String> actions, List<String> cancelActions, String reaction, String duration, List<String> profiles, List<String> conditions) {
      this.name = name;
      this.triggerText = Pattern.compile(triggerText);
      this.cancelTriggerText = Pattern.compile(cancelTriggerText);
      this.actions = actions;
      this.cancelActions = cancelActions;
      this.durationMillis = (long)(Double.parseDouble(duration) * 1000L);
      this.reactionMillis = (long)(Double.parseDouble(reaction) * 1000L);
      this.profiles = profiles;
      this.conditions = conditions;
      this.blocking = Boolean.parseBoolean(blocking);
    }

    /**
     * Checks if target is a valid target.
     */
    public boolean matchesConditions(EverquestSession session, Spawn mainTarget, Spawn mainAssist) {
      return ExpressionEvaluator.evaluate(conditions, new ExpressionRoot(session, session.getMe().getTarget(), mainTarget, mainAssist, null), this);
    }

    public List<String> getActions() {
      return actions;
    }

    public List<String> getCancelActions() {
      return cancelActions;
    }

    public Pattern getTextPattern() {
      return triggerText;
    }

    public Pattern getExpireTextPattern() {
      return cancelTriggerText;
    }

    public long getDurationMillis() {
      return durationMillis;
    }

    public long getReactionMillis() {
      return reactionMillis;
    }

    public List<String> getProfiles() {
      return new ArrayList<>(profiles);
    }

    public boolean isBlocking() {
      return blocking;
    }

    public String getName() {
      return name;
    }

    @Override
    public String toString() {
      return String.format("Action(" + name + ")");
    }
  }

  @Override
  public int getBurstCount() {
    return 8;
  }
}
