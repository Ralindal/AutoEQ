package autoeq.eq;

public class Gem {
  private final Spell spell;
  private final int slot;
  private final int priority;

  public Gem(Spell spell, int slot, int priority) {
    this.spell = spell;
    this.slot = slot;
    this.priority = priority;
  }

  public Spell getSpell() {
    return spell;
  }

  public int getSlot() {
    return slot;
  }

  public int getPriority() {
    return priority;
  }

  @Override
  public String toString() {
    return spell + " in " + slot;
  }
}