package autoeq.effects;

import autoeq.eq.EverquestSession;
import autoeq.eq.ExpressionListener;
import autoeq.eq.Spell;
import autoeq.eq.TargetType;

public class AbilityEffect extends AbstractEffect {
  private final String name;
  private final double range;

  private boolean ready;

  public AbilityEffect(EverquestSession session, String name, double range, long lockOutMillis) {
    super(session, lockOutMillis);

    this.name = name;
    this.range = range;

    session.registerExpression("${Me.AbilityReady[" + name + "]}", new ExpressionListener() {
      @Override
      public void stateUpdated(String result) {
        ready = result.equals("TRUE");
      }
    });
  }

  @Override
  public int getCastTime() {
    return 0;
  }

  @Override
  public Type getType() {
    return Type.MELEE_ABILITY;
  }

  @Override
  public int getAgro() {
    return 0;
  }

  @Override
  public void internalActivate() {
    getSession().doCommand("/nomod /doability \"" + getSpell().getName() + "\"");
  }

  @Override
  protected boolean internalIsReady() {
    return ready;
  }

  @Override
  public long getReadyMillis() {
    return ready ? 0 : 5000;
  }

  @Override
  public String toString() {
    return name;
  }

  @Override
  public boolean willUseGOM() {
    return false;
  }

  @Override
  public boolean requiresStanding() {
    return true;
  }

  @Override
  public Spell getSpell() {
    return null;
  }

  @Override
  public TargetType getTargetType() {
    return TargetType.SELF;
  }

  @Override
  public boolean isDetrimental() {
    return false;
  }

  @Override
  public double getRange() {
    return range;
  }

  @Override
  public boolean internalIsUsable() {
    return true;
  }

  @Override
  public String getId() {
    return getClass().getName() + ":" + name;
  }
}
