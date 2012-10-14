package autoeq.effects;

import java.util.HashMap;
import java.util.Map;

import autoeq.eq.EverquestSession;
import autoeq.eq.Spell;


public class SpellEffect implements Effect {
  private static final Map<String, Integer> MAX_GOM_LEVEL = new HashMap<>();

  static {
    MAX_GOM_LEVEL.put("Gift of Mana", 70);
    MAX_GOM_LEVEL.put("Gift of Radiant Mana", 75);
    MAX_GOM_LEVEL.put("Gift of Exquisite Radiant Mana", 80);
    MAX_GOM_LEVEL.put("Gift of Amazing Exquisite Radiant Mana", 85);
  }

  private final EverquestSession session;
  private final Spell spell;
  private final int agro;

  public SpellEffect(EverquestSession session, Spell spell, int agro) {
    this.session = session;
    this.spell = spell;
    this.agro = agro;
  }

  @Override
  public Spell getSpell() {
    return spell;
  }

  @Override
  public Type getType() {
    return Type.SPELL;
  }

  @Override
  public int getAgro() {
    return agro;
  }

  @Override
  public String getCastingLine() {
    return "/casting " + spell.getId() + " gem" + session.getMe().getGem(spell) + " -maxtries|1";
  }

  @Override
  public boolean isReady() {
    return session.getMe().isSpellReady(spell);
  }

  @Override
  public boolean willUseGOM() {
    for(String name : session.getMe().getBuffNames()) {
      Integer maxLevel = MAX_GOM_LEVEL.get(name);

      if(maxLevel != null) {
        return spell.getLevel() <= maxLevel;
      }
    }

    return false;
  }

  @Override
  public String toString() {
    return spell.getName();
  }
}
