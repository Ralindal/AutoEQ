package autoeq.eq;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import autoeq.ExpressionEvaluator;
import autoeq.TargetPattern;
import autoeq.effects.Effect;
import autoeq.modules.buff.EffectSet;

public class ParsedEffectLine implements SpellLine, Iterable<EffectSet> {
  private final ParsedEffectGroup group;
  private final String name;
  private final int gem;
  private final List<EffectSet> effectSets;

  public ParsedEffectLine(ParsedEffectGroup group, String name, int gem, List<EffectSet> effectSets) {
    this.group = group;
    this.name = name;
    this.gem = gem;
    this.effectSets = new ArrayList<>(effectSets);
  }

  public boolean isProfileActive(EverquestSession session) {
    return group.isProfileActive(session);
  }

  public ParsedEffectGroup getGroup() {
    return group;
  }

  public String getName() {
    return name;
  }

  public int getGem() {
    return gem;
  }

  public List<EffectSet> getEffectSets() {
    return Collections.unmodifiableList(effectSets);
  }

  public String getValidTargets() {
    return group.getValidTargets();
  }

  public List<String> getProfiles() {
    return group.getProfiles();
  }

  public String getPriorityExpr() {
    return group.getPriorityExpr();
  }

  public int getMinimumTargets() {
    return group.getMinimumTargets();
  }

  @Override
  public List<String> getPostActions() {
    return group.getPostActions();
  }

  @Override
  public Effect getEffect() {
    for(EffectSet effectSet : effectSets) {
      if(effectSet.isUsable()) {
        return effectSets.get(0).getSingleOrGroup();
      }
    }

    return null;
  }

  @Override
  public boolean isEnabled() {
    return group.isEnabled();
  }

  @Override
  public void setEnabled(boolean enabled) {
    group.setEnabled(enabled);
  }

  public List<String> getPriorityAdjusts() {
    return group.getPriorityAdjusts();
  }

  public TargetCategory getTargetCategory() {
    return group.getTargetCategory();
  }

  public double determinePriority(Effect effect, Spawn target, Spawn mainTarget, Spawn mainAssist) {
    int basePriority;

    if(name.startsWith("Heal.")) {
      basePriority = 100;
    }
    else if(name.startsWith("Debuff.")) {
      basePriority = 200;
    }
    else if(name.startsWith("Buff.")) {
      basePriority = 300;
    }
    else {
      throw new RuntimeException("Unsupported Section for determining Priority: " + name);
    }

    double priority = Priority.decodePriority(target.getSession(), target, effect, getPriorityExpr(), basePriority);

    priority += ExpressionEvaluator.sum(getPriorityAdjusts(), new ExpressionRoot(target.getSession(), target, mainTarget, mainAssist, effect), this);

    return priority;
  }

  /**
   * Checks if target is a valid target.
   */
  public boolean matchesConditions(Spawn target, Spawn mainTarget, Spawn mainAssist, Effect effect) {
    return getFirstNonMatchingCondition(target, mainTarget, mainAssist, effect) == null;
  }

  public String getFirstNonMatchingCondition(Spawn target, Spawn mainTarget, Spawn mainAssist, Effect effect) {
    if(getValidTargets() != null && !TargetPattern.isValidTarget(getValidTargets(), target)) {
      return "TargetClassMismatch";
    }

    if(!getTargetCategory().matches(target, mainTarget)) {
      return "TargetTypeMismatch";
    }

    return ExpressionEvaluator.evaluateAndReturnFailingCondition(group.getConditions(), new ExpressionRoot(target.getSession(), target, mainTarget, mainAssist, effect), this);
  }

  /**
   * Checks if target is a valid target.
   */
  public boolean isValidTarget(Spawn target) {
    boolean valid = getValidTargets() != null ? TargetPattern.isValidTarget(getValidTargets(), target) : true;

    EffectSet effectSet = effectSets.get(0);
    TargetType targetType = effectSet.getSingleOrGroup().getTargetType();

    valid = valid && ((targetType == TargetType.CORPSE) == !target.isAlive());
    valid = valid && ExpressionEvaluator.evaluate(group.getConditions(), new ExpressionRoot(target.getSession(), target, null, null, effectSet.getSingleOrGroup()), this);

    return valid;
  }

  @Override
  public Iterator<EffectSet> iterator() {
    return effectSets.iterator();
  }

  @Override
  public boolean isAnnounce() {
    return group.isAnnounce();
  }

  @Override
  public String getAnnounceChannelPrefix() {
    return group.getAnnounceChannelPrefix();
  }

  @Override
  public double getRangeExtensionFactor() {
    return group.getRangeExtensionFactor();
  }

  @Override
  public double getDurationExtensionFactor() {
    return group.getDurationExtensionFactor();
  }

  public long getAdditionalDurationExtension() {
    return group.getAdditionalDurationExtension();
  }

  @Override
  public String toString() {
    return name;
  }
}
