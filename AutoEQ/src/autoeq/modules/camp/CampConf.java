package autoeq.modules.camp;

import autoeq.commandline.Parameter;

public class CampConf {
  public enum Mode {CLEAR, SET}

  @Parameter(defaultParameter = true)
  private Mode mode = Mode.CLEAR;

  @Parameter(hint = "PC name")
  private String at;

  @Parameter
  private int size = 10;

  @Parameter
  private int maxDistance = 70;

  @Parameter
  private boolean fs;

  public CampConf(CampConf source) {
    if(source != null) {
      mode = source.mode;
      at = source.at;
      size = source.size;
      maxDistance = source.maxDistance;
      fs = source.fs;
    }
  }

  public CampConf() {
    this(null);
  }

  public Mode getMode() {
    return mode;
  }

  public String getAt() {
    return at;
  }

  public void setAt(String at) {
    this.at = at;
  }

  public int getSize() {
    return size;
  }

  public int getMaxDistance() {
    return maxDistance;
  }

  public boolean isFs() {
    return fs;
  }

  public void setMode(Mode mode) {
    this.mode = mode;
  }
}
