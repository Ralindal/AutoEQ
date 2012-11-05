package autoeq.eq;

import java.util.Arrays;

import autoeq.SpellData;
import autoeq.effects.Effect;


public class ActivateEffectCommand implements Command {
  private final SpellLine spellLine;
  private final Effect effect;
  private final Spawn[] targets;
  private final String moduleName;

  public ActivateEffectCommand(String moduleName, SpellLine spellLine, Spawn... targets) {
    this.moduleName = moduleName;
    this.spellLine = spellLine;
    this.effect = spellLine.getEffect();
    this.targets = targets;
  }

  @Override
  public double getPriority() {
    return spellLine.getPriority();
  }

  @Override
  public boolean execute(EverquestSession session) {
    // TODO Creators should check if effect is ready, and targets are of the right type (friendly/enemy)

    session.log(moduleName + ": We can cast " + effect + " on " + Arrays.toString(targets));

    //session.doCommand("/bc " + moduleName + ": " + effect.getType().getVerb() + " '" + effect + "' on [ " + targets[0].getName() + " ]");
    String castResult = session.getMe().activeEffect(effect, targets[0]);

    if(castResult.equals("CAST_OUTDOORS")) {
      session.doCommand("/echo BUFF: Disabling '" + effect + "' because not outdoors");
      spellLine.setEnabled(false);
    }
    else if(castResult.equals("CAST_COMPONENTS")) {
      session.doCommand("/echo BUFF: Disabling '" + effect + "' because out of components or spell disabled");
      spellLine.setEnabled(false);
    }
    else if(castResult.equals("CAST_FIZZLE")) {
      session.log("FIZZLE: " + effect.getSpell().getName());
    }
    else {

      /*
       * The following raw spell data check handles the cast of spells being automatically cast, specifically
       * for Unity of the Spirits.
       */

      SpellData spellData = session.getRawSpellData(effect.getSpell().getId());

      for(int i = 0; i < 12; i++) {
        if(spellData.getAttrib(i) == SpellData.ATTRIB_AUTO_CAST) {
          if(spellData.getBase(i) == 100) { // 100 = 100% chance
            // Base2 is the ID of the auto cast spell
            for(Spawn target : targets) {
              target.getSpellEffectManager((int)spellData.getBase2(i)).addCastResult(castResult);
            }
          }
        }
      }

      /*
       * Handles shrink
       */

      if(spellData.hasAttribute(SpellData.ATTRIB_SHRINK)) {
        for(Spawn target : targets) {
          target.getSpellEffectManager(effect.getSpell()).addCastResult("CAST_SHRINK");
        }
      }

      if(effect.getSpell().getDuration() > 25) {
        String targetDescription = "";

        for(Spawn target : targets) {
          if(!targetDescription.isEmpty()) {
            targetDescription += ", ";
          }
          targetDescription += target.isGroupMember() ? target.getName() : target.toString();
        }

        session.echo("Adding " + effect.getSpell() + " for " + effect.getSpell().getDuration() + "s to " + targetDescription + " [" + castResult + "]");
      }

      for(Spawn target : targets) {
        target.getSpellEffectManager(effect.getSpell()).addCastResult(castResult);
      }

      if(castResult.equals("CAST_SUCCESS") || castResult.equals("CAST_RESIST")) {
        increaseAgro(effect, targets);

        if(effect.getSpell().getDuration() == 0) {
          // For instant spells which are succesful (usually heals or nukes) a complete spell casting lockout
          // is desired because there is some lag before the next health update.  0.25 seconds should be sufficient
          // without really disrupting casting (mainly healing).  The lock-out only affects the speed at which
          // AA/Clickeys + normal spells can be casted back to back.  Since spells have a 2.25 second lockout anyway
          // these won't be affected.
          session.setCastLockOut(250);
        }
      }

    }

//    if(castResult.equals("CAST_SUCCESS") || castResult.equals("CAST_TAKEHOLD") || castResult.equals("CAST_IMMUNE") || castResult.equals("CAST_RESIST") || castResult.equals("CAST_CANNOTSEE")) {
//      increaseAgro(effect, targets);
//
//      if(!castResult.equals("CAST_RESIST")) {
//        // TODO If CAST_TAKEHOLD but is a bot, then it will keep recasting anyway... See Infected Bite + Spirit of the Stoic One
//        if(effect.getSpell().getDuration() > 0) {
//          for(Spawn target : targets) {
//            target.getSpellEffectManager(effect.getSpell()).addSpellEffect();
//          }
//        }
//        else {
//          if(castResult.equals("CAST_TAKEHOLD") || castResult.equals("CAST_IMMUNE")) {
//            // If didn't take hold or immune for a spell with no duration, we flag the PC/NPC with a 5 minute timer not to try again.
//            for(Spawn target : targets) {
//              target.getSpellEffectManager(effect.getSpell()).addSpellEffect(5 * 60 * 1000);
//            }
//          }
//          else {
//            // For instant spells which are succesful (usually heals or nukes) a complete spell casting lockout
//            // is desired because there is some lag before the next health update.  0.25 seconds should be sufficient
//            // without really disrupting casting (mainly healing).  The lock-out only affects the speed at which
//            // AA/Clickeys + normal spells can be casted back to back.  Since spells have a 2.25 second lockout anyway
//            // these won't be affected.
//            session.setCastLockOut(250);
//          }
//        }
//      }
//    }

    return true;
  }

  private static void increaseAgro(Effect effect, Spawn... targets) {
    for(Spawn target : targets) {
      target.increaseAgro(effect.getAgro());
    }
  }

  /**
   * Checks ranges, target types (corpses), spell type (beneficial/detrimental) and if effect is ready
   * to cast.
   *
   * TODO Add LoS check?  Is it for Detrimentals only?
   * TODO Add check to see if duration effect is already present?
   *
   * @return <code>true</code> if effect makes sense to cast
   */
  public static boolean checkEffect(Effect effect, Spawn... targets) {
    if(!effect.isReady()) {
      return false;
    }

    Spell spell = effect.getSpell();
    double rangeMultiplier = spell.getTargetType() == TargetType.SINGLE || spell.getTargetType() == TargetType.PBAE ? 1.0 : 0.8;

    for(Spawn target : targets) {
      if(spell.getRange() != 0.0 && target.getDistance() > spell.getRange() * rangeMultiplier) {
        return false;
      }
      if(spell.isDetrimental() && !target.isEnemy() && spell.getTargetType() != TargetType.PBAE) {
        return false;
      }
      if(!spell.isDetrimental() && spell.isTargetted() && !target.isFriendly() && spell.getTargetType() != TargetType.CORPSE) {
        return false;
      }
    }

    return true;
  }

  @Override
  public String toString() {
    return getClass().getName() + "[" + effect + " -> " + Arrays.toString(targets) + "]";
  }
}
