package autoeq.eq;

import java.util.Arrays;

public class MemorizeCommand implements Command {
  private final double priority;
  private final String moduleName;
  private final Gem[] gems;

  public MemorizeCommand(double priority, String moduleName, Gem... spellSlots) {
    int highestPriority = 0;

    for(Gem gem : spellSlots) {
      highestPriority = Math.max(highestPriority, gem.getPriority());
    }

    this.priority = priority - highestPriority;
    this.moduleName = moduleName;
    this.gems = spellSlots;
  }

  @Override
  public double getPriority() {
    return priority;
  }

  @Override
  public boolean execute(EverquestSession session) {
    if(!session.getMe().isCasting()) {
      return session.getMe().memorize(gems);
    }

    return false;
  }

  @Override
  public String toString() {
    return getClass().getName() + "[" + Arrays.toString(gems) + "]";
  }
}
