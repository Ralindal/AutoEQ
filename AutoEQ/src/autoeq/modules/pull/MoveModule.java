package autoeq.modules.pull;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import autoeq.eq.Command;
import autoeq.eq.DummyCommand;
import autoeq.eq.EverquestSession;
import autoeq.eq.Location;
import autoeq.eq.Me;
import autoeq.eq.Module;
import autoeq.eq.ResourceProvider;
import autoeq.eq.UserCommand;


public class MoveModule implements Module, ResourceProvider<MoveModule.Mover> {
  private static final int THRESHOLD = 10;
  private static final List<Command> DUMMY_COMMAND = new ArrayList<Command>();
  
  static {
    DUMMY_COMMAND.add(new DummyCommand(-100, "MOVE"));
  }
  
  private enum State {IDLE, MOVING}
  
  private final EverquestSession session;
  private final List<Location> path = new LinkedList<Location>();
  
  private Mover currentMover;
  
  private float lastX;
  private float lastY;
  private State state = State.IDLE;
  
  public MoveModule(EverquestSession session) {
    this.session = session;
    
    session.registerResourceProvider(Mover.class, this);
    session.addUserCommand("move", Pattern.compile("loc ([0-9\\.]+,[0-9\\.]+)"), "loc <x>,<y>", new UserCommand() {
      public void onCommand(Matcher matcher) {
        String[] coords = matcher.group(1).split(",");
        float locX = Float.parseFloat(coords[1]);
        float locY = Float.parseFloat(coords[0]);
        
        moveTo(locX, locY);
      }
    });
  }
 
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
      
      if(me.isSitting()) {
        session.doCommand("/stand");
        return;
      }
      
      // Check if arrived
      while(path.size() > 0 && me.getDistance(path.get(0).x, path.get(0).y) < THRESHOLD) {
        path.remove(0);
      }
      
      if(path.size() == 0) {
        state = State.IDLE;
        session.doCommand("/keypress forward");
        return;
      }

      Location location = path.get(0);

      float heading = me.getHeading();
      float directHeading = calculateHeading(location.x, location.y);
      
      System.out.println("current heading = " + heading + "; dh = " + directHeading + "; diff = " + headingDiff(heading, directHeading) + "; ("+ session.getMe().getX() + ", " + session.getMe().getY() + ")");
      
      // Adjust heading if needed
      if(Math.abs(headingDiff(heading, directHeading)) > 3.0) {
        session.doCommand(String.format("/face fast nolook loc %.2f,%.2f", location.getY(), location.getX()));
        return;
      }
      
      // Heading in right direction and not arrived yet
      if(state == State.IDLE) {
        session.doCommand("/keypress forward hold");
        state = State.MOVING;
        return;
      }
      
      // Check distance travelled
      double distanceTravelled = me.getDistance(lastX, lastY);
      
      if(distanceTravelled < 3) {
        // System.err.println("Might be stuck at (" + me.getX() + ", " + me.getY());
      }
    }
  }
    
  public Mover obtainResource() {
    if(currentMover != null) {
      return null;
    }
    
    currentMover = new Mover();
    return currentMover;
  }

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
  
  void addWayPoint(float x, float y) {
    this.path.add(new Location(x, y, 0));
  }

  void moveTo(float x, float y) {
    this.path.clear();
    this.path.add(new Location(x, y, 0));
  }

  void clear() {
    this.path.clear();
  }
  
  private float calculateHeading(float x, float y) {
    return (float)(Math.atan2(x - session.getMe().getX(), y - session.getMe().getY()) / Math.PI * 180);
  }
  
  private float headingDiff(float h1, float h2) {
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
    return 0;
  }

  public boolean isLowLatency() {
    return true;
  }
  
  public class Mover {
    public void addWayPoint(float x, float y) {
      if(this != currentMover) {
        throw new IllegalStateException("This mover is not the current mover");
      }
      MoveModule.this.addWayPoint(x, y);
    }
    
    public void moveTo(float x, float y) {
      if(this != currentMover) {
        throw new IllegalStateException("This mover is not the current mover");
      }
      MoveModule.this.moveTo(x, y);
    }
    
    public void clear() {
      if(this != currentMover) {
        throw new IllegalStateException("This mover is not the current mover");
      }
      MoveModule.this.clear();
    }
  }
}
