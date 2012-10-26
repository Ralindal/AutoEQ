package autoeq.modules.buff;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import autoeq.ThreadScoped;
import autoeq.effects.Effect;
import autoeq.effects.Effect.Type;
import autoeq.eq.ActivateEffectCommand;
import autoeq.eq.CombatState;
import autoeq.eq.Command;
import autoeq.eq.EverquestSession;
import autoeq.eq.Me;
import autoeq.eq.MemorizeCommand;
import autoeq.eq.Module;
import autoeq.eq.Spawn;
import autoeq.eq.SpawnType;
import autoeq.eq.Spell;
import autoeq.eq.SpellEffectManager;
import autoeq.eq.SpellLine;
import autoeq.eq.TargetType;
import autoeq.ini.Section;

import com.google.inject.Inject;


@ThreadScoped
public class BuffModule implements Module {
  private final EverquestSession session;
  private final List<BuffLine> buffLines = new ArrayList<>();

  private boolean putPetOnHold;

  @Inject
  public BuffModule(EverquestSession session) {
    this.session = session;

    for(Section section : session.getIni()) {
      if(section.getName().startsWith("Buff.")) {
        buffLines.add(new BuffLine(session, section));
      }
    }
  }

  @Override
  public List<Command> pulse() {
    List<Command> commands = new ArrayList<>();
    Me me = session.getMe();

    if((!me.isMoving() || me.isBard()) && me.getType() == SpawnType.PC) {

      /*
       * Ensure buffs are memmed
       */

      buffLines:
      for(BuffLine buffLine : buffLines) {
        if(session.isProfileActive(buffLine.getProfile())) {
          if(buffLine.getGem() != 0) {
            for(EffectSet effectSet : buffLine) {
              Effect effect = effectSet.getGroup() != null ? effectSet.getGroup() : effectSet.getSingle();

              if(effect.getType() == Type.SPELL || effect.getType() == Type.SONG) {
                int gem = me.getGem(effect.getSpell());

                if(gem == 0) {
                  commands.add(new MemorizeCommand(1000, "BUFF", effect.getSpell(), buffLine.getGem()));
                  return commands;
                }
                else {
                  me.lockSpellSlot(gem);
                }
              }
              continue buffLines;
            }
          }
        }
      }

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

//      System.out.println("List of potential targets (without pets):");
//
//      for(Spawn potentialTarget : potentialTargets) {
//        System.out.println(potentialTarget);
//      }

      List<Buff> buffs = handleBuffs(potentialTargets);

      if(buffs.size() > 0) {
        return memOrCast(buffs);
      }
    }

    return null;
  }

  private List<Buff> handleBuffs(Set<Spawn> potentialTargets) {
    List<Buff> buffs = new ArrayList<>();

    for(BuffLine buffLine : buffLines) {
      if(session.isProfileActive(buffLine.getProfile()) && buffLine.isEnabled()) {
        Map<EffectSet, List<Spawn>> m = new HashMap<>();


        for(Spawn potentialTarget : potentialTargets) {

//          System.out.println("PotBuffTarget : " + potentialTarget + " " + potentialTarget.getType());
          if(buffLine.isValidTarget(potentialTarget) && potentialTarget.getDistance() < 1000) {
            spellSetLoop:
            for(EffectSet effectSet : buffLine) {  // SpellSets are spell of the same "buff level", not necessarily same spell level.  Usually group + single target.
              for(Effect effect : effectSet.getEffects()) {  // All spells are from book
                Spell spell = effect.getSpell();

                if(((effect.getType() != Type.SPELL && effect.getType() != Type.SONG) || (spell.isWithinLevelRestrictions(potentialTarget) && spell.getLevel() <= session.getMe().getLevel()))) {  // Level check here in case of delevelling (but spell is still in book)
//                  System.out.println(potentialTarget + " is a valid target for " + spell);


                  if(effect.getType() == Type.SONG) {
                    SpellEffectManager manager = session.getMe().getSpellEffectManager(spell);
                    long millisLeft = manager.getDuration();

                    if(millisLeft <= 6000) { // One Tick
                      long currentTime = System.currentTimeMillis();
                      long timeSinceLastCast = (currentTime - manager.getLastCastMillis()) / 1000;

                      float bardSongPriority = 0.75f;

                      if(timeSinceLastCast > 0) {
                        bardSongPriority = 0.5f / timeSinceLastCast;
                      }

//                      System.out.println(millisLeft + " -- " + effect.getSpell() + " " + (buffLine.getPriority() + bardSongPriority) + " " + session.getMe().inCombat() + " READY: " + effect.isReady());
                      buffs.add(new Buff(effect, buffLine, buffLine.getPriority() + bardSongPriority, potentialTarget));
                    }
                  }
                  else {
                    if(potentialTarget.willStack(spell) && potentialTarget.getSpellEffectManager(spell).getDuration() == 0) {
//                    System.err.println(potentialTarget + " is a valid target for " + spell);

                      if(spell.getTargetType() == TargetType.SINGLE) {
                        buffs.add(new Buff(effect, buffLine, buffLine.getPriority(), potentialTarget));
                      }
                      else {
                        List<Spawn> targets = m.get(effectSet);

                        if(targets == null) {
                          targets = new ArrayList<>();
                          m.put(effectSet, targets);
                        }

                        targets.add(potentialTarget);
                      }
                    }
                  }

                  break spellSetLoop;
                }
              }
            }
          }
        }

        for(EffectSet effectSet : m.keySet()) {
          List<Spawn> targets = m.get(effectSet);

          // TODO number of targets should be adjustable, depending on buff
          // TODO take into account targets where the buff will soon expire (careful with the 1 minute trick!)
          if((targets.size() > 2 && effectSet.getGroup() != null) || effectSet.getSingle() == null) {  // Cast group spell if more than two targets or when only group spell is available
            buffs.add(new Buff(effectSet.getGroup(), buffLine, buffLine.getPriority(), targets.toArray(new Spawn[targets.size()])));
          }
          else {
            for(Spawn target : targets) {
              buffs.add(new Buff(effectSet.getSingle(), buffLine, buffLine.getPriority(), target));
            }
          }
        }
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
          me.lockSpellSlot(gem);
        }
        else if(memorizeCommand == null) {
          memorizeCommand = new MemorizeCommand(1000, "BUFF", effect.getSpell(), buff.getBuffLine().getGem());
        }
      }

      if(ActivateEffectCommand.checkEffect(effect, buff.getTargets())) {
        commands.add(new ActivateEffectCommand("BUFF", buff, buff.getTargets()));
      }
    }

    // If we get here, and no commands have yet been created, we'll need to mem something because
    // no spells were memmed or ready.

    if(commands.size() == 0 && memorizeCommand != null && me.getCombatState() != CombatState.COMBAT) { // TODO while mounted memming is ok
      commands.add(memorizeCommand);
    }

    return commands;
  }

  public static class Buff implements SpellLine {
    private final Spawn[] potentialTargets;
    private final Effect effect;
    private final BuffLine buffLine;
    private final double priority;

    public Buff(Effect effect, BuffLine buffLine, double priority, Spawn... potentialTargets) {
      this.buffLine = buffLine;
      this.priority = priority;
      this.potentialTargets = potentialTargets;
      this.effect = effect;
    }

    @Override
    public double getPriority() {
      return priority;
    }

    public Spawn[] getTargets() {
      return potentialTargets;
    }

    @Override
    public Effect getEffect() {
      return effect;
    }

    public BuffLine getBuffLine() {
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
  }

  @Override
  public boolean isLowLatency() {
    return false;
  }
}
