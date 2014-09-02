package autoeq;

import java.util.HashMap;
import java.util.Map;

import autoeq.eq.EverquestSession;
import autoeq.eq.Spawn;
import autoeq.eq.Spell;
import autoeq.eq.SpellEffectManager;
import autoeq.eq.ZoningException;
import autoeq.eq.Spawn.Source;

public class BotUpdateEvent extends Event {
  private final int zoneId;

  private final String name;
  private final int spawnId;
  private final int healthPct;
  private final int manaPct;
  private final int endurancePct;
  private final boolean isUnderDirectAttack;
  private final Map<Integer, Long> spellDurations;

  private final int targetId;
  private final boolean targetIsExtendedTarget;
  private final int targetHitPointsPct;

  private final long eventTime;

  public BotUpdateEvent(int zoneId, Spawn spawn) {
    this.eventTime = System.currentTimeMillis();
    this.zoneId = zoneId;
    this.name = spawn.getName();
    this.spawnId = spawn.getId();
    this.healthPct = spawn.getHitPointsPct();
    this.manaPct = spawn.getManaPct();
    this.endurancePct = spawn.getEndurancePct();
    this.isUnderDirectAttack = spawn.isUnderDirectAttack();
    this.targetId = spawn.getTarget() == null ? 0 : spawn.getTarget().getId();
    this.targetIsExtendedTarget = spawn.getTarget() != null && spawn.getTarget().isExtendedTarget();
    this.targetHitPointsPct = spawn.getTarget() == null ? 0 : spawn.getTarget().getHitPointsPct();

    this.spellDurations = new HashMap<>();

    for(Spell spell : spawn.getSpellEffects()) {
      SpellEffectManager manager = spawn.getSpellEffectManager(spell);

      long timeLeft = manager.getMillisLeft();

      if(timeLeft > 0) {
        spellDurations.put(spell.getId(), timeLeft);
      }
    }
  }

  public long getEventAge() {
    return System.currentTimeMillis() - eventTime;
  }

  public String getName() {
    return name;
  }

  public void applyEvent(EverquestSession session) {
    Spawn botSpawn = session.getSpawn(spawnId);

    if(botSpawn != null && !botSpawn.isMe() && session.getZoneId() == zoneId) {
      if(spellDurations != null) {
        String buffsAndDurations = "";

        for(Map.Entry<Integer, Long> entry : spellDurations.entrySet()) {
          if(!buffsAndDurations.isEmpty()) {
            buffsAndDurations += " ";
          }
          buffsAndDurations += entry.getKey() + ":" + (entry.getValue() + 12000) / 1000;  // +12000 here to make sure Promised heals wear off -- the values are multiples of 6000 unfortunately
        }

        try {
          botSpawn.updateBuffsAndDurations(buffsAndDurations, true, Source.BOT);
        }
        catch(RuntimeException e) {
          System.out.println("Error while updating BOT: " + botSpawn.getName() + " for " + session.getMeSpawn());
          throw new ZoningException();
        }
      }

      botSpawn.updateHealth(healthPct, Source.BOT);
      botSpawn.updateMana(manaPct);
      botSpawn.updateEndurance(endurancePct);
      botSpawn.updateTarget(targetId, targetIsExtendedTarget);
      botSpawn.updateUnderDirectAttack(isUnderDirectAttack);

      if(targetId != 0) {
        Spawn target = session.getSpawn(targetId);

        if(target != null) {
          target.updateHealth(targetHitPointsPct, Source.BOT);
        }
      }
    }
  }
}