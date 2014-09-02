package autoeq.modules.pull;

import autoeq.commandline.Parameter;

public class PullConf {
  public enum State {ON, OFF}
  public enum Order {PATH, DENSITY, NEAREST}
  public enum Mode {CAMP, PATH}

  @Parameter(defaultParameter = true)
  private State state = State.OFF;

  @Parameter
  private Mode mode = Mode.PATH;

  @Parameter
  private String path;

  @Parameter
  private String effect;

  @Parameter(hint = "seconds")
  private int ignoreAgro;

  @Parameter(hint = "0-10")
  private int minimum;

  @Parameter
  private Order order = Order.PATH;

  @Parameter(hint = "0-999")
  private int zRange = 30;

  @Parameter(name = "nameds")
  private boolean pullNameds = true;

  @Parameter(name = "ignoredHostiles")
  private String ignoredHostiles;

  public PullConf(PullConf source) {
    if(source != null) {
      state = source.state;
      mode = source.mode;
      path = source.path;
      effect = source.effect;
      ignoreAgro = source.ignoreAgro;
      minimum = source.minimum;
      order = source.order;
      zRange = source.zRange;
      pullNameds = source.pullNameds;
      ignoredHostiles = source.ignoredHostiles;
    }
  }

  public PullConf() {
    this(null);
  }

  public State getState() {
    return state;
  }

  public Mode getMode() {
    return mode;
  }

  public String getPath() {
    return path;
  }

  public String getEffect() {
    return effect;
  }

  public int getIgnoreAgro() {
    return ignoreAgro;
  }

  public int getMinimum() {
    return minimum;
  }

  public Order getOrder() {
    return order;
  }

  public int getZRange() {
    return zRange;
  }

  public boolean isPullNameds() {
    return pullNameds;
  }

  public String getIgnoredHostiles() {
    return ignoredHostiles;
  }
}
