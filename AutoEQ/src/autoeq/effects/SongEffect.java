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

  @Override
  public Spell getSpell() {
    return spell;
  }

  @Override
  public int getCastTime() {
    return spell.getCastTime();
  }

  @Override
  public Type getType() {
    return Type.SONG;
  }

  @Override
  public int getAgro() {
    return agro;
  }

  @Override
  public String getCastingLine() {
    return "/cast " + session.getMe().getGem(spell);
  }

  @Override
  public boolean isReady() {
    return session.getMe().isSpellReady(spell);
  }

  @Override
  public String toString() {
    return spell.getName();
  }

  @Override
  public boolean willUseGOM() {
    return false;
  }
}
