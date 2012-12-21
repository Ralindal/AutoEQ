package autoeq;

import java.util.Map;

import autoeq.eq.Spawn;

public class BotUpdateEvent extends Event {
  private final String name;
  private final Map<Integer, Long> spellDurations;
  private final int healthPct;
  private final Spawn target;
  private final int spawnId;
  private final int manaPct;
  private final int endurancePct;
  private final int zoneId;

  private final long eventTime;

  public BotUpdateEvent(String name, int zoneId, int spawnId, Map<Integer, Long> spellDurations, int healthPct, int manaPct, int endurancePct, Spawn target) {
    this.eventTime = System.currentTimeMillis();
    this.name = name;
    this.zoneId = zoneId;
    this.spawnId = spawnId;
    this.spellDurations = spellDurations;
    this.healthPct = healthPct;
    this.manaPct = manaPct;
    this.endurancePct = endurancePct;
    this.target = target;
  }

  public long getEventAge() {
    return System.currentTimeMillis() - eventTime;
  }

  public int getZoneId() {
    return zoneId;
  }

  public int getSpawnId() {
    return spawnId;
  }

  public String getName() {
    return name;
  }

  public Map<Integer, Long> getSpellDurations() {
    return spellDurations;
  }

  public int getHealthPct() {
    return healthPct;
  }

  public int getManaPct() {
    return manaPct;
  }

  public int getEndurancePct() {
    return endurancePct;
  }

  public Spawn getTarget() {
    return target;
  }
}