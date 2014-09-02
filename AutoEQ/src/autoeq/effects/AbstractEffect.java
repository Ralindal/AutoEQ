package autoeq.effects;

import autoeq.eq.EverquestSession;

public abstract class AbstractEffect implements Effect {
  private final EverquestSession session;
  private final long lockOutMillis;

  private long effectAvailableTime;
  private boolean usable = true;

  public AbstractEffect(EverquestSession session, long lockOutMillis) {
    this.session = session;
    this.lockOutMillis = lockOutMillis;
  }

  protected EverquestSession getSession() {
    return session;
  }

  @Override
  public final void activate() {
    effectAvailableTime = System.currentTimeMillis() + lockOutMillis;
    internalActivate();
  }

  @Override
  public final boolean isReady() {
    if(System.currentTimeMillis() >= effectAvailableTime) {
      return internalIsReady();
    }

    return false;
  }

  @Override
  public boolean isSameAs(Effect otherEffect) {
    if(otherEffect == null) {
      return false;
    }

    return otherEffect.getId().equals(getId());
  }

  @Override
  public final boolean isUsable() {
    return usable && internalIsUsable();
  }

  @Override
  public final void setUsable(boolean usable) {
    this.usable = usable;
  }

  protected abstract boolean internalIsUsable();
  protected abstract void internalActivate();
  protected abstract boolean internalIsReady();
}
