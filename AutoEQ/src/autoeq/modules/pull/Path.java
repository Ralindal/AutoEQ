package autoeq.modules.pull;

import java.awt.geom.Line2D;
import java.util.LinkedList;

import autoeq.eq.EverquestSession;
import autoeq.eq.Location;
import autoeq.eq.LocationListener;


public class Path implements LocationListener {
  private final EverquestSession session;
  private final LinkedList<Location> locations = new LinkedList<Location>();

  public Path(EverquestSession session) {
    this.session = session;
  }

  public void record() {
    session.getMe().addLocationListener(this);
  }

  public void stopRecording() {
    session.getMe().removeLocationListener(this);
  }

  public void playbackReverse() {
    stopRecording();

    for(int i = locations.size() - 1; i >= 0; i--) {
      Location location = locations.get(i);
      MoveUtils2.moveTowards(session, location.x, location.y);
    }
    MoveUtils2.stop(session);
  }

  @Override
  public void updateLocation(Location location) {
    if(locations.size() > 1) {
      Location last1 = locations.getLast();
      Location last2 = locations.get(locations.size() - 2);

      Line2D.Float line = new Line2D.Float(last2.x, last2.y, location.x, location.y);
      double ptLineDist = line.ptLineDist(last1.x, last1.y);
      double oldAngle = Math.toDegrees(Math.atan2(last1.y - last2.y, last1.x - last2.x));
      double newAngle = Math.toDegrees(Math.atan2(location.y - last1.y, location.x - last1.x));
      double angleDiff = angleDiff(oldAngle, newAngle);

      if(ptLineDist < 3 && location.distance(last2) < 20 && angleDiff < 20) {
        locations.removeLast();
      }
    }

    locations.addLast(location);
  }

  private static double angleDiff(double a, double b) {
    double diff = Math.max(a, b) - Math.min(a, b);

    return diff > 180 ? 360 - diff : diff;
  }
}
