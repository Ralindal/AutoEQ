package autoeq.modules.loot;

import autoeq.commandline.Parameter;

public class LootConf {
  // /jb loot upgrades (on|off) --> loots anything that is an upgrade/useable augment
  // /jb loot pattern (X|off) --> loots anything matching X
  // /jb loot normal (on|off) --> does standard looting/destroying according to jloot.ini

  public LootConf(LootConf conf) {
    if(conf != null) {
      pattern = conf.pattern;
      upgrades = conf.upgrades;
      normal = conf.normal;
      delay = conf.delay;
    }
  }

  public LootConf() {
    this(null);
  }

  public enum Mode {ON, OFF}

  @Parameter
  private String pattern = "off";

  @Parameter
  private Mode upgrades = Mode.OFF;

  @Parameter
  private Mode normal = Mode.OFF;

  @Parameter
  private int delay = 0;

  public String getPattern() {
    return pattern;
  }

  public Mode getUpgrades() {
    return upgrades;
  }

  public Mode getNormal() {
    return normal;
  }

  public int getDelay() {
    return delay;
  }
}
