package autoeq.effects;

import autoeq.eq.EverquestSession;
import autoeq.eq.Spell;

public class SongEffect implements Effect {
  private final EverquestSession session;
  private final Spell spell;
  private final int agro;
  
  public SongEffect(EverquestSession session, Spell spell, int agro) {
    this.session = session;
    this.spell = spell;
    this.agro = agro;
  }
  
  public Spell getSpell() {
    return spell;
  }

  public Type getType() {
    return Type.SONG;
  }

  public int getAgro() {
    return agro;
  }

  public String getCastingLine() {
    return "/cast " + session.getMe().getGem(spell);
  }

  public boolean isReady() {
    return session.getMe().isSpellReady(spell);
  }
  
  @Override
  public String toString() {
    return spell.getName();
  }

  public boolean willUseGOM() {
    return false;
  }
}
