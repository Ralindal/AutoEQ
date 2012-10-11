package autoeq.eq;

public class ModuleInitializationEception extends RuntimeException {

  public ModuleInitializationEception() {
    super();
  }

  public ModuleInitializationEception(String message, Throwable cause) {
    super(message, cause);
  }

  public ModuleInitializationEception(String message) {
    super(message);
  }

  public ModuleInitializationEception(Throwable cause) {
    super(cause);
  }

}
