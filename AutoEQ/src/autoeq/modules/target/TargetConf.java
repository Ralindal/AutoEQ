package autoeq.modules.target;

import autoeq.commandline.Parameter;

public class TargetConf {
  public enum Mode {OFF, ASSIST, NEAREST, OLDEST, HIGHEST_LEVEL, SMART}

  @Parameter(defaultParameter = true)
  private Mode mode = Mode.OFF;

  @Parameter
  private int range = 50;

  public TargetConf(TargetConf source) {
    if(source != null) {
      mode = source.mode;
      range = source.range;
    }
  }

  public TargetConf() {
    this(null);
  }

  public Mode getMode() {
    return mode;
  }

  public void setMode(Mode mode) {
    this.mode = mode;
  }

  public int getRange() {
    return range;
  }
}
