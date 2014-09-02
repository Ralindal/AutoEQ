package autoeq;

public class ExternalCommandEvent extends Event {
  private final String command;

  public ExternalCommandEvent(String command) {
    this.command = command;
  }

  public String getCommand() {
    return command;
  }
}