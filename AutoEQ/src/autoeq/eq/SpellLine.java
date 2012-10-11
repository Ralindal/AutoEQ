package autoeq.eq;

import autoeq.effects.Effect;

public interface SpellLine {
  public boolean isEnabled();
  public void setEnabled(boolean enabled);
  public double getPriority();
  public Effect getEffect();
}
