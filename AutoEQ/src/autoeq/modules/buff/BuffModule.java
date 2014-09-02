package autoeq.modules.buff;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import autoeq.ThreadScoped;
import autoeq.effects.Effect;
import autoeq.effects.Effect.Type;
import autoeq.eq.ActivateEffectCommand;
import autoeq.eq.Command;
import autoeq.eq.EverquestSession;
import autoeq.eq.Me;
import autoeq.eq.MemorizeCommand;
import autoeq.eq.Module;
import autoeq.eq.ParsedEffectLine;
import autoeq.eq.Priority;
import autoeq.eq.Spawn;
import autoeq.eq.SpawnType;
import autoeq.eq.Spell;
import autoeq.eq.SpellEffectManager;
import autoeq.eq.SpellLine;
import autoeq.eq.Gem;
import autoeq.eq.SpellParser;
import autoeq.eq.SpellUtil;
import autoeq.ini.Section;

import com.google.inject.Inject;

@ThreadScoped
public class BuffModule implements Module {
  private final EverquestSession session;
  private final List<ParsedEffectLine> buffLines = new ArrayList<>();
  private final Set<String> activeProfiles = new HashSet<>();

  private boolean putPetOnHold;

  @Inject
  public BuffModule(EverquestSession session) {
    this.session = session;

    activeProfiles.add("--no active profile--");
  }

  @Override
  public List<Command> pulse() {
    List<Command> commands = new ArrayList<>();
    Me me = session.getMe();

    if((!me.isMoving() || me.isBard()) && me.getType() == SpawnType.PC && !me.isCasting()) {
      long startTime = System.currentTimeMillis();

      if(!session.getActiveProfiles().equals(activeProfiles)) {
        activeProfiles.clear();
        activeProfiles.addAll(session.getActiveProfiles());
        buffLines.clear();

        for(Section section : session.getIni()) {
          if(section.getName().startsWith("Buff.")) {
            buffLines.addAll(SpellParser.parseSpells(session, section, 10));
          }
        }
      }

      /*
       * Ensure buffs are memmed
       */

      buffLines:
      for(ParsedEffectLine buffLine : buffLines) {
        if(buffLine.getGem() != 0) {
          if(buffLine.isProfileActive(session)) {
            for(EffectSet effectSet : buffLine) {
              Effect effect = effectSet.getGroup() != null ? effectSet.getGroup() : effectSet.getSingle();

              if(effect.getType() == Type.SPELL || effect.getType() == Type.SONG) {
                int gem = me.getGem(effect.getSpell());

                if(gem == 0 && me.isSafeToMemorizeSpell()) {
                  System.out.println(">>> mem command for : " + effect);
                  commands.add(new MemorizeCommand(1000, "BUFF", new Gem(effect.getSpell(), buffLine.getGem(), 10)));
                  return commands;
                }
                else {
                  me.lockSpellSlot(gem, 10);
                }
              }
              continue buffLines;
            }
          }
        }
      }

      long millis = System.currentTimeMillis();

      /*
       * Pet control
       */

      Spawn myPet = me.getPet();
      if(!putPetOnHold && myPet != null) {
        session.doCommand("/pet ghold on");
        putPetOnHold = true;
      }
      if(myPet == null) {
        putPetOnHold = false;
      }

      /*
       * Create set of buff targets
       */

      Set<Spawn> potentialTargets = new HashSet<>();

      potentialTargets.addAll(session.getBots());
      potentialTargets.addAll(session.getGroupMembers());

      for(Spawn spawn : session.getBots()) {
        Spawn pet = spawn.getPet();

        if(pet != null) {
          potentialTargets.add(pet);
        }
      }

      long loop1 = System.currentTimeMillis();

//      System.out.println(me.getName() + ": List of potential targets (without pets): " + potentialTargets);

      List<Buff> buffs = handleBuffs(potentialTargets);

      if(System.currentTimeMillis() - startTime > 10) {
        session.log("BUFF: " + (System.currentTimeMillis() - startTime) + "/" + (System.currentTimeMillis() - millis) + "/" + (System.currentTimeMillis() - loop1) + " ms");
      }

      if(buffs.size() > 0) {
        return memOrCast(buffs);
      }
    }

    return null;
  }

  private List<Buff> handleBuffs(Set<Spawn> potentialTargets) {
    List<Buff> buffs = new ArrayList<>();
    Set<String> steps = new LinkedHashSet<>();

    for(ParsedEffectLine buffLine : buffLines) {
      steps.clear();

//      System.out.println(">>> " + buffLine + " : " + buffLine.isEnabled());
      if(buffLine.isProfileActive(session) && buffLine.isEnabled()) {
        Map<EffectSet, Set<Spawn>> m = new HashMap<>();

        steps.add("profile");

        for(Spawn potentialTarget : potentialTargets) {
//          if(buffLine.iterator().next().getSingleOrGroup().getSpell() != null && buffLine.iterator().next().getSingleOrGroup().getSpell().getName().contains("Certitude")) {
//            System.out.println(">>> Certitude " + potentialTarget + " = " + buffLine.isValidTarget(potentialTarget));
//          }

//          System.out.println(buffLine + " PotBuffTarget : " + potentialTarget + " " + potentialTarget.getType());
          if(buffLine.isValidTarget(potentialTarget) && potentialTarget.getDistance() < 1000) {  // TODO isValidTarget uses the "best" spell for the Effect parameter, but that may not be the actual spell we're gonna cast
            steps.add("validTarget");

            buffLineLoop:
            for(EffectSet effectSet : buffLine) {  // SpellSets are spell of the same "buff level", not necessarily same spell level.  Usually group + single target.
              boolean willStack = true;
              long millisLeft = 0;

              for(Effect effect : effectSet.getEffects()) {
                Spell spell = effect.getSpell();

                if(spell != null) {
                  if(!potentialTarget.willStack(spell)) {
                    willStack = false;
                  }

                  long millis = potentialTarget.getSpellEffectManager(spell).getMillisLeft();  // TODO this doesn't work correctly with autocasted spells, while willStack does

                  if(millis > millisLeft) {
                    millisLeft = millis;
                  }
                }
              }

              boolean validBufFound = false;

              for(Effect effect : effectSet.getEffects()) {  // All spells are from book
                Spell spell = effect.getSpell();

                if(effect.isUsable() && ((effect.getType() != Type.SPELL && effect.getType() != Type.SONG) || potentialTarget.isValidLevelFor(spell))) {  // Level check here in case of delevelling (but spell is still in book)
//                  System.out.println(potentialTarget + " is a valid target for " + spell);

                  validBufFound = true;

//                  if(spell.getName().contains("Temper")) {
//                    System.out.println(">>> Temper considered for " + potentialTarget + " : willstack = " + willStack + "; ml = " + millisLeft);
//                  }

                  if(effect.getType() == Type.SONG) {
                    if(millisLeft <= 6000) { // One Tick
                      SpellEffectManager manager = session.getMe().getSpellEffectManager(spell);

                      long currentTime = System.currentTimeMillis();
                      long timeSinceLastCast = (currentTime - manager.getLastCastMillis()) / 1000;

                      float bardSongPriority = 0.75f;

                      if(timeSinceLastCast > 0) {
                        bardSongPriority = 0.5f / timeSinceLastCast;
                      }

                      String bardSongPriorityExpr = buffLine.getPriorityExpr() == null ? String.format("%.4f", bardSongPriority + 200) : "(" + buffLine.getPriorityExpr() + String.format(") + %.4f", bardSongPriority);

//                      System.out.println(millisLeft + " -- " + effect.getSpell() + " " + (buffLine.getPriority() + bardSongPriority) + " " + session.getMe().inCombat() + " READY: " + effect.isReady());
                      buffs.add(new Buff(effect, buffLine, bardSongPriorityExpr, potentialTarget));

                    }
                  }
                  else {
//                    if(spell.getName().contains("Reverb")) {
//                      System.out.println("Considering " + spell.getName() + " on " + potentialTarget + " willstack = " + willStack);
//                    }

                    if(willStack) { // TODO removed millis check, since it doesn't undestand autocasted spells: && millisLeft == 0) {
                      steps.add("willStack");

//                      System.err.println(potentialTarget + " is a valid target for " + spell + " or " + effectSet.getGroup());

//                      if(spell.getTargetType() == TargetType.SINGLE || spell.getTargetType() == TargetType.CORPSE) {
//                        buffs.add(new Buff(effect, buffLine, buffLine.getPriority(), potentialTarget));
//                      }
//                      else {
                      Set<Spawn> targets = m.get(effectSet);

                      if(targets == null) {
                        targets = new HashSet<>();
                        m.put(effectSet, targets);
                      }

                      targets.add(potentialTarget);
//                      }
                    }
                  }
                }
              }

              if(validBufFound) {
                break buffLineLoop;
              }
            }
          }
        }

        for(EffectSet effectSet : m.keySet()) {
          Set<Spawn> targets = m.get(effectSet);

          // TODO number of targets should be adjustable, depending on buff
          // TODO take into account targets where the buff will soon expire (careful with the 1 minute trick!)

          if(((targets.size() >= buffLine.getMinimumTargets() && effectSet.getGroup() != null) || effectSet.getSingle() == null) && effectSet.getGroup().isUsable()) {
            buffs.add(new Buff(effectSet.getGroup(), buffLine, buffLine.getPriorityExpr(), new ArrayList<>(targets)));
          }
          else {
            for(Spawn target : targets) {
              buffs.add(new Buff(effectSet.getSingleOrGroup(), buffLine, buffLine.getPriorityExpr(), target));
            }
          }
        }
      }

      debugEffect(buffLine, steps);
    }

    for(Iterator<Buff> iterator = buffs.iterator(); iterator.hasNext();) {
      Buff buff = iterator.next();

      if(SpellUtil.findAffectedTargets(buff, session.getMe(), buff.getTargets()).isEmpty()) {
        iterator.remove();
      }
    }

    return buffs;
  }

  /**
   * We have a list of targets and buffs.  If anything is memmed and ready, we'll do that first.
   * Otherwise we'll mem something.
   */
  private List<Command> memOrCast(List<Buff> buffs) {
    List<Command> commands = new ArrayList<>();
    Me me = session.getMe();
    MemorizeCommand memorizeCommand = null;

    // TODO Should probably not cast buffs longer than X seconds if in combat

    for(Buff buff : buffs) {
      Effect effect = buff.getEffect();

      if(effect.getType() == Type.SPELL || effect.getType() == Type.SONG) {
        int gem = me.getGem(effect.getSpell());

        if(gem > 0) {
          me.lockSpellSlot(gem, 10);
        }
        else if(memorizeCommand == null) {
          memorizeCommand = new MemorizeCommand(1000, "BUFF", new Gem(effect.getSpell(), buff.getBuffLine().getGem(), 10));
        }
      }

      if(effect.isReady()) {
        double priority = Priority.decodePriority(session, null, effect, buff.getPriorityExpr(), 300);

        List<Spawn> affectedTargets = SpellUtil.findAffectedTargets(buff, me, buff.getTargets());

        if(!affectedTargets.isEmpty()) {
          commands.add(new ActivateEffectCommand("BUFF", buff, priority, buff.getTargets()));
        }
      }
    }

    // If we get here, and no commands have yet been created, we'll need to mem something because
    // no spells were memmed or ready.

    if(commands.size() == 0 && memorizeCommand != null && me.isSafeToMemorizeSpell()) {
      commands.add(memorizeCommand);
    }

    return commands;
  }

  public static class Buff implements SpellLine {
    private final List<Spawn> potentialTargets;
    private final Effect effect;
    private final ParsedEffectLine buffLine;
    private final String priorityExpr;

    public Buff(Effect effect, ParsedEffectLine buffLine, String priorityExpr, List<Spawn> potentialTargets) {
      if(effect == null) {
        throw new IllegalArgumentException("parameter 'effect' cannot be null; buffLine = " + buffLine);
      }

      this.buffLine = buffLine;
      this.priorityExpr = priorityExpr;
      this.potentialTargets = potentialTargets;
      this.effect = effect;
    }

    public Buff(Effect effect, ParsedEffectLine buffLine, String priorityExpr, Spawn potentialTarget) {
      this(effect, buffLine, priorityExpr, Collections.singletonList(potentialTarget));
    }

    public String getPriorityExpr() {
      return priorityExpr;
    }

    public List<Spawn> getTargets() {
      return potentialTargets;
    }

    @Override
    public Effect getEffect() {
      return effect;
    }

    public ParsedEffectLine getBuffLine() {
      return buffLine;
    }

    @Override
    public boolean isEnabled() {
      return buffLine.isEnabled();
    }

    @Override
    public void setEnabled(boolean enabled) {
      buffLine.setEnabled(enabled);
    }

    @Override
    public boolean isAnnounce() {
      return buffLine.isAnnounce();
    }

    @Override
    public String getAnnounceChannelPrefix() {
      return buffLine.getAnnounceChannelPrefix();
    }

    @Override
    public List<String> getPostActions() {
      return buffLine.getPostActions();
    }

    @Override
    public double getRangeExtensionFactor() {
      return buffLine.getRangeExtensionFactor();
    }

    @Override
    public double getDurationExtensionFactor() {
      return buffLine.getDurationExtensionFactor();
    }

    @Override
    public String getName() {
      return buffLine.getName();
    }
  }

  @Override
  public int getBurstCount() {
    return 4;
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
}
