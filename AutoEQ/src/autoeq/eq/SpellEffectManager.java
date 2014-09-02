package autoeq.eq;

import autoeq.Attribute;
import autoeq.eq.CastResultMonitor.SpellCast;
import autoeq.spelldata.effects.EffectDescriptor;

public class SpellEffectManager {
  private final EverquestSession session;
  private final Spell spell;
  private final Spawn spawn;

  private long untilMillis;
  private long lastCastMillis;
  private long lastSuccesfulCastMillis;
  private boolean unremovable;
  private boolean unupdatable;

  private boolean lastCastWasSuccess;
  private int succesfulCastCount;
  private int totalCastCount;
  private int totalCastAttempts;
  private int cannotSee;
  private int resist;
  private int shrunk;

  public SpellEffectManager(Spell spell, Spawn spawn) {
//    System.out.println(">>> Created new effect manager for : " + spell + " + " + spawn);
    this.session = spawn.getSession();
    this.spell = spell;
    this.spawn = spawn;
  }

  public void addCastResult(SpellCast spellCast, String result, double durationExtensionFactor) {
    lastCastWasSuccess = false;
    lastCastMillis = System.currentTimeMillis();
    totalCastAttempts++;
    untilMillis = 0;  // Clear timer, it will be set again below

    if(!result.equals("CAST_SUCCESS") && !result.equals("CAST_ASSUMED_SUCCESS")) {
      session.echo("<" + spell.getName() + "> " + result);
      session.log("<" + spell.getName() + "> " + result);
    }

    if(result.equals("CAST_ASSUMED_SUCCESS")) {  // Temporary success assumption, to lock out spells of a similar type until we've determined the spell was truly succesful or not
      addSpellEffect(durationExtensionFactor);
    }
    else if(result.equals("CAST_SUCCESS")) {
      lastSuccesfulCastMillis = System.currentTimeMillis();
      lastCastWasSuccess = true;
      succesfulCastCount++;
      totalCastCount++;
      cannotSee = 0;
      resist /= 2;  // when succesful, lower resistyness counter by half

      addSpellEffect(durationExtensionFactor);
    }
    else if(result.equals("CAST_TRY_LATER")) {  // for rez
      addPermanentSpellEffect(30 * 1000);
      totalCastCount++;
    }
    else if(result.equals("CAST_IMMUNE")) {
      addSpellEffect(5 * 60 * 1000);
      totalCastCount++;
    }
    else if(result.equals("CAST_TAKEHOLD")) {
      if(spellCast.effect.getSpell() == null || spellCast.effect.getSpell().getDuration() > 0) {
        session.echo("CASTRESULT: [" + spawn.getName() + "] was unaffected by " + spell.getName() + ".  Ignoring for " + Math.max(spell.getDuration(), 30) + "s");
        addPermanentSpellEffect(Math.max(spell.getDuration() * 1000, 30000));
      }

      totalCastCount++;
    }
    else if(result.equals("CAST_CANNOTSEE")) {
      cannotSee++;
      long ignoreMillis = 2L << (cannotSee + 10);
      addPermanentSpellEffect(ignoreMillis);
      session.echo("CASTRESULT: [" + spawn.getName() + "] is not in line of sight for " + spell.getName() + ".  Ignoring for " + formatTime(ignoreMillis) + ".");
    }
    else if(result.equals("CAST_RESIST")) {
      resist++;
      totalCastCount++;

      if(spell.getDamageOverTime() > 0 || spell.getDamage() > 0) {
        long ignoreMillis = 2L << (resist + 12);
        addPermanentSpellEffect(ignoreMillis);
        session.echo("CASTRESULT: [" + spawn.getName() + "] resisting " + spell.getName() + ".  Ignoring for " + formatTime(ignoreMillis) + ".");
      }
      else if(resist > 4) {
        addPermanentSpellEffect(5 * 60 * 1000);
        session.echo("CASTRESULT: [" + spawn.getName() + "] resisting " + spell.getName() + ".  Flagging immune for 5 mins.");
      }
    }
    else if(result.equals("CAST_SHRINK")) {
      shrunk++;
      unremovable = true;
      if(shrunk >= 2) {
        addPermanentSpellEffect(24 * 60 * 60 * 1000);
      }
    }
    else if(result.equals("CAST_DISTRACTED")) {
      // Silenced or DA for example
      addSpellEffect(3 * 1000);  // Wait half a tick before trying again
    }
    else if(result.equals("CAST_INTERRUPTED") || result.equals("CAST_NOTARGET") || result.equals("CAST_OUTOFRANGE")) {
      // Do Nothing
      addSpellEffect(1 * 1000);  // Wait a second before trying again
    }
    else if(result.equals("CAST_RECOVER")) {
      addSpellEffect(spell.getRecastTime());
    }
    else if(result.equals("CAST_FIZZLE")) {
      // Ignore
    }
    else if(result.equals("CAST_NOT_ATTUNED")) {
      session.doCommand("/echo SpellEffectManager: Disabling '" + spellCast.effect + "' because not attuned");
      spellCast.effect.setUsable(false);
    }
    else if(result.equals("CAST_OUTDOORS")) {
      session.doCommand("/echo SpellEffectManager: Disabling '" + spellCast.effect + "' because not outdoors");
      if(spellCast.spellLine != null) {
        spellCast.spellLine.setEnabled(false);
      }
    }
    else if(result.equals("CAST_COMPONENTS")) {
      session.doCommand("/echo BUFF: Disabling '" + spellCast.effect + "' because out of components or spell disabled");
      if(spellCast.spellLine != null) {
        spellCast.spellLine.setEnabled(false);
      }
    }
    else {
      session.logErr("Unhandled cast result for spell " + spell + ": " + result);
      // Do Nothing
      addSpellEffect(1 * 1000);  // Wait a second before trying again
    }
  }

  public int getTotalCastCount() {
    return totalCastCount;
  }

  public int getSuccesfulCastCount() {
    return succesfulCastCount;
  }

  public int getTotalCastAttempts() {
    return totalCastAttempts;
  }

  public double getSecondsSinceLastCastAttempt() {
    return (double)(System.currentTimeMillis() - lastCastMillis) / 1000;
  }

  public double getSecondsSinceLastSuccesfulCast() {
    return (double)(System.currentTimeMillis() - lastSuccesfulCastMillis) / 1000;
  }

  private void addSpellEffect(double durationExtensionFactor) {
    if(session.getMe().isBard() && spell.getDuration() <= 30) {
      //System.err.println("Adding timer for " + spell + " : " + spell.getDuration());
      untilMillis = System.currentTimeMillis() + spell.getDuration() * 1000;
      unremovable = true;
      unupdatable = false;
    }
    else if(spell.getDuration() > 0) {
      // session.echo("Adding lockout of " + spell.getDuration() + "s for " + spell);
      // TODO +6000 is for Promised Renewal... need a way to make sure it has triggered before refreshing.
      untilMillis = System.currentTimeMillis() + (long)(spell.getDuration() * 1000 * durationExtensionFactor) + (spell.isDetrimental() ? 0 : 6000);
      unremovable = false;
      unupdatable = false;
    }
  }

  private void addSpellEffect(long millis) {
    untilMillis = System.currentTimeMillis() + millis;
    //System.err.println("Setting until millis for " + spell + " to " + untilMillis);
    unremovable = true;
    unupdatable = false;
  }

  private void addPermanentSpellEffect(long millis) {
    untilMillis = System.currentTimeMillis() + millis;
    //System.err.println("Setting until millis for " + spell + " to " + untilMillis);
    unremovable = true;
    unupdatable = true;
  }

  /**
   * The total duration in milliseconds that this effect has been present (possibly
   * from multiple casts).
   */
  public long getTotalDuration() {
    // TODO This doesn't work properly when the real time left of spells is much longer than the nominal spell duration (target buffs updating causes this)
    return (long)succesfulCastCount * (spell.getDuration() * 1000 + 6000) - (lastCastWasSuccess ? getMillisLeft() : 0);
  }

  public long getLastCastMillis() {
    return lastCastMillis;
  }

  public int getSecondsLeft() {
    return (int)(getMillisLeft() / 1000);
  }

  /**
   * @return duration before this effect runs out in milliseconds
   */
  public long getMillisLeft() {
    long duration = untilMillis - System.currentTimeMillis();

    if(duration < 0) {
      duration = 0;
    }

    return duration;
  }

  public void setMillisLeft(long millis) {
    if(!unupdatable) {
      untilMillis = System.currentTimeMillis() + millis;
      unremovable = false;
    }
  }

  /**
   * Used to indicate this spell is no longer present.  Spells are generally never completely
   * removed so it is possible to keep track of cast counts, etc.
   *
   * Note that the spell is not removed if we had a succesful cast less than a second ago --
   * this helps prevent double casts when the target's buff window has not registered the
   * effect landing yet (especially when a spell and an AA which can be cast back to back
   * can have the same effect).
   */
  public void clear() {
    if(!unremovable && lastSuccesfulCastMillis + 2000 < System.currentTimeMillis()) {
      setMillisLeft(0);
    }
  }

  public Spell getSpell() {
    return spell;
  }

  public EffectDescriptor getEffectDescriptor(Attribute spellEffect) {
    return getSpell().getRawSpellData().getEffect(spellEffect);
  }

  private static String formatTime(long millis) {
    if(millis < 60000) {
      return millis / 1000 + "s";
    }
    else if(millis < 600000) {
      return millis / 60000 + "m" + millis % 60000 / 1000 + "s";
    }
    else {
      return millis / 60000 + "m";
    }
  }

  public boolean isShrink() {
    return shrunk > 0;
  }

  @Override
  public String toString() {
    return "SEM[" + spell + "; secondsLeft=" + getSecondsLeft() + "]";
  }
}
