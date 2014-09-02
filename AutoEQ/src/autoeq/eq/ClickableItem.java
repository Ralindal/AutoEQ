package autoeq.eq;

public class ClickableItem {
  private final int id;
  private final int castTime;
  private final int timer;
  private final int spellId;

  public ClickableItem(int id, int spellId, int castTime, int timer) {
    this.id = id;
    this.spellId = spellId;
    this.castTime = castTime;
    this.timer = timer;
  }

  public int getId() {
    return id;
  }

  public int getSpellId() {
    return spellId;
  }

  /**
   * @return cast time in ms
   */
  public int getCastTime() {
    return castTime;
  }

  /**
   * @return time left in ticks
   */
  public int getTimer() {
    return timer;
  }
}
