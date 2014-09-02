package autoeq.modules.pull;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import autoeq.eq.Command;
import autoeq.eq.DummyCommand;
import autoeq.eq.EverquestSession;
import autoeq.eq.HistoricValue;
import autoeq.eq.Location;
import autoeq.eq.Me;
import autoeq.eq.Module;
import autoeq.eq.ResourceProvider;
import autoeq.eq.Timer;
import autoeq.eq.UserCommand;

public class MoveModule implements Module, ResourceProvider<MoveModule.Mover> {
  private static final int THRESHOLD = 10;
  private static final int WARP_THRESHOLD = 150;
  private static final long WARP_WAIT = 2000;
  private static final List<Command> DUMMY_COMMAND = new ArrayList<>();

  static {
    DUMMY_COMMAND.add(new DummyCommand(-100, "MOVE"));
  }

  private enum State {IDLE, MOVING}

  private final EverquestSession session;
  private final List<Location> path = new LinkedList<>();

  private Mover currentMover;

  private float lastX;
  private float lastY;
  private State state = State.IDLE;
  private long warpTime;
  private boolean movingDown;

  private Timer standTimer = new Timer(300);
  private Timer faceTimer = new Timer(100);

  private HistoricValue<Location> historicLocations = new HistoricValue<>(10000, 100);

  public MoveModule(EverquestSession session) {
    this.session = session;

    session.registerResourceProvider(Mover.class, this);
    session.addUserCommand("move", Pattern.compile("loc ([0-9\\.]+,[0-9\\.]+)"), "loc <x>,<y>", new UserCommand() {
      @Override
      public void onCommand(Matcher matcher) {
        String[] coords = matcher.group(1).split(",");
        float locX = Float.parseFloat(coords[1]);
        float locY = Float.parseFloat(coords[0]);

        moveTo(locX, locY, null);
      }
    });
  }

  @Override
  public List<Command> pulse() {
    handleMovement();
    lastX = session.getMe().getX();
    lastY = session.getMe().getY();

    if(state == State.MOVING || path.size() > 0) {
      return DUMMY_COMMAND;
    }

    return null;
  }

  public void handleMovement() {
    if(state == State.MOVING || path.size() > 0) {
      Me me = session.getMe();

      if(!me.isStanding()) {
        if(standTimer.isExpired()) {
          session.doCommand("/stand");
          standTimer.reset();
        }
        return;
      }

      // Check if arrived
      while(path.size() > 0 && me.getDistance(path.get(0).x, path.get(0).y) < THRESHOLD) {
        path.remove(0);
      }

      double distanceTravelled = me.getDistance(lastX, lastY);

      if(distanceTravelled > WARP_THRESHOLD) {
        session.echo("MOVE: Warped!");

        // Warping occured, remove all waypoints until we find one near our current location (if any)
        while(path.size() > 0 && me.getDistance(path.get(0).x, path.get(0).y) > THRESHOLD * 2) {
          path.remove(0);
        }
      }

      if(path.size() == 0) {
        state = State.IDLE;
        session.doCommand("/nomodkey /keypress forward");
        session.doCommand("/nomodkey /keypress end");
        movingDown = false;
        return;
      }

      Location location = path.get(0);

      if(me.getDistance(location.x, location.y) > WARP_THRESHOLD) {
        if(state != State.IDLE) {
          session.echo("MOVE: Warping detected in follow path, holding!");
          state = State.IDLE;
          session.doCommand("/keypress forward");
          warpTime = System.currentTimeMillis();
        }

        if(warpTime + WARP_WAIT < System.currentTimeMillis()) {
          session.echo("MOVE: Warping was not resolved, clearing waypoints!");
          path.clear();
        }

        return;
      }

      float heading = me.getHeading();
      float directHeading = calculateHeading(location.x, location.y);

      System.out.println(me + " current heading = " + heading + "; dh = " + directHeading + "; diff = " + headingDiff(heading, directHeading) + "; ("+ session.getMe().getX() + ", " + session.getMe().getY() + ")");

      // Adjust heading if needed
      if(Math.abs(headingDiff(heading, directHeading)) > 3.0) {
        if(faceTimer.isExpired()) {
          session.doCommand(String.format("/face fast nolook loc %.2f,%.2f", location.getY(), location.getX()));
          faceTimer.reset();
        }

        return;
      }

      if(location.z != null && state == State.MOVING) {
        if(me.getZ() + 2 > location.z && !movingDown) {
          session.doCommand("/nomodkey /keypress end hold");
          movingDown = true;
        }
        else if(movingDown) {
          session.doCommand("/nomodkey /keypress end");
          movingDown = false;
        }
      }

      // Heading in right direction and not arrived yet
      if(state == State.IDLE) {
        historicLocations.reset();
        session.doCommand("/nomodkey /keypress forward hold");
        state = State.MOVING;
      }

      Location currentLocation = new Location(me.getX(), me.getY());

      historicLocations.add(currentLocation);

      // Check distance travelled
      if(historicLocations.getPeriod() > 1000 && currentLocation != historicLocations.getValue(500) && currentLocation.distance(historicLocations.getValue(500)) < 5) {
        // System.err.println("Might be stuck at (" + me.getX() + ", " + me.getY());
        session.doCommand("/nomodkey /keypress forward");
        session.doCommand("/nomodkey /keypress end");
        state = State.IDLE;
        movingDown = false;
      }
    }
  }

  @Override
  public Mover obtainResource() {
    if(currentMover != null) {
      return null;
    }

    currentMover = new Mover();
    return currentMover;
  }

  @Override
  public void releaseResource(Mover resource) {
    if(currentMover != resource) {
      throw new IllegalStateException("Illegal release attempt");
    }

    currentMover = null;
  }

//  public Mover obtainMover(MovementMonitor monitor) {
//    if(currentMover != null) {
//      throw new IllegalStateException("Only one mover may be active at the same time");
//    }
//
//    return new Mover(monitor);
//  }
//
//  public void releaseMover(Mover mover) {
//    if(currentMover != mover) {
//      throw new IllegalStateException("Illegal release attempt");
//    }
//
//    currentMover = null;
//  }

  Location getLastWayPoint() {
    return this.path.isEmpty() ? null : this.path.get(this.path.size() - 1);
  }

  void addWayPoint(float x, float y, float z) {
    this.path.add(new Location(x, y, z));
  }

  void moveTo(float x, float y, Float z) {
    this.path.clear();
    this.path.add(new Location(x, y, z));
  }

  void clear() {
    this.path.clear();
  }

  private float calculateHeading(float x, float y) {
    return (float)(Math.atan2(x - session.getMe().getX(), y - session.getMe().getY()) / Math.PI * 180);
  }

  private static float headingDiff(float h1, float h2) {
    float d = h2 - h1;

    if(d > 180) {
      d -= 360;
    }
    else if(d < -180) {
      d += 360;
    }

    return d;
  }

  public int getPriority() {
    return -50;
  }

  @Override
  public int getBurstCount() {
    return 1;
  }

  public class Mover {
    public Location getLastWayPoint() {
      return MoveModule.this.getLastWayPoint();
    }

    public void addWayPoint(float x, float y, Float z) {
      if(this != currentMover) {
        throw new IllegalStateException("This mover is not the current mover");
      }
      MoveModule.this.addWayPoint(x, y, z);
    }

    public void moveTo(float x, float y, Float z) {
      if(this != currentMover) {
        throw new IllegalStateException("This mover is not the current mover");
      }
      MoveModule.this.moveTo(x, y, z);
    }

    public void clear() {
      if(this != currentMover) {
        throw new IllegalStateException("This mover is not the current mover");
      }
      MoveModule.this.clear();
    }
  }
}
