package autoeq.modules.heal;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import autoeq.ThreadScoped;
import autoeq.effects.Effect;
import autoeq.effects.Effect.Type;
import autoeq.eq.ActivateEffectCommand;
import autoeq.eq.Command;
import autoeq.eq.EverquestSession;
import autoeq.eq.Gem;
import autoeq.eq.Me;
import autoeq.eq.MemorizeCommand;
import autoeq.eq.Module;
import autoeq.eq.ParsedEffectLine;
import autoeq.eq.Spawn;
import autoeq.eq.SpawnType;
import autoeq.eq.Spell;
import autoeq.eq.SpellParser;
import autoeq.eq.SpellUtil;
import autoeq.eq.TargetType;
import autoeq.ini.Section;
import autoeq.modules.target.TargetModule;

import com.google.inject.Inject;

@ThreadScoped
public class HealModule implements Module {
  private final EverquestSession session;
  private final List<ParsedEffectLine> heals = new ArrayList<>();
  private final TargetModule targetModule;
  private final Set<String> activeProfiles = new HashSet<>();

  @Inject
  public HealModule(EverquestSession session, TargetModule targetModule) {
    this.session = session;
    this.targetModule = targetModule;

    activeProfiles.add("--no active profile--");
  }

  public int getPriority() {
    return 1;
  }

  @Override
  public List<Command> pulse() {
    List<Command> commands = new ArrayList<>();
    Me me = session.getMe();

    if(!me.isMoving() && me.getType() == SpawnType.PC && !me.isCasting()) {
      long startTime = System.currentTimeMillis();

      if(!session.getActiveProfiles().equals(activeProfiles)) {
        System.out.println("1");

        activeProfiles.clear();
        activeProfiles.addAll(session.getActiveProfiles());
        heals.clear();

        for(Section section : session.getIni()) {
          if(section.getName().startsWith("Heal.")) {
            heals.addAll(SpellParser.parseSpells(session, section, 10));
          }
        }
      }

      /*
       * Ensure heals are memmed
       */

      List<Gem> spellsToMemorize = new ArrayList<>();

      for(ParsedEffectLine heal : heals) {
        if(heal.isProfileActive(session)) {
          if(heal.getEffect() != null) {
            if(heal.getEffect().getType() == Type.SPELL || heal.getEffect().getType() == Type.SONG) {
              int gem = me.getGem(heal.getEffect().getSpell());

              if(gem == 0 && me.isSafeToMemorizeSpell()) {
                spellsToMemorize.add(new Gem(heal.getEffect().getSpell(), heal.getGem(), heal.getGem() != 0 ? 10 : 5));
              }
              else {
                me.lockSpellSlot(gem, heal.getGem() != 0 ? 10 : 5);
              }
            }
          }
        }
      }

      if(!spellsToMemorize.isEmpty()) {
        commands.add(new MemorizeCommand(1000, "HEAL", spellsToMemorize.toArray(new Gem[0])));
        //return commands;
      }

      /*
       * Gather potential targets
       */

      Set<Spawn> targets = new HashSet<>();

      targets.addAll(session.getBots());
      targets.addAll(session.getGroupMembers());
      targets.addAll(me.getFriendlyExtendedTargets());

      for(Spawn spawn : session.getBots()) {
        Spawn pet = spawn.getPet();

        if(pet != null) {
          targets.add(pet);
        }
      }

//      System.out.println("Heals: " + heals);
//      System.out.println("Mem: " + spellsToMemorize);
//      System.out.println("Targets: " + targets);

      long millis = System.currentTimeMillis();

      Spawn mainAssist = targetModule.getMainAssist();
      Spawn mainTarget = mainAssist == null ? null : mainAssist.getTarget();
      Set<String> steps = new LinkedHashSet<>();

      for(ParsedEffectLine heal : heals) {
        steps.clear();

        if(heal.isProfileActive(session)) {
          steps.add("profile");

          for(Spawn target : targets) {
            Effect effect = heal.getEffect();

            if(effect != null && effect.isReady()) {
              String conditionFailReason = heal.getFirstNonMatchingCondition(target, mainTarget, mainAssist, effect);

              if(conditionFailReason == null) {
                steps.add("matchesConditions");

                Spell spell = effect.getSpell();

                List<Spawn> potentialTargets = (spell.getTargetType() == TargetType.GROUP && target.isGroupMember()) || spell.getTargetType() == TargetType.TARGETED_GROUP ? target.getGroupMembers() : Collections.singletonList(target);
                List<Spawn> affectedTargets = SpellUtil.findAffectedTargets(heal, me, potentialTargets);

                if(!affectedTargets.isEmpty()) {
                  steps.add("affectedTarget");
//                    if(heal.getProfiles().contains("Raid")) {
//                      session.log(">>> (2) considering " + targets.size() + ": " + target + " p=" + target.isPulling() + " s=" + target.willStack(spell) + " (dist=" + target.getDistance() + ") " + heal.getProfiles() + " " + spell.getName());
//                    }

                  String noStackReason = target.getNoStackReason(spell);

                  if(noStackReason == null && !target.isPulling()) {
                    steps.add("willStack");

                    double priority = heal.determinePriority(effect, target, mainTarget, mainAssist);
                    System.out.println(">>> Adding command for " + spell + " (tt=" +spell.getTargetType()+ "): targets=" + affectedTargets);
                    commands.add(new ActivateEffectCommand("HEAL", heal, priority, affectedTargets));
                  }
                  else {
                    steps.add("\\aowontStack(" + noStackReason + ")\\ag");
                  }
                }
                else {
                  steps.add("\\aounaffectedTarget\\ag");
                }
              }
              else {
                steps.add("\\aoconditionMismatch(" + conditionFailReason + ")\\ag");
              }
            }
          }
        }

        debugEffect(heal, steps);
      }

      if(System.currentTimeMillis() - startTime > 10) {
        session.log("HEAL: " + (System.currentTimeMillis() - startTime) + "/" + (System.currentTimeMillis() - millis) + " ms");
      }
    }

    return commands;
  }

  private int debugEffectLimiter = 10;

  private void debugEffect(ParsedEffectLine healLine, Set<String> steps) {
    if(healLine.getEffect() != null && healLine.getEffect().isSameAs(session.getDebugEffect())) {
      if(debugEffectLimiter-- == 0) {
        session.echo("\\ay[DebugEffect]\\aw " + healLine + " (" + healLine.getEffect() + "):\\ag " + steps);
        debugEffectLimiter = 10;
      }
    }
  }

  @Override
  public int getBurstCount() {
    return 4;
  }
}
