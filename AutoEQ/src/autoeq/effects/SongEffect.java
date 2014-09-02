package autoeq.effects;

import autoeq.eq.EverquestSession;
import autoeq.eq.Spell;

public class SongEffect extends AbstractSpellBasedEffect {
  private final int agro;

  public SongEffect(EverquestSession session, Spell spell, int agro, long lockOutMillis) {
    super(session, spell, lockOutMillis);

    this.agro = agro;
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
  public void internalActivate() {
    getSession().doCommand("/cast " + getSession().getMe().getGem(getSpell()));
  }

  @Override
  public boolean internalIsReady() {
    if(!getSession().getMe().isSpellReady(getSpell())) {
      return false;
    }

    return super.internalIsReady();
  }

  @Override
  public long getReadyMillis() {
    return getSession().getMe().getGemReadyMillis(getSession().getMe().getGem(getSpell()));
  }

  @Override
  public String toString() {
    return getSpell().getName();
  }

  @Override
  public boolean willUseGOM() {
    return false;
  }

  @Override
  public boolean requiresStanding() {
    return true;
  }

  @Override
  protected boolean usesSpellCasting() {
    return true;
  }

  @Override
  protected boolean isUnaffectedBySilence() {
    return false;
  }
}
