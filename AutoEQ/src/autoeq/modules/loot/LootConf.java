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
      lore = conf.lore;
      delay = conf.delay;
      maxStack = conf.maxStack;
      unique = conf.unique;
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
  private Mode lore = Mode.OFF;

  @Parameter
  private int delay = 0;

  @Parameter
  private int maxStack = 0;

  @Parameter
  private boolean unique = false;

  @Parameter
  private boolean destroyQuest = false;

  public String getPattern() {
    return pattern;
  }

  public void setPattern(String pattern) {
    this.pattern = pattern;
  }

  public Mode getUpgrades() {
    return upgrades;
  }

  public Mode getNormal() {
    return normal;
  }

  public Mode getLore() {
    return lore;
  }

  public int getDelay() {
    return delay;
  }

  public int getMaxStack() {
    return maxStack;
  }

  public boolean isDestroyQuest() {
    return destroyQuest;
  }

  public boolean isUnique() {
    return unique;
  }
}
