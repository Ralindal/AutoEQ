package autoeq;

public class DebuffCounter {
  public enum Type {POISON, DISEASE, CURSE, CORRUPTION}

  private final Type type;
  private final int counters;
  
  public DebuffCounter(Type type, int counters) {
    this.type = type;
    this.counters = counters;
  }
  
  public int getCounters() {
    return counters;
  }
  
  public Type getType() {
    return type;
  }
}
