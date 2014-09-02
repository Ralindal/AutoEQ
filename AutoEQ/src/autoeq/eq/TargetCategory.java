package autoeq.eq;

public enum TargetCategory {
  MAIN, EXTENDED, ALL;

  public boolean matches(Spawn target, Spawn mainTarget) {
    if(this == MAIN) {
      return target.equals(mainTarget);
    }
    else if(this == EXTENDED) {
      return target.isExtendedTarget();
    }

    return true;
  }
}
