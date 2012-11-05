package autoeq.modules.pull;

import java.util.List;

import autoeq.eq.Condition;
import autoeq.eq.EverquestSession;
import autoeq.eq.HistoryValue;
import autoeq.eq.Location;
import autoeq.eq.Me;
import autoeq.eq.Timer;

public class MoveUtils2 {
  private static final int THRESHOLD = 5;

  public static void moveBackwardsTo(EverquestSession session, float x, float y) {
    moveTo(session, x, y, THRESHOLD, false, false, null);
  }

  public static void moveTo(EverquestSession session, float x, float y) {
    moveTo(session, x, y, THRESHOLD, true, false, null);
  }

  public static void moveTo(EverquestSession session, float x, float y, Condition earlyExit) {
    moveTo(session, x, y, THRESHOLD, true, false, earlyExit);
  }

  public static void moveTowards(EverquestSession session, float x, float y) {
    moveTo(session, x, y, THRESHOLD, true, true, null);
  }

  public static void moveTowards(EverquestSession session, float x, float y, Condition earlyExit) {
    moveTo(session, x, y, THRESHOLD, true, true, earlyExit);
  }

  public static void followPath(EverquestSession session, List<Location> locations) {
    followPath(session, locations, null);
  }

  public static void followPath(EverquestSession session, List<Location> locations, Condition earlyExit) {
    Me me = session.getMe();

    for(int i = 0; i < locations.size(); i++) {
      Location location = locations.get(i);
      Location nextLocation = i + 1 < locations.size() ? locations.get(i + 1) : null;
      int exitAccuracy = THRESHOLD;

      if(nextLocation != null) {
        double currentAngle = Math.toDegrees(Math.atan2(location.y - me.getY(), location.x - me.getX()));
        double nextAngle = Math.toDegrees(Math.atan2(nextLocation.y - location.y, nextLocation.x - location.x));
        double angleDiff = angleDiff(currentAngle, nextAngle);

        if(angleDiff < 15) {
          exitAccuracy = THRESHOLD * 2;
        }
      }

      if(moveTo(session, location.x, location.y, exitAccuracy, true, true, earlyExit)) {
        break;
      }
    }

    stop(session);
  }

  public static boolean moveTo(EverquestSession session, float x, float y, int exitAccuracy, boolean forwards, boolean noStop, Condition earlyExit) {
    final Me me = session.getMe();
    float startX = me.getX();
    float startY = me.getY();

    session.log(String.format("MOVE: from %.2f,%.2f to %.2f,%.2f; noStop = " + noStop + "; forwards = " + forwards, startY, startX, y, x));

    HistoryValue<Double> distanceHistory = new HistoryValue<>(20000);
    boolean moveSent = false;
    Timer moveTimer = new Timer(1500);
    boolean exitingEarly = false;

    for(;;) {
      double distance = me.getDistance(x, y);

      distanceHistory.add(distance);

      if(distance <= exitAccuracy) {
        break;
      }
      if(earlyExit != null && earlyExit.isValid()) {
        exitingEarly = true;
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
        session.doCommand(String.format("/moveto updateloc %.2f %.2f mdist %d", y, x, moveAccuracy));
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

    return exitingEarly;
  }

  public static void stop(EverquestSession session) {
    session.doCommand("/moveto off");
    session.doCommand("/nomodkey /keypress back");
  }

  private static double angleDiff(double a, double b) {
    double diff = Math.max(a, b) - Math.min(a, b);

    return diff > 180 ? 360 - diff : diff;
  }
}
