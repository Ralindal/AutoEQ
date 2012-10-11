package autoeq.eq;

import autoeq.effects.Effect;


public class ExpressionRoot {
  private final Spawn target;
  private final Spawn mainTarget;
  private final EverquestSession session;
  private final Effect effect;

  public ExpressionRoot(EverquestSession session, Spawn target, Spawn mainTarget, Effect effect) {
    this.session = session;
    this.target = target;
    this.mainTarget = mainTarget;
    this.effect = effect;
  }
  
  public EverquestSession session() {
    return session;
  }
    
  public Me me() {
    return session.getMe();
  }
  
  public Spawn target() {
    return target;
  }
  
  public Spawn mainTarget() {
    return mainTarget;
  }

  public Effect effect() {
    return effect;
  }
  
  @Override
  public String toString() {
    return "Top Level Objects";
  }
}