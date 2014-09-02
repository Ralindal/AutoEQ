package autoeq.eq;

import autoeq.SpellData;

public class MySpell {
  private final SpellData spellData;
  private final int level;
  private final int duration;
  private final String targetType;
  private final String spellType;
  private final int myCastMillis;

  public MySpell(SpellData spellData, int level, int duration, String targetType, String spellType, int myCastMillis) {
    this.spellData = spellData;
    this.level = level;
    this.duration = duration;
    this.targetType = targetType;
    this.spellType = spellType;
    this.myCastMillis = myCastMillis;
  }

  public int getLevel() {
    return level;
  }

  public int getDuration() {
    return duration;
  }

  public String getTargetType() {
    return targetType;
  }

  public String getSpellType() {
    return spellType;
  }

  public int getMyCastMillis() {
    return myCastMillis;
  }
}
