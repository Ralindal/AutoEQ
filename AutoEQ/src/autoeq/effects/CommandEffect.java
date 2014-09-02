package autoeq.effects;

import autoeq.eq.EverquestSession;
import autoeq.eq.Spell;
import autoeq.eq.TargetType;

public class CommandEffect extends AbstractEffect {
  private final String command;
  private final double range;
  private final TargetType targetType;

  public CommandEffect(EverquestSession session, String command, double range, long lockOutMillis, TargetType targetType) {
    super(session, lockOutMillis);

    this.command = command;
    this.range = range;
    this.targetType = targetType;
  }

  @Override
  public int getCastTime() {
    return 0;
  }

  @Override
  public Spell getSpell() {
    return null;
  }

  @Override
  public Type getType() {
    return Type.COMMAND;
  }

  @Override
  public int getAgro() {
    return 0;
  }

  @Override
  public void internalActivate() {
    getSession().doCommand("/nomodkey " + command);
  }

  @Override
  public boolean internalIsReady() {
    return true;
  }

  @Override
  public String toString() {
    return command;
  }

  @Override
  public boolean willUseGOM() {
    return false;
  }

  @Override
  public boolean requiresStanding() {
    return false;
  }

  @Override
  public TargetType getTargetType() {
    return targetType;
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
    return getClass().getName() + ":" + command;
  }

  @Override
  public long getReadyMillis() {
    return 0;
  }
}
