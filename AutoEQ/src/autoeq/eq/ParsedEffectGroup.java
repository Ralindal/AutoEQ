package autoeq.eq;

import java.util.ArrayList;
import java.util.List;

public class ParsedEffectGroup {
  private final List<String> gems;
  private final String validTargets;
  private final List<String> profiles;
  private final List<String> conditions;
  private final List<String> priorityAdjusts;
  private final String priorityExpr;
  private final TargetCategory targetCategory;
  private final int minimumTargets;
  private final List<String> postActions;
  private final boolean announce;
  private final String announceChannelPrefix;
  private final double rangeExtensionFactor;
  private final double durationExtensionFactor;
  private final long additionalDurationExtension;
  private final String gemSumExpression;

  private final ExpiringValue<Boolean> isProfileActive = new ExpiringValue<>(5000);

  public ParsedEffectGroup(List<String> gems, String validTargets, List<String> profiles, List<String> conditions, List<String> priorityAdjusts, String priorityExpr, TargetCategory targetCategory, int minimumTargets, List<String> postActions, boolean announce, String announceChannelPrefix,
      double rangeExtensionFactor, double durationExtensionFactor, long additionalDurationExtension, String gemSumExpression) {
    this.gems = gems;
    this.validTargets = validTargets;
    this.profiles = profiles;
    this.conditions = conditions;
    this.priorityAdjusts = priorityAdjusts;
    this.priorityExpr = priorityExpr;
    this.targetCategory = targetCategory;
    this.minimumTargets = minimumTargets;
    this.postActions = postActions;
    this.announce = announce;
    this.announceChannelPrefix = announceChannelPrefix;
    this.rangeExtensionFactor = rangeExtensionFactor;
    this.durationExtensionFactor = durationExtensionFactor;
    this.additionalDurationExtension = additionalDurationExtension;
    this.gemSumExpression = gemSumExpression;
  }

  public boolean isProfileActive(EverquestSession session) {
    if(isProfileActive.isExpired()) {
      isProfileActive.setValue(session.isProfileActive(getProfiles()));
    }

    return isProfileActive.getValue();
  }

  public String getValidTargets() {
    return validTargets;
  }

  public List<String> getProfiles() {
    return new ArrayList<>(profiles);
  }

  public String getPriorityExpr() {
    return priorityExpr;
  }

  public int getMinimumTargets() {
    return minimumTargets;
  }

  public List<String> getPostActions() {
    return postActions;
  }

  public List<String> getPriorityAdjusts() {
    return priorityAdjusts;
  }

  public TargetCategory getTargetCategory() {
    return targetCategory;
  }

  private boolean enabled = true;

  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
  }

  public List<String> getConditions() {
    return conditions;
  }

  public boolean isAnnounce() {
    return announce;
  }

  public String getAnnounceChannelPrefix() {
    return announceChannelPrefix;
  }

  public double getRangeExtensionFactor() {
    return rangeExtensionFactor;
  }

  public double getDurationExtensionFactor() {
    return durationExtensionFactor;
  }

  public long getAdditionalDurationExtension() {
    return additionalDurationExtension;
  }

  public String getGemSumExpression() {
    return gemSumExpression;
  }

  public List<String> getGems() {
    return new ArrayList<>(gems);
  }
}
