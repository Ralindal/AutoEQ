package autoeq.eq;

public class Timer {
  private final long millis;

  private long expiryTime;
  
  public Timer(long millis) {
    this.millis = millis;
    reset();
  }

  public void reset() {
    this.expiryTime = System.currentTimeMillis() + millis;
  }
  
  public boolean isExpired() {
    return System.currentTimeMillis() >= expiryTime;
  }
}
