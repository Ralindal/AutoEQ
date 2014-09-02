package autoeq.modules.buff;

import autoeq.effects.Effect;

public class EffectSet {
  private final Effect single;
  private final Effect group;

  public EffectSet(Effect single, Effect group) {
    this.single = single;
    this.group = group;
  }

  public Effect getSingle() {
    return single;
  }

  public Effect getGroup() {
    return group;
  }

  public Effect[] getEffects() {
    if(single != null && group != null) {
      return new Effect[] {group, single};
    }
    else if(single != null) {
      return new Effect[] {single};
    }
    else {
      return new Effect[] {group};
    }
  }

  public Effect getSingleOrGroup() {
    return single != null ? single : group;
  }

  public Effect getGroupOrSingle() {
    return group != null ? group : single;
  }

  public boolean isUsable() {
    return (single != null && single.isUsable()) || (group != null && group.isUsable());
  }

  @Override
  public String toString() {
    return "EffectSet(S: " + single + " G: " + group + ")";
  }
}
