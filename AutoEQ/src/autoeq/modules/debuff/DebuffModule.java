package autoeq.modules.debuff;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;

import autoeq.ExpressionEvaluator;
import autoeq.TargetPattern;
import autoeq.ThreadScoped;
import autoeq.effects.Effect;
import autoeq.effects.Effect.Type;
import autoeq.eq.ActivateEffectCommand;
import autoeq.eq.Command;
import autoeq.eq.EverquestSession;
import autoeq.eq.ExpressionRoot;
import autoeq.eq.Me;
import autoeq.eq.MemorizeCommand;
import autoeq.eq.Module;
import autoeq.eq.Priority;
import autoeq.eq.Spawn;
import autoeq.eq.SpawnType;
import autoeq.eq.SpellLine;
import autoeq.eq.SpellParser;
import autoeq.eq.TargetType;
import autoeq.ini.Section;
import autoeq.modules.buff.EffectSet;
import autoeq.modules.target.TargetModule;

import com.google.inject.Inject;

@ThreadScoped
public class DebuffModule implements Module {
  private final EverquestSession session;
  private final List<DebuffLine> debuffLines = new ArrayList<>();
  private final TargetModule targetModule;
  private final Set<String> activeProfiles = new HashSet<>();

  private Spawn mainTarget;
  private Spawn mainAssist;

  @Inject
  public DebuffModule(EverquestSession session, TargetModule targetModule) {
    this.session = session;
    this.targetModule = targetModule;

    activeProfiles.add("--no active profile--");
  }

  @Override
  public List<Command> pulse() {
    List<Command> commands = new ArrayList<>();
    Me me = session.getMe();

    if(!me.isMoving() && me.getType() == SpawnType.PC) {

      if(!session.getActiveProfiles().equals(activeProfiles)) {
        activeProfiles.clear();
        activeProfiles.addAll(session.getActiveProfiles());
        debuffLines.clear();

        for(Section section : session.getIni()) {
          if(section.getName().startsWith("Debuff.")) {
            List<EffectSet> effectSets = SpellParser.parseSpells(session, section, 100);

            if(effectSets.size() > 0) {
              Effect effect = effectSets.get(0).getSingleOrGroup();
              int gem = section.get("Gem") != null ? Integer.parseInt(section.get("Gem")) : 0;
              debuffLines.add(new DebuffLine(gem, effect, section.get("ValidTargets"), section.get("Profile"), Priority.decodePriority(session, effect, section.get("Priority"), 200), section.get("TargetType").equals("main"), section.getAll("Condition")));
            }
          }
        }
      }

      // 0. Mem debuff spells
      // 1. Keep a list of nearby mobs
      // 2. Keep track of their debuffs
      // 3. (Re)Debuff something when needed.

      /*
       * Ensure Debuffs are memmed
       */

      for(DebuffLine debuffLine : debuffLines) {
        if(session.isProfileActive(debuffLine.getProfile()) && !me.inCombat()) {
          Effect effect = debuffLine.getEffect();

          if(effect.getType() == Effect.Type.SPELL || effect.getType() == Type.SONG) {
            int gem = me.getGem(effect.getSpell());

            if(gem == 0) {
              commands.add(new MemorizeCommand(1000, "DEBUFF", effect.getSpell(), debuffLine.getGem()));
              return commands;
            }
            else {
              me.lockSpellSlot(gem);
            }
          }
        }
      }

      /*
       * Determine if we're ready to cast (we do this before attempting to assist to prevent useless assists).
       */

      if(mainTarget != null && !mainTarget.isEnemy()) {
        mainTarget = null;
      }

      LinkedHashMap<DebuffLine, List<Spawn>> targetLists = new LinkedHashMap<>();
      boolean assisted = false;

      for(DebuffLine debuffLine : debuffLines) {
        if(session.isProfileActive(debuffLine.getProfile())) {
          Effect effect = debuffLine.getEffect();

          if(effect.isReady()) {

            if(!assisted) {
              mainAssist = targetModule.getMainAssist();
              mainTarget = targetModule.getFromAssist();
              assisted = true;
            }

            for(Spawn spawn : session.getSpawns()) {
              if((spawn.getType() == SpawnType.NPC || !effect.getSpell().isDetrimental()) && (effect.getSpell().getRange() == 0.0 || spawn.getDistance() < effect.getSpell().getRange())) {
                if(!spawn.isIgnored()) {
    //              if(spawn.getDistance() < 75.0) {
    //                System.out.println(spawn + " ttl " + spawn.getTimeToLive());
    //              }

                  if(!spawn.getSpellEffects().contains(effect.getSpell()) && !effect.getSpell().isEquivalent(spawn.getSpellEffects())) {
                    if(debuffLine.isValidTarget(spawn, mainTarget, mainAssist, effect)) {
                      if(spawn == mainTarget) {
                        session.echo("DEBUFF: Considering '" + effect.getSpell() + "' for " + spawn.getName() + ", TTL=" + spawn.getMobTimeToLive() + "; Agro=" + (spawn.getMyAgro() - spawn.getTankAgro()));
                      }

//                      session.echo(spawn + "; TTL = " + spawn.getTimeToLive() + "; MyAgro = " + spawn.getMyAgro() + "; TankAgro = " + spawn.getTankAgro());
                      if(ActivateEffectCommand.checkEffect(effect, spawn)) {
                        List<Spawn> targets = targetLists.get(debuffLine);

                        if(targets == null) {
                          targets = new ArrayList<>();
                          targetLists.put(debuffLine, targets);
                        }

                        targets.add(spawn);
                      }
                    }
                  }
                }
              }
            }
          }
        }
      }

      for(DebuffLine debuffLine : targetLists.keySet()) {
        List<Spawn> targets = targetLists.get(debuffLine);

        if(debuffLine.getEffect().getSpell().getTargetType() == TargetType.PBAE) {
          if(targets.size() > 1) {
            commands.add(new ActivateEffectCommand("DEBUFF", debuffLine, targets.toArray(new Spawn[targets.size()])));
          }
        }
        else {
          commands.add(new ActivateEffectCommand("DEBUFF", debuffLine, targets.get(0)));
        }
      }
    }

    return commands;
  }

  private static class DebuffLine implements SpellLine {
    private final int gem;
    private final Effect effect;
    private final List<String> conditions;
    private final String profiles;
    private final String validTargets;
    private final boolean mainOnly;
    private final double priority;

    private boolean enabled = true;

    public DebuffLine(int gem, Effect effect, String validTargets, String profiles, double priority, boolean mainOnly, List<String> conditions) {
      this.gem = gem;
      this.effect = effect;
      this.validTargets = validTargets;
      this.profiles = profiles;
      this.conditions = conditions;
      this.mainOnly = mainOnly;
      this.priority = priority;
    }

    @Override
    public double getPriority() {
      return priority;
    }

    public int getGem() {
      return gem;
    }

    /**
     * @return the best available Debuff in this line
     */
    @Override
    public Effect getEffect() {
      return effect;
    }

    /**
     * Checks if target is a valid target.
     */
    public boolean isValidTarget(Spawn target, Spawn mainTarget, Spawn mainAssist, Effect effect) {
      boolean valid = validTargets != null ? TargetPattern.isValidTarget(validTargets, target) : true;

      valid = valid && (!mainOnly || target.equals(mainTarget));
      valid = valid && target.inLineOfSight();
      valid = valid && effect.getSpell().isWithinLevelRestrictions(target);

      if(valid) {
        valid = ExpressionEvaluator.evaluate(conditions, new ExpressionRoot(target.getSession(), target, mainTarget, mainAssist, effect), this);
      }

      return valid;
    }

    public String getProfile() {
      return profiles;
    }

    @Override
    public boolean isEnabled() {
      return enabled;
    }

    @Override
    public void setEnabled(boolean enabled) {
      this.enabled = enabled;
    }

    @Override
    public String toString() {
      return String.format("DebuffLine(" + effect + "; pri=%8.4f)", priority);
    }
  }

  @Override
  public boolean isLowLatency() {
    return false;
  }
}
