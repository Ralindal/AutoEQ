package autoeq.effects;

import autoeq.eq.Spell;
import autoeq.eq.TargetType;

public interface Effect {

  public enum Type {
    SPELL("Casting"), ITEM("Clicking"), DISCIPLINE("Using"), ABILITY("Activating"), SONG("Singing"), COMMAND("Doing"), MELEE_ABILITY("Using");

    private final String verb;

    private Type(String verb) {
      this.verb = verb;
    }

    public String getVerb() {
      return verb;
    }
  }

  public String getId();
  public Type getType();
  public Spell getSpell();
  public boolean isDetrimental();
  public boolean isReady();
  public long getReadyMillis();
  public boolean isUsable();    // Spells may become unusable when delevelled; AA abilities (like Glyphs) can disappear after use; Items may disappear after use (mod rods)
  public boolean willUseGOM();
  public void activate();
  public int getAgro();
  public boolean requiresStanding();
  public TargetType getTargetType();
  public double getRange();
  public boolean isSameAs(Effect otherEffect);
  public void setUsable(boolean usable);

  /**
   * Cast time in milliseconds.  It's possible that an item has a cast time, while the actual spell
   * does not.  In that case getCastTime will reflect the item's cast time.
   */
  public int getCastTime();
}
