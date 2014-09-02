package autoeq.modules.debuff;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import autoeq.ThreadScoped;
import autoeq.effects.Effect;
import autoeq.eq.ActivateEffectCommand;
import autoeq.eq.Command;
import autoeq.eq.EverquestSession;
import autoeq.eq.Gem;
import autoeq.eq.GemAssigner;
import autoeq.eq.Me;
import autoeq.eq.MemorizeCommand;
import autoeq.eq.Module;
import autoeq.eq.ParsedEffectLine;
import autoeq.eq.Spawn;
import autoeq.eq.SpawnType;
import autoeq.eq.Spell;
import autoeq.eq.SpellParser;
import autoeq.eq.SpellUtil;
import autoeq.eq.TargetCategory;
import autoeq.eq.TargetType;
import autoeq.ini.Section;
import autoeq.modules.target.TargetModule;

import com.google.inject.Inject;

@ThreadScoped
public class DebuffModule implements Module {
  private final EverquestSession session;
  private final List<ParsedEffectLine> debuffLines = new ArrayList<>();
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

    if(!me.isMoving() && me.getType() == SpawnType.PC && !me.isCasting()) {
      long startTime = System.currentTimeMillis();

      if(!session.getActiveProfiles().equals(activeProfiles)) {
        activeProfiles.clear();
        activeProfiles.addAll(session.getActiveProfiles());
        debuffLines.clear();

        for(Section section : session.getIni()) {
          if(section.getName().startsWith("Debuff.")) {
            debuffLines.addAll(SpellParser.parseSpells(session, section, 100));
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

      GemAssigner gemAssigner = new GemAssigner(session);
      List<Gem> spellsToMemorize = new ArrayList<>();

      for(ParsedEffectLine debuffLine : debuffLines) {
        if(debuffLine.isProfileActive(session)) {
          Gem gem = gemAssigner.getGem(debuffLine);

          if(gem != null) {
            spellsToMemorize.add(gem);
          }
        }
      }

      if(!spellsToMemorize.isEmpty()) {
        commands.add(new MemorizeCommand(1000, "DEBUFF", spellsToMemorize.toArray(new Gem[0])));
        //return commands;
      }

      /*
       * Determine if we're ready to cast (we do this before attempting to assist to prevent useless assists).
       */

      if(mainTarget != null && !mainTarget.isEnemy()) {
        mainTarget = null;
      }

      LinkedHashMap<ParsedEffectLine, List<Spawn>> targetLists = new LinkedHashMap<>();

      mainAssist = targetModule.getMainAssist();

      long millis = System.currentTimeMillis();
      long counter = 0;

      Set<Spawn> spawns = session.getSpawns(300, 300);
      Map<ParsedEffectLine, Long> testedSpells = new HashMap<>();
      Set<String> steps = new LinkedHashSet<>();

      for(ParsedEffectLine debuffLine : debuffLines) {
        steps.clear();

        if(debuffLine.isProfileActive(session)) {
          Effect effect = debuffLine.getEffect();

          steps.add("profile");

          if(effect != null && effect.isReady()) {
//            session.log("" + debuffLine);
            steps.add("ready");

            mainTarget = debuffLine.getTargetCategory() == TargetCategory.MAIN ? targetModule.getMainAssistTarget() : targetModule.getFromAssist() ;  // here because the result differs depending on target type and also to prevent doing /assist if nothing is ready to cast
//            if(me.getName().equals("Belderan")) { System.out.println(">>> " + debuffLine.getTargetCategory() + " mainTarget = " + mainTarget); }

            for(Spawn spawn : spawns) {
              if(debuffLine.getTargetCategory().matches(spawn, mainTarget)) {  // Performance: This condition eliminates the bulk of targets for main targeted effects
                steps.add("targetMatch(\\a-g" + spawn.getName() + "\\ag)");

                if(!SpellUtil.findAffectedTargets(debuffLine, me, Collections.singletonList(spawn)).isEmpty()) {
                  steps.add("affectedTarget");

                  counter++;

    //              if(spawn.getDistance() < 75.0) {
    //                System.out.println(spawn + " ttl " + spawn.getTimeToLive());
    //              }
  //                  if(me.getName().equals("bla") && me.getPet() == null && effect.getSpell().getName().contains("Impose") && spawn.isExtendedTarget() && spawn != mainTarget) {
  //                    System.out.println(">>> Pot. charm for: " + spawn + "; containsEffect = " + spawn.getSpellEffects().contains(effect.getSpell()));
  //                  }
  //                  if(me.getName().equals("bla") && (effect.getSpell().getName().contains("Dreary Deeds") || effect.getSpell().getName().contains("Helix")) && spawn.isExtendedTarget() && spawn != mainTarget) {
  //                    System.out.println(">>> Pot. slow for: " + spawn + "; containsEffect = " + spawn.getSpellEffects().contains(effect.getSpell()) + ", equiv=" + effect.getSpell().isEquivalent(spawn.getSpellEffects()) + "; matchesCond: " + debuffLine.matchesConditions(spawn, mainTarget, mainAssist, effect) + ": " + effect.getSpell());
  //                  }

                  String noStackReason = spawn.getNoStackReason(effect.getSpell());

                  if(noStackReason == null) {
                    steps.add("willStack");

                    long conditionMillis = System.currentTimeMillis();

  //                  if(!spawn.getSpellEffects().contains(effect.getSpell()) && !effect.getSpell().isEquivalent(spawn.getSpellEffects())) {

                    String conditionFailReason = debuffLine.getFirstNonMatchingCondition(spawn, mainTarget, mainAssist, effect);

                    if(conditionFailReason == null) {
                      steps.add("matchesConditions");

                      List<Spawn> targets = targetLists.get(debuffLine);

                      if(targets == null) {
                        targets = new ArrayList<>();
                        targetLists.put(debuffLine, targets);
                      }

                      targets.add(spawn);
                    }
                    else {
                      steps.add("\\aoconditionMismatch(" + conditionFailReason + ")\\ag");
                    }

                    if(testedSpells.containsKey(debuffLine)) {
                      conditionMillis -= testedSpells.get(debuffLine);
                    }

                    testedSpells.put(debuffLine, System.currentTimeMillis() - conditionMillis);
                  }
                  else {
                    steps.add("\\aowontStack(" + noStackReason + ")\\ag");
                  }
                }
                else {
                  steps.add("\\aounaffectedTarget\\ag");
                }
              }
            }
          }
        }

        debugEffect(debuffLine, steps);
      }

      long loop1 = System.currentTimeMillis();

      for(ParsedEffectLine debuffLine : targetLists.keySet()) {
        List<Spawn> targets = targetLists.get(debuffLine);
        Spell spell = debuffLine.getEffect().getSpell();
        double priority = debuffLine.determinePriority(debuffLine.getEffect(), targets.get(0), mainTarget, mainAssist);

        if(spell.getTargetType().isAreaOfEffect()) {
          if(targets.size() >= debuffLine.getMinimumTargets()) {
            if(spell.getTargetType().isTargeted() || spell.getTargetType() == TargetType.BEAM) {
              // Determine primary target that would affect most surrounding targets
              List<Spawn> bestTargets = null;

              for(Spawn primaryTargetCandidate : targets) {
                List<Spawn> potentialTargets = new ArrayList<>(targets);

                potentialTargets.remove(primaryTargetCandidate);
                potentialTargets.add(0, primaryTargetCandidate);

                List<Spawn> affectedTargets = SpellUtil.findAffectedTargets(debuffLine, me, potentialTargets);

                if(bestTargets == null || bestTargets.size() < affectedTargets.size()) {
                  bestTargets = affectedTargets;
                }
              }

              if(bestTargets != null) {
                commands.add(new ActivateEffectCommand("DEBUFF", debuffLine, priority, bestTargets));
              }
            }
            else {
              commands.add(new ActivateEffectCommand("DEBUFF", debuffLine, priority, targets));
            }
          }
        }
        else {
          commands.add(new ActivateEffectCommand("DEBUFF", debuffLine, priority, targets.get(0)));
        }
      }

      if(!commands.isEmpty()) {
        session.getLogger().finest("DebuffModules: " + (System.currentTimeMillis() - startTime) + " ms, " + commands);
      }

      if(System.currentTimeMillis() - startTime > 10) {
        session.log("--DEBUFF: " + (System.currentTimeMillis() - startTime) + "/" + (System.currentTimeMillis() - millis) + "/" + (System.currentTimeMillis() - loop1) + " ms; counter = " + counter + "; " + testedSpells);
      }
    }

    return commands;
  }

  private int debugEffectLimiter = 10;

  private void debugEffect(ParsedEffectLine debuffLine, Set<String> steps) {
    if(debuffLine.getEffect() != null && debuffLine.getEffect().isSameAs(session.getDebugEffect())) {
      if(debugEffectLimiter-- == 0) {
        session.echo("\\ay[DebugEffect]\\aw " + debuffLine + " (" + debuffLine.getEffect() + "):\\ag " + steps);
        debugEffectLimiter = 10;
      }
    }
  }

  @Override
  public int getBurstCount() {
    return 4;
  }
}
