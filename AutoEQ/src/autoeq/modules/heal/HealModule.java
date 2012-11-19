package autoeq.modules.heal;

import java.util.ArrayList;
import java.util.HashSet;
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
import autoeq.ini.Section;
import autoeq.modules.buff.EffectSet;
import autoeq.modules.target.TargetModule;

import com.google.inject.Inject;

@ThreadScoped
public class HealModule implements Module {
  private final EverquestSession session;
  private final List<Heal> heals = new ArrayList<>();
  private final int maxHealRange;
  private final TargetModule targetModule;

  @Inject
  public HealModule(EverquestSession session, TargetModule targetModule) {
    this.session = session;
    this.targetModule = targetModule;
    this.maxHealRange = Integer.parseInt(session.getIni().getSection("General").getDefault("MaxHealRange", "100"));

    for(Section section : session.getIni()) {
      if(section.getName().startsWith("Heal.")) {
        List<EffectSet> effectSets = SpellParser.parseSpells(session, section, 10);

        Effect effect = effectSets.isEmpty() ? null : effectSets.get(0).getSingleOrGroup();

        if(effect != null) {
          System.out.println("adding spell " + effect);
          heals.add(
            new Heal(
              effect,
              section.get("Gem") != null ? Integer.parseInt(section.get("Gem")) : 0,
              Priority.decodePriority(section.get("Priority"), 100),
              section.get("ValidTargets"),
              section.get("Profile"),
              section.getAll("Condition")
            )
          );
        }
        else {
          System.err.println("HEAL: No such effect: " + section.get("Spell"));
        }
      }
    }
  }

  public int getPriority() {
    return 1;
  }

  @Override
  public List<Command> pulse() {
    List<Command> commands = new ArrayList<>();
    Me me = session.getMe();

    if(!me.isMoving() && me.getType() == SpawnType.PC) {

      /*
       * Ensure heals are memmed
       */

      for(Heal heal : heals) {
        if(session.isProfileActive(heal.getProfile())) {
          if(heal.getEffect().getType() == Type.SPELL || heal.getEffect().getType() == Type.SONG) {
            int gem = me.getGem(heal.getEffect().getSpell());

            if(gem == 0) {
              commands.add(new MemorizeCommand(1000, "HEAL", heal.getEffect().getSpell(), heal.getGem()));
              return commands;
            }
            else {
              me.lockSpellSlot(gem);
            }
          }
        }
      }

      /*
       * Gather potential targets
       */

      Set<Spawn> targets = new HashSet<>();

      targets.addAll(session.getBots());
      targets.addAll(session.getGroupMembers());

      for(Spawn spawn : session.getBots()) {
        Spawn pet = spawn.getPet();

        if(pet != null) {
          targets.add(pet);
        }
      }

      // 1. Update health information

//      String dbg = "";
//
//      for(Spawn target : targets) {
//  //      target.getHealthInfo().addDataPoint(target.getSpawn().getHitPointsPct());
//  //        dbg += String.format(target.getSpawn().getName() + " = " + Integer.parseInt(hp) + " (dps = %5.1f; ttl = " + target.getHealthInfo().getTimeToLive() + "s); ", target.getHealthInfo().getDPS(6000));
//        if(target.getHitPointsPct() < 100) {
//          dbg += String.format(target.getName() + " = " + target.getHitPointsPct() + "; ");
//        }
//      }
//
//      // 2. Decide what to do
//
//      if(dbg.length() > 0) {
//        session.log("Health Status: " + dbg);
//      }

      Spawn mainAssist = targetModule.getMainAssist();
      Spawn mainTarget = mainAssist == null ? null : mainAssist.getTarget();

      for(Heal heal : heals) {
        if(session.isProfileActive(heal.getProfile())) {
          for(Spawn target : targets) {

            if(TargetPattern.isValidTarget(heal.validTargets, target)) {
              if(ExpressionEvaluator.evaluate(heal.getConditions(), new ExpressionRoot(session, target, mainTarget, mainAssist, heal.getEffect()), this)) {
                if(heal.getEffect().getSpell().isWithinLevelRestrictions(target)) {
                  //System.out.println("Heal Target -> " + target + " -- " + heal.getEffect() + " -- " + heal.getEffect().getSpell().getDuration());
                  if(target.willStack(heal.getEffect().getSpell())) {
                    if(target.getDistance() <= maxHealRange) {
                      if(ActivateEffectCommand.checkEffect(heal.getEffect(), target)) {
                        commands.add(new ActivateEffectCommand("HEAL", heal, target));
                      }
                    }
                  }
                }
              }
            }
          }
        }
      }
    }

    return commands;
  }

  private class Heal implements SpellLine {
    private final int gem;
    private final int priority;
    private final Effect effect;
    private final String validTargets;
    private final String profiles;
    private final List<String> conditions;

    private boolean enabled = true;

    public Heal(Effect effect, int gem, int priority, String validTargets, String profiles, List<String> conditions) {
      this.gem = gem;
      this.effect = effect;
      this.priority = priority;
      this.validTargets = validTargets;
      this.profiles = profiles;
      this.conditions = conditions;
    }

    public int getGem() {
      return gem;
    }

    @Override
    public double getPriority() {
      return priority;
    }

    @Override
    public Effect getEffect() {
      return effect;
    }

    public List<String> getConditions() {
      return conditions;
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
  }

  @Override
  public boolean isLowLatency() {
    return false;
  }
}
