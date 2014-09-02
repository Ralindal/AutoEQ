package autoeq.eq;

import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.List;

import autoeq.effects.Effect;

public class SpellUtil {

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

    return checkRangeAndTargetType(effect, targets);
  }

  public static boolean checkRangeAndTargetType(Effect effect, Spawn... targets) {
    Spell spell = effect.getSpell();
    double rangeMultiplier = spell.getTargetType() == TargetType.SINGLE || spell.getTargetType() == TargetType.BOLT || spell.getTargetType() == TargetType.SELF || spell.getTargetType() == TargetType.PBAE ? 1.0 : 0.8;
    double spellRange = spell.getTargetType() == TargetType.GROUP ? spell.getAERange() : spell.getRange();

    for(Spawn target : targets) {
      if(spellRange != 0.0 && target.getDistance() > spellRange * rangeMultiplier) {
        return false;
      }
      if(spell.isDetrimental() && !target.isEnemy() && spell.getTargetType() != TargetType.PBAE) {
        return false;
      }
      if(!spell.isDetrimental() && spell.getTargetType().isTargeted() && !target.isFriendly() && spell.getTargetType() != TargetType.CORPSE) {
        return false;
      }
    }

    return true;
  }

  /**
   * Returns the targets that are in range of the spell effect and
   * would be affected by it.  The first target is assumed to be the target
   * of a Targetted AE spell and thus counts as the origin of the effect.
   *
   * @param effect the effect
   * @param targets the targets
   * @return the number of targets that are in range of the spell effect and would be affected by it
   */
  public static List<Spawn> findAffectedTargets(SpellLine spellLine, Me me, List<Spawn> targets) {
    Effect effect = spellLine.getEffect();
    Spell spell = effect.getSpell();
    TargetType targetType = effect.getTargetType();
    double rangeMultiplier = targetType == TargetType.GROUP || targetType == TargetType.TARGETED_AE ? 0.8 : 1.0;

    List<Spawn> targetsAffected = new ArrayList<>();

    for(Spawn target : targets) {
      if(target.isValidTypeFor(effect) && (spell == null || target.isValidLevelFor(spell)) && (!effect.isDetrimental() || !targetType.isTargeted() || target.inLineOfSight())) {
        if(targetType.isAreaOfEffect()) {
          if(spell != null) {
            if(targetType.isTargeted()) {
              if(targets.get(0).getDistance() <= effect.getRange() * rangeMultiplier &&
                  target.getDistance(targets.get(0)) <= spell.getAERange()) {
                targetsAffected.add(target);
              }
            }
            else if(targetType == TargetType.BEAM) {
              float direction = targets.get(0).getDirection();  // direction towards primary target, in degrees

              Point2D.Double pointLeft = new Point2D.Double(me.getX() + spell.getAERange() / 2 * Math.sin(Math.toRadians(direction - 90)), me.getY() + spell.getAERange() / 2 * Math.cos(Math.toRadians(direction - 90)));
              Point2D.Double pointRight = new Point2D.Double(me.getX() + spell.getAERange() / 2 * Math.sin(Math.toRadians(direction + 90)), me.getY() + spell.getAERange() / 2 * Math.cos(Math.toRadians(direction + 90)));
              Point2D.Double pointFarLeft = new Point2D.Double(pointLeft.x + spell.getRange() * Math.sin(Math.toRadians(direction)), pointLeft.y + spell.getRange() * Math.cos(Math.toRadians(direction)));
              Point2D.Double pointFarRight = new Point2D.Double(pointRight.x + spell.getRange() * Math.sin(Math.toRadians(direction)), pointRight.y + spell.getRange() * Math.cos(Math.toRadians(direction)));

              Path2D.Double path = new Path2D.Double();
              path.moveTo(pointLeft.x, pointLeft.y);
              path.lineTo(pointFarLeft.x, pointFarLeft.y);
              path.lineTo(pointFarRight.x, pointFarRight.y);
              path.lineTo(pointRight.x, pointRight.y);
              path.closePath();

              if(path.contains(target.getX(), target.getY())) {
                targetsAffected.add(target);
              }
            }
            else if(target.getDistance() <= spell.getAERange()) {
              targetsAffected.add(target);
            }
          }
        }
        else if(targetType.isTargeted() && target.getDistance() <= effect.getRange() * rangeMultiplier * spellLine.getRangeExtensionFactor()) {
          targetsAffected.add(target);
          break;
        }
        else if(targetType == TargetType.SELF && target.isMe()) {
          targetsAffected.add(target);
          break;
        }
      }
    }

    return targetsAffected;
  }
}
