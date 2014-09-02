package autoeq;

import java.util.HashMap;
import java.util.Map;

import autoeq.spelldata.effects.EffectDescriptor;

public enum Attribute {
  DAMAGE(0),
  AC(1),
  MOVEMENT(3),
  SLOW(11),
  MANA(15),
  CHARM(22),
  MESMERIZE(31),
  DISEASE_COUNTER(35),
  POISON_COUNTER(36),
  DIVINE_AURA(40),
  RUNE(55),
  UNKNOWN57(57),
  DAMAGE_SHIELD(59),
  SPELL_RUNE(78),
  INITIAL_DAMAGE(79),
  SILENCE(96),
  HEAL_OVER_TIME(100),
  MOUNT(113),
  CURSE_COUNTER(116),
  REVERSE_DAMAGE_SHIELD(121),
  SPELL_HASTE(127),
  MANA_COST(132),
  LIMIT_MAX_LEVEL(134),
  LIMIT_TARGET(136),
  LIMIT_EFFECT(137),
  LIMIT_SPELL_TYPE(138),
  LIMIT_SPELL(139),
  LIMIT_MIN_CAST_TIME(143),
  SPELL_REFLECT(158),          // base1 = reflect chance, max = original damage %
  MITIGATE_MELEE_DAMAGE(162),  // base1 = percentage mitigated, base2 = max total damage, max = max damage per hit
  INCREASE_MELEE_MITIGATION(168),
  UNKNOWN254(254),
  INCREASE_SPELL_CHANCE_TO_CRIT_AND_CRIT_DAMAGE(294),  // base1 = crit chance, base2 = extra crit damage (minus 100)
  INCREASE_SPELL_DAMAGE(296),
  SHRINK(298),
  LIMIT_EXCLUDE_COMBAT_SKILLS(311),
  AUTO_CAST_ONE(340),          // Auto Casts one of the spells (single roll); base1 = chance
  LIMIT_BY_MANA_COST(348),
  AUTO_CAST_ANY(374),          // Auto Casts any of the spells by chance (multiple rolls); base1 = chance
  RESET_SPELL_TIMERS(389),
  TWIN_CAST(399),
  MANA_HP_DRAIN(401);  // base2 determines hp drain

  private static final Map<Integer, Attribute> byAttributeCode = new HashMap<>();

  static {
    for(Attribute attribute : Attribute.values()) {
      byAttributeCode.put(attribute.getAttribCode(), attribute);
    }
  }

  private int attribCode;
  private Class<?> effectClass;

  Attribute(int attribCode, Class<?> effectClass) {
    this.attribCode = attribCode;
    this.effectClass = effectClass;
  }

  Attribute(int attribCode) {
    this(attribCode, null);
  }

  public int getAttribCode() {
    return attribCode;
  }

  public EffectDescriptor createEffect(int base1, int base2, int max, int calc) {
    return new EffectDescriptor(getAttribCode(), base1, base2, max, calc);
  }

  public static Attribute getByAttributeCode(int i) {
    return byAttributeCode.get(i);
  }
}
