package autoeq.eq;

public class SpellEffectManager {
  private final EverquestSession session;
  private final Spell spell;
  private final Spawn spawn;

  private long untilMillis;
  private long lastCastMillis;
  private boolean unremovable;
  private boolean unupdatable;

  private boolean lastCastWasSuccess;
  private int castCount;
  private int cannotSee;
  private int resist;
  private int shrunk;

  public SpellEffectManager(Spell spell, Spawn spawn) {
    this.session = spawn.getSession();
    this.spell = spell;
    this.spawn = spawn;
  }

  public void addCastResult(String result) {
    lastCastWasSuccess = false;
    lastCastMillis = System.currentTimeMillis();

    if(result.equals("CAST_SUCCESS")) {
      lastCastWasSuccess = true;
      castCount++;
      cannotSee = 0;
      resist /= 2;  // when succesful, lower resistyness counter by half

      if(!spell.isCharm()) {  // Donot add charms because they can wear of at any time.  They donot need to be added anyway, because when succesful, no new charms will hold
        addSpellEffect();
      }
    }
    else if(result.equals("CAST_IMMUNE")) {
      addSpellEffect(5 * 60 * 1000);
    }
    else if(result.equals("CAST_TAKEHOLD")) {
      addSpellEffect(25 * 60 * 1000);
    }
    else if(result.equals("CAST_CANNOTSEE")) {
      cannotSee++;
      long ignoreMillis = 2L << (cannotSee + 10);
      addSpellEffect(ignoreMillis);
      session.echo("CASTRESULT: [" + spawn.getName() + "] is not in line of sight for " + spell.getName() + ".  Ignoring for " + formatTime(ignoreMillis));
    }
    else if(result.equals("CAST_RESIST")) {
      resist++;
      if(resist > 4) {
        addSpellEffect(5 * 60 * 1000);
        session.echo("CASTRESULT: [" + spawn.getName() + "] resisting " + spell.getName() + ".  Flagging immune for 5 mins.");
      }
    }
    else if(result.equals("CAST_SHRINK")) {
      System.err.println("Casted shrink" + resist);
      shrunk++;
      if(shrunk >= 2) {
        addPermanentSpellEffect(24 * 60 * 60 * 1000);
      }
    }
    else if(result.equals("CAST_DISTRACTED")) {
      // Silenced for example
      addSpellEffect(3 * 1000);  // Wait half a tick before trying again
    }
    else if(result.equals("CAST_INTERRUPTED") || result.equals("CAST_NOTARGET") || result.equals("CAST_OUTOFRANGE")) {
      // Do Nothing
    }
    else {
      session.logErr("Unhandled cast result for spell " + spell + ": " + result);
    }
  }

  private void addSpellEffect() {
    if(session.getMe().isBard() && spell.getDuration() <= 30) {
      //System.err.println("Adding timer for " + spell + " : " + spell.getDuration());
      untilMillis = System.currentTimeMillis() + spell.getDuration() * 1000;
      unremovable = true;
      unupdatable = false;
    }
    else if(spell.getDuration() > 0) {
      // session.echo("Adding lockout of " + spell.getDuration() + "s for " + spell);
      // TODO +6000 is for Promised Renewal... need a way to make sure it has triggered before refreshing.
      untilMillis = System.currentTimeMillis() + spell.getDuration() * 1000 + 6000;
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
    return (long)castCount * (spell.getDuration() * 1000 + 6000) - (lastCastWasSuccess ? getMillisLeft() : 0);
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

  public Spell getSpell() {
    return spell;
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

  public boolean isRemoveable() {
    return !unremovable || getMillisLeft() == 0;
  }
}
