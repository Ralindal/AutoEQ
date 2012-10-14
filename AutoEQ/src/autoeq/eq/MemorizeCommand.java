package autoeq.eq;

public class MemorizeCommand implements Command {
  private final double priority;
  private final String moduleName;
  private final Spell spell;
  private final int gem;

  public MemorizeCommand(double priority, String moduleName, Spell spell, int gem) {
    this.priority = priority;
    this.moduleName = moduleName;
    this.spell = spell;
    this.gem = gem;
  }

  @Override
  public double getPriority() {
    return priority;
  }

  @Override
  public boolean execute(EverquestSession session) {
    session.log(moduleName + ": Memorizing " + spell + " in slot " + gem);
    session.getMe().memorize(spell.getId(), gem);
    return true;
  }
  
  @Override
  public String toString() {
    return getClass().getName() + "[" + spell + " in " + gem + "]";
  }
}
