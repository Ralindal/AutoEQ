package autoeq.effects;

import autoeq.eq.Spell;

public interface Effect {

  public enum Type {
    SPELL("Casting"), ITEM("Clicking"), DISCIPLINE("Using"), ABILITY("Activating"), SONG("Singing");

    private final String verb;

    private Type(String verb) {
      this.verb = verb;
    }

    public String getVerb() {
      return verb;
    }
  }

  public Type getType();
  public Spell getSpell();
  public boolean isReady();
  public boolean willUseGOM();
  public String getCastingLine();
  public int getAgro();

  /**
   * Cast time in milliseconds.  It's possible that an item has a cast time, while the actual spell
   * does not.  In that case getCastTime will reflect the item's cast time.
   */
  public int getCastTime();

}
