package autoeq.effects;

import autoeq.eq.EffectType;
import autoeq.eq.EverquestSession;
import autoeq.eq.Spell;
import autoeq.eq.TargetType;

public abstract class AbstractSpellBasedEffect extends AbstractEffect {
  private final Spell spell;

  public AbstractSpellBasedEffect(EverquestSession session, Spell spell, long lockOutMillis) {
    super(session, lockOutMillis);

    this.spell = spell;
  }

  @Override
  protected boolean internalIsReady() {
    if(!isUnaffectedBySilence() && getSession().getMe().getActiveEffect(EffectType.SILENCE) != null) {
      return false;
    }
    if(usesSpellCasting() && getSession().getMe().isCasting()) {
      return false;
    }
    if(getSession().getMe().getMana() < spell.getMana() || getSession().getMe().getManaHistory().getValue(3000) < spell.getMana()) {
      return false;
    }

    return getReadyMillis() == 0;
  }

  @Override
  public final Spell getSpell() {
    return spell;
  }

  @Override
  public boolean isDetrimental() {
    return spell.isDetrimental();
  }

  @Override
  public int getCastTime() {
    return spell.getCastTime();
  }

  @Override
  public TargetType getTargetType() {
    return spell.getTargetType();
  }

  @Override
  public double getRange() {
    return spell.getRange();
  }

  @Override
  public boolean internalIsUsable() {
    return getSession().getMe().getLevel() >= getSpell().getLevel();
  }

  @Override
  public String getId() {
    return getClass().getName() + ":" + spell.getName();
  }

  protected abstract boolean isUnaffectedBySilence();
  protected abstract boolean usesSpellCasting();  // determines whether it locks out other spell casting abilities
}
