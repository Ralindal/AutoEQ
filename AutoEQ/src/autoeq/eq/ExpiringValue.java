package autoeq.eq;

public class ExpiringValue<T> {
  private final long expiryTime;

  private long expirationTime;
  private T value;

  public ExpiringValue(long expiryTime) {
    this.expiryTime = expiryTime;
  }

  public void setValue(T value) {
    this.value = value;
    this.expirationTime = System.currentTimeMillis() + expiryTime;
  }

  public T getValue() {
    return value;
  }

  public boolean isExpired() {
    return System.currentTimeMillis() > expirationTime;
  }
}
