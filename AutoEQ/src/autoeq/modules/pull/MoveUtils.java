package autoeq.modules.pull;

import java.util.Random;

import autoeq.eq.Condition;
import autoeq.eq.EverquestSession;
import autoeq.eq.HistoryValue;
import autoeq.eq.Me;
import autoeq.eq.Timer;


public class MoveUtils {
  private static final double THRESHOLD = 10;
  private static final Random rnd = new Random();

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

    session.log("MOVE: from " + (int)startX + "," + (int)startY + " to " + (int)x + "," + (int)y + "; noStop = " + noStop + "; forwards = " + forwards);

    final String forward = forwards ? "forward" : "back";
    final String back = forwards ? "back" : "forward";

    if(!me.isStanding()) {
      session.doCommand("/stand");
      session.delay(200);
    }

    Timer moveTimer = new Timer(1500);
    HistoryValue<Double> distanceHistory = new HistoryValue<Double>(20000);
    boolean moving = me.isMoving();
    boolean quickExit = false;

    if(moving) {
      session.doCommand("/nomodkey /keypress " + forward + " hold");
    }

    for(;;) {
      if(me.isSitting()) {
        session.echo("MOVE: Aborted.  Sitting during move.");
        session.doCommand("/nomodkey /keypress " + back);
        throw new MoveException("Sitting");
      }
      if(!me.isAlive()) {
        session.echo("MOVE: Aborted.  Dead during move.");
        throw new MoveException("Dead");
      }
      if(distanceHistory.getPeriod() > 12000 && Math.abs(distanceHistory.getValue(5000) - distanceHistory.getMostRecent()) < 10.0) {
        session.echo("MOVE: Aborted.  Stuck during move.");
        throw new MoveException("Stuck");
      }

      double distance = me.getDistance(x, y);

      if(distance <= THRESHOLD) {
        break;
      }
      if(earlyExit != null && earlyExit.isValid()) {
        quickExit = true;
        break;
      }

      if(moving && moveTimer.isExpired() && distanceHistory.getValue(1000) - distance < 3.0) {
        session.log("Distance to move target unchanged, might be stuck (" + (distanceHistory.getValue(500) - distance) + ").  Resetting.");
        session.doCommand("/nomodkey /keypress " + back);
        session.delay(200);

        String cmd = "/nomodkey /keypress strafe_" + (rnd.nextBoolean() ? "left" : "right");

        session.doCommand(cmd + " hold");
        session.delay(500);
        session.doCommand(cmd);
        session.delayUntilUpdate();  // Added so session values can be updated
        session.log("Loc " + (int)me.getX() + "," + (int)me.getY() + "; heading = " + me.getHeading() + "; required heading = " + calculateHeading(session, x, y));

        moveTimer.reset();
        moving = false;
      }

      distanceHistory.add(distance);

//      if(distanceHistory.getPeriod() > 500) {
//        System.out.println("pps : " + (distanceHistory.getMostRecent() - distanceHistory.getValue(100000)) / distanceHistory.getPeriod());
//      }

//      float heading = me.getHeading();
//      float directHeading = calculateHeading(session, x, y);
//      float maxDiff = (float)Math.toDegrees(Math.atan(0.5 * THRESHOLD / distance));

//      if(!forwards) {  // Adjust heading when moving backwards.
//        directHeading += 180;
//        if(directHeading > 180) {
//          directHeading -= 360;
//        }
//      }

//      System.out.printf("Max Diff %6.1f (%6.1f) ; heading %6.1f ; directHeading %6.1f ; distance %6.1f\n", maxDiff, Math.abs(headingDiff(heading, directHeading)), heading, directHeading, distance);


      //
      if(moving) {
        double distanceBetweenPoints = Math.sqrt((startX - x) * (startX - x) + (startY - y) * (startY - y));
//        session.doCommand(String.format("/docommand ${If[%.2f < ${Math.Distance[%.2f,%.2f]},/keypress back,/face fast nolook " + (forwards ? "" : "away ") + "loc %.2f,%.2f]}", distanceBetweenPoints, startY, startX, y, x));
        session.doCommand(String.format("/if (${Math.Distance[%.2f,%.2f]} > 10 && %.2f > ${Math.Distance[%.2f,%.2f]}) /face fast nolook " + (forwards ? "" : "away ") + "loc %.2f,%.2f", y, x, distanceBetweenPoints, startY, startX, y, x));
//        session.doCommand(String.format("/if (${Math.Distance[%.2f,%.2f]} > 10) /face fast nolook " + (forwards ? "" : "away ") + "loc %.2f,%.2f", y, x, y, x));
      }
      else {
        session.doCommand(String.format("/face fast nolook " + (forwards ? "" : "away ") + "loc %.2f,%.2f", y, x));
      }
      session.delayUntilUpdate();

//      // Adjust heading if needed
//      if(Math.abs(headingDiff(heading, directHeading)) > 5.0) {
//        session.doCommand(String.format("/face fast nolook " + (forwards ? "" : "away ") + "loc %.2f,%.2f", y, x));
//        if(!moving) {
//          final float dh = directHeading;
//
//          session.delay(1000, new Condition() {
//            @Override
//            public boolean isValid() {
//              return Math.abs(headingDiff(me.getHeading(), dh)) <= 3.0;
//            }
//          });
//        }
//        else {
//          session.delay(25);
//        }
//      }
//      else
      if(!moving) {
        session.doCommand("/nomodkey /keypress " + forward + " hold");
        moving = true;
        moveTimer.reset();
      }

      session.delay(25);
    }

    if(noStop) {
      return;
    }

    // stopping, even if we did not move should not move us as the /keypress is super fast
    session.doCommand("/nomodkey /keypress " + back);

    if(quickExit) {
      return;
    }

    session.delay(200);

    String style = me.getDistance(startX, startY) < Math.sqrt((startX - x) * (startX - x) + (startY - y) * (startY - y)) ? "undershot" : "overshot";

    session.getLogger().info(String.format("Desired move was (%7.2f, %7.2f)->(%7.2f, %7.2f).  Current is (%7.2f, %7.2f).  Error-distance: %7.2f (%s)", startX, startY, x, y, me.getX(), me.getY(), me.getDistance(x, y), style));
    //System.err.println(String.format("Desired move was (%7.2f, %7.2f)->(%7.2f, %7.2f).  Current is (%7.2f, %7.2f).  Error-distance: %7.2f (%s)", startX, startY, x, y, me.getX(), me.getY(), me.getDistance(x, y), style));
  }

  public static void stop(EverquestSession session) {
    session.doCommand("/nomodkey /keypress back");
  }

  private static float calculateHeading(EverquestSession session, float x, float y) {
    return (float)(Math.atan2(x - session.getMe().getX(), y - session.getMe().getY()) / Math.PI * 180);
  }

//  private static float headingDiff(float h1, float h2) {
//    float d = h2 - h1;
//
//    if(d > 180) {
//      d -= 360;
//    }
//    else if(d < -180) {
//      d += 360;
//    }
//
//    return d;
//  }
}
