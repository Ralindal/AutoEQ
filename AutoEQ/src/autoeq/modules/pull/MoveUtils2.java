package autoeq.modules.pull;

import autoeq.eq.Condition;
import autoeq.eq.EverquestSession;
import autoeq.eq.HistoryValue;
import autoeq.eq.Me;
import autoeq.eq.Timer;

public class MoveUtils2 {
  private static final int THRESHOLD = 5;

  public static void moveBackwardsTo(EverquestSession session, float x, float y) {
    moveTo(session, x, y, false, false, null);
  }

  public static void moveTo(EverquestSession session, float x, float y) {
    moveTo(session, x, y, true, false, null);
  }

  public static void moveTo(EverquestSession session, float x, float y, Condition earlyExit) {
    moveTo(session, x, y, true, false, earlyExit);
  }

  public static void moveTowards(EverquestSession session, float x, float y) {
    moveTo(session, x, y, true, true, null);
  }

  public static void moveTowards(EverquestSession session, float x, float y, Condition earlyExit) {
    moveTo(session, x, y, true, true, earlyExit);
  }

  public static void moveTo(EverquestSession session, float x, float y, boolean forwards, boolean noStop, Condition earlyExit) {
    final Me me = session.getMe();
    float startX = me.getX();
    float startY = me.getY();

    session.log(String.format("MOVE: from %.2f,%.2f to %.2f,%.2f; noStop = " + noStop + "; forwards = " + forwards, startY, startX, y, x));

    HistoryValue<Double> distanceHistory = new HistoryValue<Double>(20000);
    boolean moveSent = false;
    Timer moveTimer = new Timer(1500);

    for(;;) {
      double distance = me.getDistance(x, y);

      distanceHistory.add(distance);

      if(distance <= THRESHOLD || (earlyExit != null && earlyExit.isValid())) {
        break;
      }

      if(moveSent && moveTimer.isExpired() && distanceHistory.getValue(1000) - distance < 3.0) {
        session.log(String.format("MOVE: not moving (%.1f,%.1f), distance %5.2f. Resetting.", me.getX(), me.getY(), distance));
        session.echo(String.format("MOVE: not moving (%.1f,%.1f), distance %5.2f. Resetting.", me.getX(), me.getY(), distance));
        stop(session);
        moveTimer.reset();
        moveSent = false;
      }

      if(!moveSent) {
        int moveAccuracy = distance < THRESHOLD * 2 ? THRESHOLD / 2 : THRESHOLD;
        session.doCommand(String.format("/moveto loc %.2f %.2f mdist %d", y, x, moveAccuracy));
        moveSent = true;
      }

      session.delayUntilUpdate();

      if(!me.isAlive()) {
        stop(session);
        session.echo("MOVE: Aborted.  Dead during move.");
        throw new MoveException("Dead");
      }

      if(distanceHistory.getPeriod() > 12000 && Math.abs(distanceHistory.getValue(5000) - distanceHistory.getMostRecent()) < 10.0) {
        stop(session);
        session.echo("MOVE: Aborted.  Stuck during move.");
        throw new MoveException("Stuck");
      }
    }

    if(!noStop) {
      stop(session);
    }

    //session.log(String.format("MOVE: result %.0f,%.0f", me.getX(), me.getY()));
  }

  public static void stop(EverquestSession session) {
    session.doCommand("/moveto off");
    session.doCommand("/nomodkey /keypress back");
  }
}
