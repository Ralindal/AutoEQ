package autoeq.eq;

public interface Command {

  /**
   * @return priority of the command, lower values are higher priority
   */
  public double getPriority();

  /**
   * @return <code>true</code> if command was succesfully activated
   */
  public boolean execute(EverquestSession session);
}
