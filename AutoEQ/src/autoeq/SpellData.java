package autoeq;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import autoeq.DebuffCounter.Type;
import autoeq.eq.ResistType;
import autoeq.spelldata.effects.EffectDescriptor;

public class SpellData {

  /**
   * Attributes which donot conflict with themselves.  A max level restriction
   * on one spell does not conflict with another max level restriction on
   * another.
   */
  public static final Set<Attribute> NON_STACKING_ATTRIBS = new HashSet<>();

  public static final int TARGET_TYPE_AE_PC_V1 = 36;
  public static final int TARGET_TYPE_BEAM = 44;

  private static final int TARGET_RESTRICTION_RAID_MOB = 191;

  static {
    NON_STACKING_ATTRIBS.add(Attribute.UNKNOWN254);  // placeholder I think
    NON_STACKING_ATTRIBS.add(Attribute.UNKNOWN57);   // ?
    NON_STACKING_ATTRIBS.add(Attribute.LIMIT_MAX_LEVEL);
    NON_STACKING_ATTRIBS.add(Attribute.LIMIT_TARGET);
    NON_STACKING_ATTRIBS.add(Attribute.LIMIT_EFFECT);
    NON_STACKING_ATTRIBS.add(Attribute.LIMIT_SPELL_TYPE);
    NON_STACKING_ATTRIBS.add(Attribute.LIMIT_SPELL);
    NON_STACKING_ATTRIBS.add(Attribute.LIMIT_MIN_CAST_TIME);
    NON_STACKING_ATTRIBS.add(Attribute.LIMIT_EXCLUDE_COMBAT_SKILLS);
    NON_STACKING_ATTRIBS.add(Attribute.LIMIT_BY_MANA_COST);
    NON_STACKING_ATTRIBS.add(Attribute.SPELL_HASTE);
    NON_STACKING_ATTRIBS.add(Attribute.DISEASE_COUNTER);
    NON_STACKING_ATTRIBS.add(Attribute.POISON_COUNTER);
    NON_STACKING_ATTRIBS.add(Attribute.INCREASE_SPELL_DAMAGE);
    NON_STACKING_ATTRIBS.add(Attribute.CURSE_COUNTER);
    NON_STACKING_ATTRIBS.add(Attribute.INITIAL_DAMAGE);
    NON_STACKING_ATTRIBS.add(Attribute.AUTO_CAST_ONE);
    NON_STACKING_ATTRIBS.add(Attribute.AUTO_CAST_ANY);
    NON_STACKING_ATTRIBS.add(Attribute.INCREASE_MELEE_MITIGATION);
  }

  private final int id;
  private final String name;
  private final String messageWhenCastOnYou;
  private final String messageWhenCastOnOther;
  private final String messageWhenFades;
  private final int range;
  private final int aeRange;
  private final int mana;
  private final int castTime;
  private final int recastTime;
  private final int durationType;
  private final int durationValue;
  private final int enduranceUpkeep;
  private final int spellGroupId;
  private final boolean isBeneficial;

  private final float[] base = new float[12];
  private final float[] base2 = new float[12];
  private final int[] max = new int[12];
  private final int[] calc = new int[12];
  private final int[] attrib = new int[12];
  private final int targetType;
  private final int warLevel;
  private final int clrLevel;
  private final int palLevel;
  private final int rngLevel;
  private final int shdLevel;
  private final int druLevel;
  private final int mnkLevel;
  private final int brdLevel;

  private final int shortBuff;
  private final int canMGBorTGB;
  private final int autoCastId;
  private final int timerId;      // shared lockout timer if > 0

  private final ResistType resistType;

  private Map<Attribute, EffectDescriptor> spellEffects = new HashMap<>();

  public SpellData(String[] fields) {
    this.id = Integer.parseInt(fields[0]);
    this.name = fields[1];
    this.messageWhenCastOnYou = fields[6];
    this.messageWhenCastOnOther = fields[7];
    this.messageWhenFades = fields[8];
    this.range = toInt(fields[9]);
    this.aeRange = toInt(fields[10]);
    this.castTime = toInt(fields[13]);
    this.recastTime = toInt(fields[15]);
    this.durationType = toInt(fields[16]);
    this.durationValue = toInt(fields[17]);
    this.mana = toInt(fields[19]);

    int resistCode = toInt(fields[85]);  // resist

    this.resistType = resistCode == 0 ? ResistType.NONE :
                      resistCode == 1 ? ResistType.MAGIC :
                      resistCode == 2 ? ResistType.FIRE :
                      resistCode == 3 ? ResistType.COLD :
                      resistCode == 4 ? ResistType.POISON :
                      resistCode == 5 ? ResistType.DISEASE :
                      resistCode == 6 ? ResistType.CHROMATIC :
                      resistCode == 7 ? ResistType.PRISMATIC :
                      resistCode == 8 ? ResistType.PHYSICAL :
                      resistCode == 9 ? ResistType.CORRUPTION :
                                        ResistType.UNKNOWN;

    for(int i = 0; i < 12; i++) {
      base[i] = toFloat(fields[i + 20]);
      base2[i] = toFloat(fields[i + 32]);
      max[i] = toInt(fields[i + 44]);
      calc[i] = toInt(fields[i + 70]);
      attrib[i] = toInt(fields[i + 86]);

      Attribute spellEffect = Attribute.getByAttributeCode(attrib[i]);

      if(spellEffect != null) {
        if(spellEffect != Attribute.DAMAGE || base2[i] != TARGET_RESTRICTION_RAID_MOB) {
          spellEffects.put(spellEffect, spellEffect.createEffect((int)base[i], (int)base2[i], max[i], calc[i]));
        }
      }
    }

    targetType = toInt(fields[98]);
    warLevel = toInt(fields[104]);
    clrLevel = toInt(fields[105]);
    palLevel = toInt(fields[106]);
    rngLevel = toInt(fields[107]);
    shdLevel = toInt(fields[108]);
    druLevel = toInt(fields[109]);
    mnkLevel = toInt(fields[110]);
    brdLevel = toInt(fields[111]);

    autoCastId = toInt(fields[150]);
    shortBuff = toInt(fields[154]);
    canMGBorTGB = toInt(fields[185]);
    timerId = toInt(fields[167]);
    enduranceUpkeep = toInt(fields[174]);
    spellGroupId = toInt(fields[207]);
    isBeneficial = toBoolean(fields[83]);
  }

  private static float toFloat(String s) {
    if(s.trim().length() == 0) {
      return 0;
    }
    return Float.parseFloat(s);
  }

  private static int toInt(String s) {
    if(s.trim().length() == 0) {
      return 0;
    }
    if(s.length() > 10) {
      return Integer.MAX_VALUE;
    }

    return Integer.parseInt(s);
  }

  private static boolean toBoolean(String s) {
    if(s.trim().length() == 0 || s.trim().equals("0")) {
      return false;
    }

    return true;
  }

  private boolean isStackData(int i) {
    if(!((attrib[i] == 10 && (base[i] == -6 || base[i] == 0)) ||
         (attrib[i] == Attribute.INITIAL_DAMAGE.getAttribCode() && base[i] > 0 && targetType == 6) ||
         ((attrib[i] == Attribute.DAMAGE.getAttribCode() || attrib[i] == Attribute.MANA.getAttribCode()) && (base[i] < 0 || (durationType == 0 && durationValue == 0))) ||  // Direct Heals/ManaInfusions always stack with regens.
         (attrib[i] == 148 || attrib[i] == 149))) {
      return true;
    }

    return false;
  }

  public int getTargetType() {
    return targetType;
  }

  /**
   * Returns <code>true</code> if this spell blocks the given spell.
   */
  public boolean blocks(SpellData sd) {
    for(int i = 0; i < 12; i++) {
      if(attrib[i] == 148 || attrib[i] == 149) {
        int slot = calc[i] - 200 - 1;

        if(sd.attrib[slot] == base[i]) {
          if(max[i] > 0) {
            if(sd.base[slot] < max[i]) {
              return true;
            }
          }
          else {
            return true;
          }
        }
      }
    }

    return false;
  }

  public int getAutoCastId() {
    return autoCastId;
  }

  public int getTimerId() {
    return timerId;
  }

  public int getRecastMillis() {
    return recastTime;
  }

  public boolean equalOrOfDifferentRank(SpellData sd) {
    if(sd.id == id) {
      return true;
    }
    if(sd.spellGroupId != 0 && spellGroupId != 0 && sd.spellGroupId == spellGroupId) {  // Spells of different ranks don't(?) overwrite each other
      return true;
    }

    return false;
  }

  public boolean isCombatAbility() {
    return enduranceUpkeep > 0;
  }

  public boolean stacksWith(SpellData sd) {
    if(equalOrOfDifferentRank(sd)) {
      return true;
    }

    for(int i = 0; i < 12; i++) {
      if(sd.attrib[i] == attrib[i] && !NON_STACKING_ATTRIBS.contains(Attribute.getByAttributeCode(attrib[i]))) {  // Do stack test if attrib[i] matches the other and is not an attribute that is ignored for stacking purposes
        if((attrib[i] != Attribute.MESMERIZE.getAttribCode() && attrib[i] != Attribute.AC.getAttribCode()) || base[i] < sd.base[i]) {  // Larger values overwrite smaller values for certain attributes
          if(isStackData(i) && sd.isStackData(i)) {
            return false;
          }
        }
      }
    }

    /*
     * Disc stacking:
     *
     * Basically, discs that go to the Combat Abilities window donot stack, however determining which ones do
     * and which ones don't is akward.  The rules for a disc going to the Combat Abilities window are:
     *
     * 1) Must be beneficial
     * 2) Must have either upkeep or a timerId between 1 and 6 with a duration > 0
     */
    if(isBeneficial && sd.isBeneficial && (enduranceUpkeep > 0 || (timerId >= 1 && timerId <= 6 && durationValue > 0)) && (sd.enduranceUpkeep > 0 || (sd.timerId >= 1 && sd.timerId <= 6 && sd.durationValue > 0))) {
      return false;
    }

//    if(blocks(sd)) {
//      return false;
//    }

    if(sd.blocks(this)) {
      return false;
    }

    return true;
  }

  public int getId() {
    return id;
  }
//
//  public int getMana() {
//    return mana;
//  }

  public String getMessageWhenCastOnOther() {
    return messageWhenCastOnOther;
  }

  public String getMessageWhenCastOnYou() {
    return messageWhenCastOnYou;
  }

  public String getMessageWhenFades() {
    return messageWhenFades;
  }

  public String getName() {
    return name;
  }

  public int getCastTime() {
    return castTime;
  }

  public ResistType getResistType() {
    return resistType;
  }

  public float getBase(int index) {
    return base[index];
  }

  public float getBase2(int index) {
    return base2[index];
  }

  public int getMax(int index) {
    return max[index];
  }

  public int getCalc(int index) {
    return calc[index];
  }

  public int getAttrib(int index) {
    return attrib[index];
  }

  public int getAERange() {
    return aeRange;
  }

  public int getRange() {
    return range;
  }

  public int getMana() {
    return mana;
  }

  public boolean getCanMGBorTGB() {
    return canMGBorTGB != 0;
  }

  public boolean isShortBuff() {
    return shortBuff != 0;
  }

  public DebuffCounter getDebuffCounters() {
    // attrib = 36 = poison
    // attrib = 35 = disease
    // attrib = 116 = curse
    // attrib = 369 = corruption

    for(int i = 0; i < 12; i++) {
      int a = attrib[i];

      if(a == 35) {
        return new DebuffCounter(Type.DISEASE, (int)base[i]);
      }
      if(a == 36) {
        return new DebuffCounter(Type.POISON, (int)base[i]);
      }
      if(a == 116) {
        return new DebuffCounter(Type.CURSE, (int)base[i]);
      }
      if(a == 369) {
        return new DebuffCounter(Type.CORRUPTION, (int)base[i]);
      }
    }

    return null;
  }

  public int getBrdLevel() {
    return brdLevel;
  }

  public int getClrLevel() {
    return clrLevel;
  }

  public int getDruLevel() {
    return druLevel;
  }

  public int getMnkLevel() {
    return mnkLevel;
  }

  public int getPalLevel() {
    return palLevel;
  }

  public int getRngLevel() {
    return rngLevel;
  }

  public int getShdLevel() {
    return shdLevel;
  }

  public int getWarLevel() {
    return warLevel;
  }

  public boolean hasAttribute(int attribute) {
    return getAttributeIndex(attribute) >= 0;
  }

  public boolean hasAttribute(Attribute spellEffect) {
    return getAttributeIndex(spellEffect.getAttribCode()) >= 0;
  }

  public int getAttributeIndex(int attribute) {
    for(int i = 0; i < 12; i++) {
      if(getAttrib(i) == attribute) {
        return i;
      }
    }

    return -1;
  }

  public EffectDescriptor getEffect(Attribute spellEffect) {
    return spellEffects.get(spellEffect);
  }

  @Override
  public String toString() {
    return name;
  }
}
