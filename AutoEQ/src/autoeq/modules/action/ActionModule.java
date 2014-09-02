package autoeq.modules.action;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import autoeq.ExpressionEvaluator;
import autoeq.ThreadScoped;
import autoeq.eq.Command;
import autoeq.eq.EverquestSession;
import autoeq.eq.ExpressionRoot;
import autoeq.eq.Me;
import autoeq.eq.Module;
import autoeq.eq.Spawn;
import autoeq.eq.SpawnType;
import autoeq.ini.Section;
import autoeq.modules.target.TargetModule;

import com.google.inject.Inject;

@ThreadScoped
public class ActionModule implements Module {
  private final EverquestSession session;
  private final List<Action> actions = new ArrayList<>();
  private final TargetModule targetModule;
  private final Set<String> activeProfiles = new HashSet<>();

  private final Map<List<String>, Long> cancelActions = new HashMap<>();
  private final Map<Action, Long> delayTimes = new HashMap<>();

  @Inject
  public ActionModule(EverquestSession session, TargetModule targetModule) {
    this.session = session;
    this.targetModule = targetModule;

    activeProfiles.add("--no active profile--");
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

      for(Iterator<Map.Entry<List<String>, Long>> iterator = cancelActions.entrySet().iterator(); iterator.hasNext();) {
        Entry<List<String>, Long> next = iterator.next();

        if(next.getValue() < System.currentTimeMillis()) {
          session.doActions(next.getKey(), null, mainTarget, mainAssist);
          iterator.remove();
        }
      }

      /*
       * Update profiles
       */

      if(!session.getActiveProfiles().equals(activeProfiles)) {
        activeProfiles.clear();
        activeProfiles.addAll(session.getActiveProfiles());
        actions.clear();

        for(Section section : session.getIni()) {
          if(section.getName().startsWith("Action.")) {
            actions.add(new Action(section.getName().substring(7), section.getAll("Action"), section.getAll("ExpireAction"), section.getDefault("ExpiresAfter", "600"), section.getAll("Profile"), section.getAll("Condition"), Long.parseLong(section.getDefault("Delay", "500"))));
          }
        }
      }

      /*
       * Execute any actions matching conditions
       */

      for(Action action : actions) {
        Long checkDelay = delayTimes.get(action);

        if(checkDelay == null || checkDelay < System.currentTimeMillis()) {
          if(session.isProfileActive(action.getProfiles())) {
            if(action.matchesConditions(session, mainTarget, mainAssist)) {
              session.doActions(action.getActions(), null, mainTarget, mainAssist);
              cancelActions.put(action.getCancelActions(), System.currentTimeMillis() + action.getDurationMillis());
            }
          }

          delayTimes.put(action, System.currentTimeMillis() + action.getCheckDelay());
        }
      }
    }

    return Collections.emptyList();
  }

  private static class Action {
    private final String name;
    private final List<String> conditions;
    private final List<String> profiles;
    private final List<String> actions;
    private final List<String> cancelActions;
    private final long durationMillis;
    private final long checkDelay;

    public Action(String name, List<String> actions, List<String> cancelActions, String duration, List<String> profiles, List<String> conditions, long checkDelay) {
      this.name = name;
      this.actions = actions;
      this.cancelActions = cancelActions;
      this.checkDelay = checkDelay;
      this.durationMillis = (long)(Double.parseDouble(duration) * 1000L);
      this.profiles = profiles;
      this.conditions = conditions;
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

    public long getDurationMillis() {
      return durationMillis;
    }

    public long getCheckDelay() {
      return checkDelay;
    }

    public List<String> getProfiles() {
      return new ArrayList<>(profiles);
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
