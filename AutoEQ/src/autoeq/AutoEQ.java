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
import autoeq.eq.Spawn;
import autoeq.ini.Ini2;

// TODO Modules should be able to be reloaded without creating new instances
// TODO Wildcard ignore list
// TODO DONE: Use groupbuffs >2 only
// TODO DONE: Lock system can be simplified since lock system knows the current Active Module.
// TODO Near check for group buffs, can use move history to see PC ever comes near
// TODO When Melee module has picked a target, but MA switches, it will keep focusing on that target (which can be annoying when chanter/bard will then try to mez it).
// TODO Bard Mez needs shorter duration
// TODO Melee and mez at same time for bard?
// TODO Malo cure with Radiant Cure
// TODO Camp out possibility (solved with "modules unload" for now).
// TODO Global commands ("follow", "hold")
// TODO Cleric pet casting (beneficial spell with NPC target)
// TODO Ignoring resists on some Area of Effect spells (to prevent recasting)
// TODO No need to wait before meleeing when Bard
// TODO DPS analysis on targets.  Problem: Using Melee DPS analysis is flawed because of range/filter issues.
// TODO Keeps buffing a bot when using group spell but bot is out of range according to server (z-axis issues for example).

// TODO MQ: Attack state (is attack on or off)
// TODO MQ: Think we need to add ${Melee.Combat} and/or ${Melee.Status}

public class AutoEQ {
  private static final Map<String, BotUpdateEvent> LAST_BOT_UPDATE_EVENTS = new HashMap<>();

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
        EverquestSession session = new EverquestSession(rawSpellData, globalIni, "localhost", port, "root", "8192");

        sessions.add(session);
        session.setBotUpdateHandler(new EventHandler<BotUpdateEvent>() {
          @Override
          public void handle(BotUpdateEvent event) {
            synchronized(LAST_BOT_UPDATE_EVENTS) {
              LAST_BOT_UPDATE_EVENTS.put(event.getName(), event);
            }
          }
        });
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

            synchronized(LAST_BOT_UPDATE_EVENTS) {

              /*
               * Update bot information for this session
               */

              Set<String> botNames = new HashSet<>();

              for(BotUpdateEvent botUpdateEvent : LAST_BOT_UPDATE_EVENTS.values()) {
                botNames.add(botUpdateEvent.getName());

                Spawn botSpawn = session.getSpawn(botUpdateEvent.getSpawnId());

                if(botSpawn != null && !botSpawn.isMe()) {
                  String buffIds = "";

                  for(int spellId : botUpdateEvent.getSpellDurations().keySet()) {
                    if(!buffIds.isEmpty()) {
                      buffIds += " ";
                    }
                    buffIds += spellId;
                  }

                  botSpawn.updateBuffs(buffIds);
                  botSpawn.updateHealth(botUpdateEvent.getHealthPct());
                  botSpawn.updateMana(botUpdateEvent.getManaPct());
                  botSpawn.updateEndurance(botUpdateEvent.getEndurancePct());
                  botSpawn.updateTarget(botUpdateEvent.getTargetId());
                }
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
