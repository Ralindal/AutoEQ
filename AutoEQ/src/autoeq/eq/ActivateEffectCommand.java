package autoeq.eq;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import autoeq.effects.Effect;

public class ActivateEffectCommand implements Command {
  private final SpellLine spellLine;
  private final Effect effect;
  private final double priority;
  private final List<Spawn> targets;
  private final String moduleName;

  public ActivateEffectCommand(String moduleName, SpellLine spellLine, double priority, List<Spawn> targets) {
    this.moduleName = moduleName;
    this.spellLine = spellLine;
    this.priority = priority;
    this.effect = spellLine.getEffect();
    this.targets = new ArrayList<>(targets);
  }

  public ActivateEffectCommand(String moduleName, SpellLine spellLine, double priority, Spawn target) {
    this(moduleName, spellLine, priority, Collections.singletonList(target));
  }

  @Override
  public double getPriority() {
    return priority;
  }

  @Override
  public boolean execute(EverquestSession session) {
    session.log(String.format(moduleName + ": [%12.8f] Casting " + effect + " on " + targets, getPriority()));

    session.getMe().activateEffect(spellLine, effect, targets, getPriority());

//    String castResult = session.getMe().activateEffect(spellLine, effect, targets);
//
//    if(castResult.equals("CAST_OUTDOORS")) {
//      session.doCommand("/echo BUFF: Disabling '" + effect + "' because not outdoors");
//      spellLine.setEnabled(false);
//    }
//    else if(castResult.equals("CAST_COMPONENTS")) {
//      session.doCommand("/echo BUFF: Disabling '" + effect + "' because out of components or spell disabled");
//      spellLine.setEnabled(false);
//    }
//    else if(castResult.equals("CAST_FIZZLE")) {
//      session.log("FIZZLE: " + effect.getSpell().getName());
//    }
//    else {
//
//      /*
//       * The following raw spell data check handles the cast of spells being automatically cast, specifically
//       * for Unity of the Spirits.
//       */
//
//      if(effect.getSpell() != null) {
//        for(Spell autoCastedSpell : effect.getSpell().getAutoCastedSpells()) {
//          for(Spawn target : targets) {
//            target.getSpellEffectManager(autoCastedSpell).addCastResult(castResult);
//          }
//        }
//
//        /*
//         * Handles shrink
//         */
//
//        if(effect.getSpell().getRawSpellData().hasAttribute(SpellData.ATTRIB_SHRINK)) {
//          for(Spawn target : targets) {
//            target.getSpellEffectManager(effect.getSpell()).addCastResult("CAST_SHRINK");
//          }
//        }
//
//        if(effect.getSpell().getDuration() > 25) {
//          String targetDescription = "";
//          int maxTargets = 5;
//
//          for(Spawn target : targets) {
//            if(maxTargets-- == 0) {
//              targetDescription += " and " + (targets.size() - 5) + " other targets";
//              break;
//            }
//
//            if(!targetDescription.isEmpty()) {
//              targetDescription += ", ";
//            }
//            targetDescription += target.isFriendly() ? target.getName() : target.toString();
//          }
//
//          session.echo("Adding " + effect.getSpell() + " for " + effect.getSpell().getDuration() + "s to " + targetDescription + " [" + castResult + "]");
//        }
//
//        for(Spawn target : targets) {
//          target.getSpellEffectManager(effect.getSpell()).addCastResult(castResult);
//        }
//
//        if(castResult.equals("CAST_SUCCESS") || castResult.equals("CAST_RESIST")) {
//          increaseAgro(effect, targets);
//
//          if(effect.getSpell().getDuration() == 0 && !effect.getSpell().isDetrimental()) {
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

  @Override
  public String toString() {
    return getClass().getName() + "[" + effect + " -> " + targets + "]";
  }
}
