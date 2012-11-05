package autoeq.eq;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import autoeq.SpellData;



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
  private final String targetType;   // self, group v1, group v2
  private final String spellType;
  private final SpellData sd;
  private final boolean isSlow;
  private final int maxTargetLevel;
  private final boolean isCharm;
  private final boolean isMez;
  private final boolean isHealOverTime;

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

    try {
      this.session = session;
      this.id = sd.getId();
      this.name = sd.getName();
      this.aeRange = sd.getAERange();
      this.range = sd.getRange();
      this.mana = sd.getMana();
      this.castTime = sd.getCastTime();

      this.level = Integer.parseInt(session.translate("${Spell[" + id + "].Level}"));
      this.duration = Integer.parseInt(session.translate("${Spell[" + id + "].Duration.TotalSeconds}"));
      this.targetType = session.translate("${Spell[" + id + "].TargetType}").toLowerCase();
      this.spellType = session.translate("${Spell[" + id + "].SpellType}").toLowerCase();
    }
    catch(NullPointerException e) {
      throw new RuntimeException("Unable to find spell with ID " + id, e);
    }

    this.isMez = sd.hasAttribute(SpellData.ATTRIB_MESMERIZE);
    this.isCharm = sd.hasAttribute(SpellData.ATTRIB_CHARM);
    this.maxTargetLevel = this.isMez   ? sd.getMax(sd.getAttributeIndex(SpellData.ATTRIB_MESMERIZE)) :
                          this.isCharm ? sd.getMax(sd.getAttributeIndex(SpellData.ATTRIB_CHARM)) :
                                         255;

    isHealOverTime = sd.hasAttribute(SpellData.ATTRIB_HEAL_OVER_TIME);
    isSlow = sd.hasAttribute(SpellData.ATTRIB_SLOW);
  }

  public int getLevel() {
    return level;
  }

  /**
   * Cast time in milliseconds.
   */
  public int getCastTime() {
    return castTime;
  }

  public int getDamageOverTime() {
    if(getDuration() > 0 && isDetrimental()) {
      int index = sd.getAttributeIndex(SpellData.ATTRIB_DAMAGE);

      if(index >= 0) {
        return (int)-sd.getBase(index);
      }
    }

    return 0;
  }

  /**
   * Checks if spell would hold on the target.
   */
  public boolean isWithinLevelRestrictions(Spawn target) {
    if(isDetrimental()) {
      if(target.getLevel() > maxTargetLevel) {
        return false;
      }

      return true;
    }
    else {
      if(getDuration() == 0) {
        return true;
      }

      if(target.getLevel() > 60 || getLevel() < 50) {
        return true;
      }
      // For Level 50 - 65 spells there is a special level based rule based on:
      // Assumption: Clarity II(54) holds on level 42
      // Assumption: Aegolism(60) holds on Level 45
      // Assumption: Virtue(62) holds on Level 46
      if(getLevel() < 66 && 50 + (target.getLevel() - 40) * 2 >= getLevel()) {
        return true;
      }

      return false;
    }
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
    if(sd1.isShortBuff() || sd2.isShortBuff()) {
      return true;
    }

    return sd1.stacksWith(sd2);
  }

  /**
   * Checks all spells given to this spell.  If any of the spells given is equivalent to this
   * spell then this method returns <code>true</code>.
   */
  public boolean isEquivalent(Iterable<Spell> spells) {
    Set<String> equivalentSpells = equivalentMap.get(name);

    if(equivalentSpells != null) {
      for(Spell spell : spells) {
        if(equivalentSpells.contains(spell.name)) {
          return true;
        }
      }
    }

    return false;
  }

  public String getName() {
    return name;
  }

  public int getId() {
    return id;
  }

  public double getRange() {
    return aeRange > 0 && range == 0.0 ? aeRange : range;
  }

  // TODO not really needed now is it?
  public double getAERange() {
    return aeRange;
  }

  /**
   * Returns <code>true</code> if this a targetted spell.
   *
   * @return <code>true</code> if this a targetted spell
   */
  public boolean isTargetted() {
    //System.err.println("++isTargetted " + this + "; tt = " + targetType + "; dur = " + duration + "; brdlvl = " + session.getRawSpellData(id).getBrdLevel());
    return targetType.equals("corpse") || !(targetType.equals("self") || (targetType.startsWith("group") && (duration == 0 || session.getMe().isBard())));
  }

  public String getTargetTypeAsString() {
    return targetType;
  }

  public boolean isDetrimental() {
    return spellType.equals("detrimental");
  }

  public TargetType getTargetType() {
    if(targetType.equals("pb ae")) {
      return TargetType.PBAE;
    }
    else if(targetType.equals("corpse")) {
      return TargetType.CORPSE;
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

  private static Map<String, Set<String>> equivalentMap = new HashMap<>();

  static {
    addEquivalent("Balance of Discord", "Turgur's Swarm");
    addEquivalent("Befuddle", "Befuddle Rk. II", "Befuddle Rk. III", "Befuddling Flash", "Befuddling Flash Rk. II", "Befuddling Flash Rk. III");
  }

  private static void addEquivalent(String... spellNames) {
    Set<String> equivalentSpells = new HashSet<>(Arrays.asList(spellNames));

    for(String spellName : equivalentSpells) {
      equivalentMap.put(spellName, equivalentSpells);
    }
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
