package autoeq.eq;

import java.awt.geom.Point2D;

public class Location extends Point2D.Float {
  public java.lang.Float z;

  public Location(float x, float y, float z) {
    super(x, y);
    this.z = z;
  }

  public Location(float x, float y) {
    super(x, y);
  }

  public java.lang.Float getZ() {
    return z;
  }

  @Override
  public boolean equals(Object obj) {
    if(obj == null || !(obj instanceof Location)) {
      return false;
    }

    Location loc = (Location)obj;

    return loc.x == x && loc.y == y;
  }

  @Override
  public String toString() {
    return String.format("(%.1f,%.1f,%.1f)", y, x, z);
  }
}
