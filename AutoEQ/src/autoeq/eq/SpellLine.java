package autoeq.eq;

import java.util.List;

import autoeq.effects.Effect;

public interface SpellLine {
  public boolean isEnabled();
  public void setEnabled(boolean enabled);
  public Effect getEffect();
  public boolean isAnnounce();
  public String getAnnounceChannelPrefix();
  public List<String> getPostActions();
  public double getRangeExtensionFactor();
  public double getDurationExtensionFactor();
  public String getName();
}
