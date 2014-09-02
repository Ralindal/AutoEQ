package autoeq.modules.target;

import autoeq.commandline.Parameter;

public class TargetConf {
  public enum Mode {OFF, MANUAL, ASSIST, ASSIST_STRICT, NEAREST, CAMP, OLDEST, HIGHEST_LEVEL, SMART}

  @Parameter(defaultParameter = true)
  private Mode mode = Mode.OFF;

  @Parameter
  private String mainAssist;

  @Parameter
  private int range = 100;

  @Parameter
  private int zRange = 30;

  @Parameter
  private String ignorePattern;

  @Parameter
  private boolean friendly;

  @Parameter
  private int delay = 0;

  @Parameter
  private boolean select;

  public TargetConf(TargetConf source) {
    if(source != null) {
      mode = source.mode;
      mainAssist = source.mainAssist;
      range = source.range;
      zRange = source.zRange;
      ignorePattern = source.ignorePattern;
      friendly = source.friendly;
      delay = source.delay;
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

  public int getZRange() {
    return zRange;
  }

  public String getMainAssist() {
    return mainAssist;
  }

  public String getIgnorePattern() {
    return ignorePattern;
  }

  public boolean isFriendly() {
    return friendly;
  }

  public int getDelay() {
    return delay;
  }

  public boolean isSelect() {
    return select;
  }

  public void clearSelect() {
    this.select = false;
  }
}
