package autoeq;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.LineNumberReader;
import java.net.ConnectException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import autoeq.eq.EverquestSession;
import autoeq.eq.Me;
import autoeq.eq.Spawn;
import autoeq.eq.Spell;
import autoeq.eq.SpellEffectManager;
import autoeq.ini.Ini2;


// TODO DONE: Make cast priorities work within the same module (allow them to return multiple possible commands)
// TODO DONE: Loot after rez
// TODO DONE: Delayed looting
// TODO DONE: Melee module must reset when out of range message
// TODO DONE: Bard songs blocking haste from shammy.. songs should never have stacking crap
// TODO DONE MQ: Add Deity to Telnet for NPC's/Corpses
// TODO DONE MQ: Fix Group Player 5 (fixed already I think)
// TODO DONE MQ: Add new MQ2Cast for Bard casting
// TODO SOLVED: Keeps buffing a bot when bot has too many buffs.

// TODO DONE: Auto use "not locked" slots for swap buffs
// TODO Modules should be able to be reloaded without creating new instances
// TODO Wildcard ignore list
// TODO DONE: Use groupbuffs >2 only
// TODO DONE: Lock system can be simplified since lock system knows the current Active Module.
// TODO Near check for group buffs, can use move history to see PC ever comes near
// TODO When Melee module has picked a target, but MA switches, it will keep focusing on that target (which can be annoying when chanter/bard will then try to mez it).
// TODO Bard Mez needs shorter duration
// TODO Melee and mez at same time for bard?
// TODO Malo cure with Radiant Cure
// TODO Melee module still "forgets" to attack sometimes.  Must be fixed.  UPDATE: Checking melee problem texts now and resetting melee.
// TODO Experience print-out
// TODO Camp out possibility (solved with "modules unload" for now).
// TODO Global commands ("follow", "hold")
// TODO Profile toggling
// TODO Cleric pet casting (beneficial spell with NPC target)
// TODO Ignoring resists on some Area of Effect spells (to prevent recasting)
// TODO No need to wait before meleeing when Bard
// TODO DPS analysis on targets.  Problem: Using Melee DPS analysis is flawed because of range/filter issues.
// TODO Keeps buffing a bot when using group spell but bot is out of range according to server (z-axis issues for example).
// TODO Switch back to /casting for bards and test targetting with new MQ2Cast.  UPDATE: doesn't work still.

// TODO MQ: Attack state (is attack on or off)
// TODO MQ: Think we need to add ${Melee.Combat} and/or ${Melee.Status}

public class AutoEQ {
  public static void main(String[] args) throws UnknownHostException, IOException {
    Log.initialize();

    /*
     * Read spell data
     */

    Map<Integer, SpellData> rawSpellData = new HashMap<>();
    try(LineNumberReader reader = new LineNumberReader(new FileReader("spells_us.txt"))) {
      String line;

      while((line = reader.readLine()) != null) {
        String[] fields = line.split("\\^");
        SpellData sd = new SpellData(fields);
        rawSpellData.put(sd.getId(), sd);
      }
    }

    /*
     * Get global.ini
     */

    Ini2 globalIni = new Ini2(new File("global.ini"));

    /*
     * Initialize sessions
     */

    List<EverquestSession> sessions = new ArrayList<>();

    for(int port = 7777; port < 7783; port++) {
      try {
        sessions.add(new EverquestSession(rawSpellData, globalIni, "localhost", port, "root", "8192"));
      }
      catch(ConnectException e) {
        System.err.println("No EQ running on port " + port);
      }
      catch(Exception e) {
        System.err.println("Exception while creating session");
        e.printStackTrace();
      }
    }

    for(EverquestSession session : sessions) {
      new Thread(new PulseThread(session)).start();
    }
  }

  private static class Bot {
    private final String name;
    private final Map<Integer, Long> spellDurations;
    private final int healthPct;
    private final int targetId;
    private final int spawnId;

    public Bot(String name, int spawnId, Map<Integer, Long> spellDurations, int healthPct, int targetId) {
      this.name = name;
      this.spawnId = spawnId;
      this.spellDurations = spellDurations;
      this.healthPct = healthPct;
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

    public int getTargetId() {
      return targetId;
    }
  }

  private static final Map<String, Bot> bots = new HashMap<>();

  public static class PulseThread implements Runnable {
    private final EverquestSession session;

    public PulseThread(EverquestSession session) {
      this.session = session;
    }

    @Override
    public void run() {
      try {
        for(;;) {
          long startMillis = System.currentTimeMillis();

          try {
            session.pulse();

            synchronized(bots) {

              /*
               * Gather information about this bot
               */

              Me me = session.getMe();

              if(me != null) {
                Map<Integer, Long> spellDurations = new HashMap<>();

                for(Spell spell : me.getSpellEffects()) {
                  SpellEffectManager manager = me.getSpellEffectManager(spell);

                  long timeLeft = manager.getDuration();

                  if(timeLeft > 0) {
                    spellDurations.put(spell.getId(), timeLeft);
                  }
                }

                bots.put(me.getName(), new Bot(me.getName(), me.getId(), spellDurations, me.getHitPointsPct(), me.getTarget() == null ? 0 : me.getTarget().getId()));
              }

              /*
               * Update bot information for this session
               */

              Set<String> botNames = new HashSet<>();

              for(Bot bot : bots.values()) {
                botNames.add(bot.getName());

                Spawn botSpawn = session.getSpawn(bot.getSpawnId());
                String buffIds = "";

                for(int spellId : bot.getSpellDurations().keySet()) {
                  if(!buffIds.isEmpty()) {
                    buffIds += " ";
                  }
                  buffIds += spellId;
                }

                botSpawn.updateBuffs(buffIds);
                botSpawn.updateHealth(bot.getHealthPct());
                botSpawn.updateTarget(bot.getTargetId());
              }

              session.setBotNames(botNames);
            }
          }
          catch(Exception e) {
            System.err.println("Exception occured for session " + session);
            e.printStackTrace();
          }

          long duration = System.currentTimeMillis() - startMillis;

          if(duration > 20) {
            //System.out.println(">>> Slow Pulse (" + duration + " ms) for " + session + " -- " + session.getMe());
          }
          Thread.sleep(10);
        }
      }
      catch(InterruptedException e) {
        throw new RuntimeException(e);
      }
    }
  }
}
