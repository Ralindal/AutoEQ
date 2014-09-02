package autoeq.eq;

public enum TargetType {
  SINGLE(false, true, false),
  BOLT(false, true, false),
  GROUP(true, false, false),
  TARGETED_GROUP(true, true, true),
  PBAE(true, false, false),
  CORPSE(false, true, false),
  TARGETED_AE(true, true, false),
  SELF(false, false, false),
  BEAM(true, false, false);

  private final boolean areaOfEffect;
  private final boolean targeted;
  private final boolean hasDefaultTarget;

  TargetType(boolean areaOfEffect, boolean targeted, boolean hasDefaultTarget) {
    this.areaOfEffect = areaOfEffect;
    this.targeted = targeted;
    this.hasDefaultTarget = hasDefaultTarget;
  }

  public boolean isAreaOfEffect() {
    return areaOfEffect;
  }

  public boolean isTargeted() {
    return targeted;
  }

  public boolean hasDefaultTarget() {
    return hasDefaultTarget;
  }
}
