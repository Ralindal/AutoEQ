package autoeq;

import java.util.Map;

public class BotUpdateEvent extends Event {
  private final String name;
  private final Map<Integer, Long> spellDurations;
  private final int healthPct;
  private final int targetId;
  private final int spawnId;
  private final int manaPct;
  private final int endurancePct;

  public BotUpdateEvent(String name, int spawnId, Map<Integer, Long> spellDurations, int healthPct, int manaPct, int endurancePct, int targetId) {
    this.name = name;
    this.spawnId = spawnId;
    this.spellDurations = spellDurations;
    this.healthPct = healthPct;
    this.manaPct = manaPct;
    this.endurancePct = endurancePct;
    this.targetId = targetId;
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

  public int getTargetId() {
    return targetId;
  }
}