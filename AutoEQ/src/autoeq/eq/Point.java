package autoeq.eq;

public class Point<T> {
  private final T value;
  private final long millis;
  
  public Point(long millis, T value) {
    this.millis = millis;
    this.value = value;
  }

  public long getMillis() {
    return millis;
  }

  public T getValue() {
    return value;
  }
}