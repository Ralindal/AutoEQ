package autoeq.modules.target;

import autoeq.commandline.Parameter;

public class TargetConf {
  public enum Mode {OFF, ASSIST, NEAREST, OLDEST, HIGHEST_LEVEL, SMART}

  @Parameter(defaultParameter = true)
  private Mode mode;

  @Parameter
  private int range = 50;

  public Mode getMode() {
    return mode;
  }

  public int getRange() {
    return range;
  }
}
