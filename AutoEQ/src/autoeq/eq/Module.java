package autoeq.eq;

import java.util.List;

public interface Module {
  public boolean isLowLatency();
  public List<Command> pulse();
}
