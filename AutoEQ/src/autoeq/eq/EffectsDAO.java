package autoeq.eq;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import autoeq.effects.AbilityEffect;
import autoeq.effects.AlternateAbilityEffect;
import autoeq.effects.CommandEffect;
import autoeq.effects.DisciplineEffect;
import autoeq.effects.Effect;
import autoeq.effects.ItemEffect;
import autoeq.effects.SongEffect;
import autoeq.effects.SpellEffect;

public class EffectsDAO {
  private final CharacterDAO characterDAO;
  private final Map<String, Effect> effects = new HashMap<>();
  private final EverquestSession session;

  public EffectsDAO(EverquestSession session, CharacterDAO characterDAO) {
    this.session = session;
    this.characterDAO = characterDAO;
  }

  public Collection<Effect> getKnownEffects() {
    return effects.values();
  }

  public Effect getEffect(String effectDescription) {
    return getEffect(effectDescription, 10);
  }

  /**
   * Gets an Effect based on a description string.<br>
   *
   * This only creates effects available to the character at the time, ie. spells must be scribed and clickeys
   * must be on the character.
   */
  public Effect getEffect(String effectDescription, int agro) {
    boolean hasEffect = effects.containsKey(effectDescription);

    if(!hasEffect) {
      Effect effect = null;
      int colon = effectDescription.indexOf(':');
      String type = effectDescription.substring(0, colon);
      String name = effectDescription.substring(colon + 1);

      if(type.equals("Spell")) {
        Spell spell = characterDAO.getSpellFromBook(name);
        if(spell != null && spell.getLevel() <= session.getMe().getLevel()) {
          effect = new SpellEffect(session, spell, agro, 500);
        }
      }
      else if(type.equals("Song")) {
        Spell spell = characterDAO.getSpellFromBook(name);
        if(spell != null) {
          effect = new SongEffect(session, spell, agro, 500);
        }
      }
      else if(type.equals("Item")) {
//        String[] result = translate("${FindItem[=" + name + "].Spell.ID},${FindItem[=" + name + "].Attuneable},${FindItem[=" + name + "].NoDrop}").split(",");

        // Checks if item exists, and if it is attuned (if applicable)
//        if(!result[0].equals("NULL") && (result[1].equals("FALSE") || result[2].equals("TRUE"))) {
        effect = new ItemEffect(session, name, agro, 500);
//        }
      }
      else if(type.equals("Alt")) {
        Spell spell = characterDAO.getAltAbility(name);
        if(spell != null) {
          effect = new AlternateAbilityEffect(session, name, spell, agro, 500);
        }
      }
      else if(type.equals("Discipline")) {
        Spell spell = characterDAO.getCombatAbilityFromList(name);
        if(spell != null) {
          effect = new DisciplineEffect(session, name, spell, agro, 500);
        }
      }
      else if(type.equals("Command")) {
        String[] parts = name.split(":");
        int range = parts.length > 1 ? Integer.parseInt(parts[1]) : 0;
        TargetType targetType = parts.length > 2 ? TargetType.valueOf(parts[2].toUpperCase()) : (range > 0 ? TargetType.SINGLE : TargetType.SELF);

        effect = new CommandEffect(session, parts[0], range, 500, targetType);
      }
      else if(type.equals("Ability")) {
        int rangeSeparator = name.indexOf(':');
        int range = 50;

        if(rangeSeparator > 0) {
          range = Integer.parseInt(name.substring(0, rangeSeparator));
          name = name.substring(rangeSeparator + 1);
        }

        effect = new AbilityEffect(session, name, range, 500);
      }

      effects.put(effectDescription, effect);
    }

    return effects.get(effectDescription);
  }

  public void clear() {
    effects.clear();
  }
}
