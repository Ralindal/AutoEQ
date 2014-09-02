package autoeq.eq;

import java.util.List;

public interface Module {
  public int getBurstCount();
  public List<Command> pulse();
}
