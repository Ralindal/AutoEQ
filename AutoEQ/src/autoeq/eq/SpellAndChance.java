package autoeq.eq;

public class SpellAndChance {
  private final Spell spell;
  private final float chance;

  public SpellAndChance(Spell spell, float chance) {
    this.spell = spell;
    this.chance = chance;
  }

  public Spell getSpell() {
    return spell;
  }

  public float getChance() {
    return chance;
  }
}
