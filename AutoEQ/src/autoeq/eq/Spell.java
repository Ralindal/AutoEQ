package autoeq.eq;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import autoeq.SpellData;
import autoeq.Attribute;
import autoeq.spelldata.effects.EffectDescriptor;

public class Spell {

  private final EverquestSession session;
  private final int id;
  private final int level;
  private final String name;
  private final double aeRange;
  private final double range;
  private final int mana;
  private final int duration;
  private final int castTime;
  private final int myCastTime;
  private final String targetType;   // self, group v1, group v2
  private final String spellType;
  private final SpellData sd;
  private final boolean canMGBorTGB;
  private final boolean isSlow;
  private final int maxTargetLevel;
  private final boolean isCharm;
  private final boolean isMez;
  private final boolean isShortMez;
  private final boolean isLongMez;
  private final boolean isHealOverTime;
  private final boolean isTwinCast;
  private final boolean isTwinHeal;
  private final boolean isSnared;
  private final boolean isManaHarvest;
  private final TargetType cachedTargetType;
  private final Set<EffectType> effectTypes;
  private final ResistType resistType;

//  private final Map<Integer, Boolean> willStackCache = new HashMap<Integer, Boolean>();
//
//  public Spell(EverquestSession session, int id) {
//    this.session = session;
//    this.id = id;
//    this.level = Integer.parseInt(session.evaluate("${Spell[" + id + "].Level}"));
//    this.name = session.evaluate("${Spell[" + id + "]}");
//    this.aeRange = Double.parseDouble(session.evaluate("${Spell[" + id + "].AERange}"));
//    this.range = Double.parseDouble(session.evaluate("${Spell[" + id + "].Range}"));
//    this.mana = Integer.parseInt(session.evaluate("${Spell[" + id + "].Mana}"));
//    this.duration = Integer.parseInt(session.evaluate("${Spell[" + id + "].Duration.TotalSeconds}"));
//    this.targetType = session.evaluate("${Spell[" + id + "].TargetType}").toLowerCase();
//    this.spellType = session.evaluate("${Spell[" + id + "].SpellType}").toLowerCase();
//  }

  public Spell(EverquestSession session, int id) {
    sd = session.getRawSpellData(id);

    if(sd == null) {
      throw new IllegalArgumentException("Cannot find spell in spell data: " + id);
    }

    try {
      this.session = session;
      this.id = sd.getId();
      this.name = sd.getName();
      this.aeRange = sd.getAERange();
      this.range = sd.getRange();
      this.mana = sd.getMana();
      this.castTime = sd.getCastTime();
      this.canMGBorTGB = sd.getCanMGBorTGB();

      MySpell mySpell = session.getMySpell(sd);

      this.level = mySpell.getLevel();
      this.duration = mySpell.getDuration();
      this.targetType = mySpell.getTargetType();
      this.spellType = mySpell.getSpellType();
      this.myCastTime = mySpell.getMyCastMillis();

      this.cachedTargetType = determineTargetType(sd, targetType, aeRange, canMGBorTGB);

      double bestChance = 0;
      ResistType bestResistType = ResistType.NONE;

      for(ResistType resistType : ResistType.values()) {
        if(resistType != ResistType.NONE) {
          double chance = getResistTypeChance(resistType);

          if(chance > bestChance) {
            bestChance = chance;
            bestResistType = resistType;
          }
        }
      }

      this.resistType = bestResistType;
    }
    catch(NullPointerException e) {
      throw new RuntimeException("Unable to find spell with ID " + id, e);
    }

    try {

  //    this.isMez = sd.hasAttribute(SpellEffect.MESMERIZE) && sd.getBase(sd.getAttributeIndex(SpellEffect.MESMERIZE)) == 2;  // Long duration mez, warning, max target level won't work either if you limit it like this!
      this.isMez = sd.hasAttribute(Attribute.MESMERIZE) && duration > 10;
      this.isShortMez = sd.hasAttribute(Attribute.MESMERIZE) && sd.getBase(sd.getAttributeIndex(Attribute.MESMERIZE.getAttribCode())) == 1;
      this.isLongMez = sd.hasAttribute(Attribute.MESMERIZE) && sd.getBase(sd.getAttributeIndex(Attribute.MESMERIZE.getAttribCode())) == 2;
      this.isCharm = sd.hasAttribute(Attribute.CHARM);
      this.isTwinCast = sd.hasAttribute(Attribute.TWIN_CAST) && sd.getEffect(Attribute.TWIN_CAST).getBase1() >= 100 && sd.getEffect(Attribute.LIMIT_SPELL_TYPE).getBase1() == 0;
      this.isTwinHeal = sd.hasAttribute(Attribute.TWIN_CAST) && sd.getEffect(Attribute.TWIN_CAST).getBase1() >= 100 && sd.getEffect(Attribute.LIMIT_SPELL_TYPE).getBase1() != 0;
      this.isManaHarvest = sd.hasAttribute(Attribute.MANA) && sd.getEffect(Attribute.MANA).getBase1() > 300 && duration == 0;
      this.maxTargetLevel = this.isMez   ? sd.getEffect(Attribute.MESMERIZE).getMax() :
                            this.isCharm ? sd.getEffect(Attribute.CHARM).getMax() :
                                           255;

      isHealOverTime = sd.hasAttribute(Attribute.HEAL_OVER_TIME) || (sd.hasAttribute(Attribute.DAMAGE) && sd.getEffect(Attribute.DAMAGE).getBase1() > 100 && duration <= 120);
      isSlow = sd.hasAttribute(Attribute.SLOW);
      isSnared = sd.hasAttribute(Attribute.MOVEMENT) && sd.getEffect(Attribute.MOVEMENT).getBase1() < 0;

      Set<EffectType> effectTypes = new HashSet<>();

      if(isMez) {
        effectTypes.add(EffectType.MEZ);
      }
      if(isShortMez) {
        effectTypes.add(EffectType.SHORT_MEZ);
      }
      if(isLongMez) {
        effectTypes.add(EffectType.LONG_MEZ);
      }
      if(isCharm) {
        effectTypes.add(EffectType.CHARM);
      }
      if(isSnared) {
        effectTypes.add(EffectType.SNARED);
      }
      if(isSlow) {
        effectTypes.add(EffectType.SLOW);
      }
      if(isHealOverTime) {
        effectTypes.add(EffectType.HEAL_OVER_TIME);
      }
      if(isTwinCast) {
        effectTypes.add(EffectType.TWIN_CAST);
      }
      if(isTwinHeal) {
        effectTypes.add(EffectType.TWIN_HEAL);
      }
      if(isManaHarvest) {
        effectTypes.add(EffectType.MANA_HARVEST);
      }
      if(getDamageOverTime() > 0) {
        effectTypes.add(EffectType.DAMAGE_OVER_TIME);
      }
      if(sd.getEffect(Attribute.MOUNT) != null) {
        effectTypes.add(EffectType.MOUNTED);
      }
      if(sd.getEffect(Attribute.SILENCE) != null) {
        effectTypes.add(EffectType.SILENCE);
      }

      EffectDescriptor manaCost = sd.getEffect(Attribute.MANA_COST);

      if(manaCost != null) {
        if(manaCost.getBase1() == 100 && manaCost.getBase2() == 100) {
          effectTypes.add(EffectType.MANA_GIFT);
        }
        else if(manaCost.getBase1() < 0 || manaCost.getBase2() < 0) {
          effectTypes.add(EffectType.MANA_PENALTY);
        }
      }

      this.effectTypes = Collections.unmodifiableSet(effectTypes);
    }
    catch(Exception e) {
      throw new IllegalArgumentException("Error creating spell: " + sd, e);
    }
  }

  public Set<EffectType> getEffectTypes() {
    return effectTypes;
  }

  public boolean isSnared() {
    return isSnared;
  }

  public int getLevel() {
    return level;
  }

  public ResistType getResistType() {
    return resistType;
  }

  public boolean usesResist(ResistType resistType) {
    return getResistTypeChance(resistType) > 0;
  }

  /**
   * Returns the chance that a certain resist type is used when casting this spell.
   * 0 means no chance.  1 means 100% chance.  More than 1 means multiple spells.
   *
   * @param resistType a resistType
   * @return a chance, 0 or greater.
   */
  public double getResistTypeChance(ResistType resistType) {
    double chance = 0.0;

    if(isDetrimental()) {
      if(sd.getResistType().equals(resistType)) {
        chance += 1.0;
      }

      for(SpellAndChance spellAndChance : getAutoCastedSpellsAndChances()) {
        chance += spellAndChance.getSpell().getResistTypeChance(resistType) * spellAndChance.getChance();
      }
    }

    return chance;
  }

  /**
   * Cast time in milliseconds.  Note that this will return incorrect values for spells attached to items, use effect.getCastTime() instead.
   * TODO this fluctuates, needs periodic updating
   */
  public int getCastTime() {
    return myCastTime;
  }

  public int getStandardCastTime() {
    return castTime;
  }

  public int getRecastTime() {
    return sd.getRecastMillis();
  }

  public int getMaxTargetLevel() {
    return maxTargetLevel;
  }

  public int getDamageOverTime() {
    if(getDuration() > 0 && isDetrimental()) {
      EffectDescriptor hp = sd.getEffect(Attribute.DAMAGE);

      if(hp != null) {
        return -hp.getCalculatedBase1(session.getMe().getLevel());  // TODO this uses character level instead of casters level (a DOT on you is not casted by you)
      }
    }

    return 0;
  }

  public int getDamage() {
    int totalDamage = 0;

    if(getDuration() == 0 && isDetrimental()) {
      EffectDescriptor hp = sd.getEffect(Attribute.DAMAGE);

      if(hp != null) {
        totalDamage += -hp.getCalculatedBase1(session.getMe().getLevel());  // TODO this uses character level instead of casters level (a DOT on you is not casted by you)
      }

      for(SpellAndChance spellAndChance : getAutoCastedSpellsAndChances()) {
        totalDamage += spellAndChance.getSpell().getDamage() * spellAndChance.getChance();
      }
    }

    return totalDamage;
  }

//  public boolean willStack(Spell spell) {
//    if(!willStackCache.containsKey(spell.id)) {
//      willStackCache.put(spell.id, session.evaluate("${Spell[" + id + "].WillStack[" + spell.id + "]}").equals("TRUE"));
//    }
//
//    return willStackCache.get(spell.id);
//  }

  public boolean willStack(Spell spell) {
    SpellData sd1 = session.getRawSpellData(id);
    SpellData sd2 = session.getRawSpellData(spell.id);

//    if(sd1.getName().contains("Unity") || sd2.getName().contains("Unity")) {
//      System.out.println("willStack " + sd1.getName() + " " + sd1.isShortBuff() + " : " + sd2.getName() + " --> " + sd1.stacksWith(sd2));
//    }

    // Assume that short buffs always stack with regular buffs
    if(!sd1.isCombatAbility() && !sd2.isCombatAbility()) {
      if(sd1.isShortBuff() || sd2.isShortBuff()) {
        return true;
      }
    }

    return sd1.stacksWith(sd2);
  }

  public boolean equalOrOfDifferentRank(Spell spell) {
    return sd.equalOrOfDifferentRank(spell.sd);
  }

  public String getName() {
    return name;
  }

  public int getId() {
    return id;
  }

  /*
   * Note about range and AE range:
   *
   * The spell file contains many mistakes when it comes to range and AE range.  There
   * are spells that are PBAE but specify both a range and AE range, however for PBAE
   * only the AE range is valid.
   *
   * Making assumptions based on the values of range and AE range without taking the
   * target type into account is therefore a bad idea.
   */

  public double getRange() {
    return aeRange > 0 && range == 0.0 ? aeRange : range;
  }

  public double getAERange() {
    return aeRange;
  }

  public boolean isDetrimental() {
    return spellType.equals("detrimental");
  }

  public TargetType getTargetType() {
    return cachedTargetType;
  }

  public static TargetType determineTargetType(SpellData sd, String targetType, double aeRange, boolean canMGBorTGB) {

    /*
     * Note: Scorch Bones = single target, but with AE range of 1.0
     *       Revile = PBAE, but with range of 150 and ae range of 50 (range is ignored obviously)
     */

    if(targetType.equals("pb ae")) {
      return TargetType.PBAE;
    }
    else if(targetType.equals("corpse")) {
      return TargetType.CORPSE;
    }
    else if(targetType.equals("single")) {
      return TargetType.SINGLE;
    }
    else if(targetType.equals("targeted ae")) {
      return TargetType.TARGETED_AE;
    }
    else if(targetType.equals("self")) {
      return TargetType.SELF;
    }
    else if(targetType.equals("line of sight")) {
      return TargetType.BOLT;
    }
    else if(targetType.equals("group v1") || targetType.equals("group v2")) {
      // Elixir of the Acquital is group v1; can be mgb'd / tgb'd
      // Fool the Fallen is group v2; cannot be mgb'd / tgb'd
      // Word of Reformation is group v1; cannot be mgb'd / tgb'd
      // Hand of Virtue is group v2; can be mgb'd / tgb'd
      return canMGBorTGB ? TargetType.TARGETED_GROUP : TargetType.GROUP;
    }
    else if(targetType.equals("caster pb pc")) {  // For example: Glyph Spray
      return TargetType.TARGETED_AE;  // TARGETED_GROUP is wrong as it has a default target, Glyph Spray MUST have a PC targetted
    }
    else if(targetType.equals("ae pc v1")) {  // For example: Large Modulating Shard
      return TargetType.TARGETED_AE;
    }
    else if(targetType.equals("beam")) {  // For example: Beam of Slumber
      return TargetType.BEAM;
    }
    else if(targetType.equals("unknown")) {
      System.out.println("[WARNING] Spell::determineTargetType - Unknown target type for: " + sd);
      if(sd.getTargetType() == SpellData.TARGET_TYPE_AE_PC_V1) {  // Glyph Spray -> UNKNOWN... AE PC v1 (PC Only) [code=36]
        return TargetType.TARGETED_AE;  // TARGETED_GROUP is wrong as it has a default target, Glyph Spray MUST have a PC targetted
      }
      else if(sd.getTargetType() == SpellData.TARGET_TYPE_BEAM) {
        return TargetType.BEAM;
      }
    }

    return aeRange > 0.0 ? TargetType.GROUP : TargetType.SINGLE;
  }

  public int getMana() {
    return mana;
  }

  /**
   * Duration in seconds.
   */
  public int getDuration() {
    return duration;
  }

  @Override
  public String toString() {
    return "Spell: " + name;
  }

  public SpellData getRawSpellData() {
    return sd;
  }

  public boolean isSlow() {
    return isSlow;
  }

  public boolean isMez() {
    return isMez;
  }

  public boolean isCharm() {
    return isCharm;
  }

  public boolean isHealOverTime() {
    return isHealOverTime;
  }

  public List<Spell> getAutoCastedSpells() {
    List<Spell> autoCastedSpells = new ArrayList<>();

    for(int i = 0; i < 12; i++) {
      if(sd.getAttrib(i) == Attribute.AUTO_CAST_ANY.getAttribCode() || sd.getAttrib(i) == Attribute.AUTO_CAST_ONE.getAttribCode()) {
        if(sd.getBase(i) == 100) { // 100 = 100% chance
          // Base2 is the ID of the auto cast spell
          autoCastedSpells.add(session.getSpell((int)sd.getBase2(i)));
        }
      }
    }

    return autoCastedSpells;
  }

  public List<SpellAndChance> getAutoCastedSpellsAndChances() {
    List<SpellAndChance> autoCastedSpells = new ArrayList<>();

    for(int i = 0; i < 12; i++) {
      if(sd.getAttrib(i) == Attribute.AUTO_CAST_ANY.getAttribCode() || sd.getAttrib(i) == Attribute.AUTO_CAST_ONE.getAttribCode()) {
        if(sd.getBase2(i) > 0) {
          autoCastedSpells.add(new SpellAndChance(session.getSpell((int)sd.getBase2(i)), sd.getBase(i) / 100));
        }
      }
    }

    return autoCastedSpells;
  }

  public EffectDescriptor getEffectDescriptor(Attribute spellEffect) {
    return getRawSpellData().getEffect(spellEffect);
  }

  private static final Pattern BASE_SPELL_NAME = Pattern.compile("(.*?)( +Rk\\. *(II|III))?");

  /**
   * Returns the spell name, without rank.
   *
   * @param name a spell name
   * @return the spell name, without rank
   */
  public static String baseSpellName(String name) {
    Matcher matcher = BASE_SPELL_NAME.matcher(name);

    if(matcher.matches()) {
      return matcher.group(1);
    }

    return name;
  }
}
