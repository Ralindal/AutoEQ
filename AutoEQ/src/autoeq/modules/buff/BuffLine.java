package autoeq.modules.buff;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import autoeq.ExpressionEvaluator;
import autoeq.TargetPattern;
import autoeq.effects.Effect;
import autoeq.eq.EverquestSession;
import autoeq.eq.ExpressionRoot;
import autoeq.eq.Priority;
import autoeq.eq.Spawn;
import autoeq.eq.TargetType;
import autoeq.ini.Section;


public class BuffLine implements Iterable<EffectSet> {
  private final String validTargets;
  private final String profiles;
  private final List<String> conditions;
  private final List<EffectSet> effectSets = new ArrayList<>();
  private String name;
  private final int gem;
  private final int priority;

  private boolean enabled = true;

  public BuffLine(EverquestSession session, Section section) {
    this.gem = section.get("Gem") != null ? Integer.parseInt(section.get("Gem")) : 0;
    this.validTargets = section.get("ValidTargets");
    this.profiles = section.get("Profile");
    this.name = section.getName();
    this.conditions = section.getAll("Condition");
    this.priority = Priority.decodePriority(section.get("Priority"), 300);

    List<String> spellNamePairs = section.getAll("Spell");

    for(String s : spellNamePairs) {
      Effect groupEffect = null;
      Effect singleEffect = null;

      for(String name : s.split("\\|")) {
        Effect effect = session.getEffect(name, 10);

        if(effect != null) {
          if(effect.getSpell().getTargetType() == TargetType.SINGLE) {
            singleEffect = effect;
          }
          else {
            groupEffect = effect;
          }
        }
      }

      if(singleEffect != null || groupEffect != null) {
        effectSets.add(new EffectSet(singleEffect, groupEffect));
      }
    }
  }

  public int getGem() {
    return gem;
  }

  public String getProfile() {
    return profiles;
  }

  /**
   * Checks if target is a valid target.
   */
  public boolean isValidTarget(Spawn target) {
    boolean valid = validTargets != null ? TargetPattern.isValidTarget(validTargets, target) : true;

    if(valid) {
      valid = ExpressionEvaluator.evaluate(conditions, new ExpressionRoot(target.getSession(), target, null, null), this);
    }

    return valid;
  }

  @Override
  public Iterator<EffectSet> iterator() {
    return effectSets.iterator();
//    final String[] spells = spellLine.split("\\|");
//
//    return new Iterator<SpellPair>() {
//      private Spell next;
//      private int index = 0;
//
//      public boolean hasNext() {
//        checkNext();
//        return next != null;
//      }
//
//      public Spell next() {
//        checkNext();
//        if(!hasNext()) {
//          throw new NoSuchElementException();
//        }
//
//        Spell result = next;
//        next = null;
//        return result;
//      }
//
//      private void checkNext() {
//        while(next == null && index < spells.length) {
//          next = session.getSpellByName(spells[index++]);
//        }
//      }
//
//      public void remove() {
//        throw new UnsupportedOperationException();
//      }
//    };
  }


  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
  }

  public boolean isEnabled() {
    return enabled;
  }

  @Override
  public String toString() {
    return name;
  }

  public double getPriority() {
    return priority;
  }

//  Sub IsValidTarget(int iSpawnId, string sValidTargets)
//   /if (${sValidTargets.Find[ ${Spawn[${iSpawnId}].Class.ShortName} ]} && !${Spawn[${iSpawnId}].Type.Equal[pet]}) /return TRUE
//   /if (${sValidTargets.Find[ self ]} && ${iSpawnId} == ${Me.ID}) /return TRUE
//   /if (${sValidTargets.Find[ mypet ]} && ${iSpawnId} == ${Me.Pet.ID}) /return TRUE
//   /if (${sValidTargets.Find[ pet ]} && ${Spawn[${iSpawnId}].Type.Equal[pet]}) /return TRUE
// /return FALSE
}
