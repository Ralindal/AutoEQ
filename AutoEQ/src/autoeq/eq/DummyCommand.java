package autoeq.eq;

public class DummyCommand implements Command {
  private final double priority;
  private final String moduleName;

  public DummyCommand(double priority, String moduleName) {
    this.priority = priority;
    this.moduleName = moduleName;
  }
  
  @Override
  public boolean execute(EverquestSession session) {
    return true;
  }

  @Override
  public double getPriority() {
    return priority;
  }
  
  @Override
  public String toString() {
    return getClass().getName() + "[" + moduleName + "]";
  }
}
