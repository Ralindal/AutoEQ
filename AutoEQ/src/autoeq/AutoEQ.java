package autoeq;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.LineNumberReader;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import autoeq.eq.DefaultEverquestSession;
import autoeq.eq.EverquestSession;

// TODO Fix DOT duration --> fixed automatically due to Target Buffs, but only if grouped
// TODO Never turn while FD
// TODO Option to stop all activity when extended target is empty and PC nearby
// TODO Need pull, loot and melee to be in command form to be able to stop their activity
// TODO Need to know when a disc will use the 'disc' slot, so it is known which discs stack and which not
// TODO Reprieve/Rest just after kill and just before pull hard to do now because pull module is not a prioritized command
// TODO Group spells will apply the spell to all valid targets, even if not grouped (ie, ungrouped bot) --> not a big deal, buffs get updated anyway
// TODO Wrong buffs appear in target buff window (lots of beneficials, with big gap) for a mob, while it should have been empty (no info).  Cannot trust this for mobs when ungrouped.

// ${Window[CombatAbilityWnd].Child[CAW_CombatEffectLabel].Text} -> "No Effect"

// For MQ2Telnet:
// TODO Add information about the Pet's buffs
// DONE Add MaxMeleeRange for Spawns (to help determine if a mob is within "range" of the group)
// TODO User activity info, key presses
// TODO Fix door target to target by nearest Z as well
// TODO Check why an instant spell takes atleast 600-700 ms to cast
// DONE Add casting id
// DONE Apparently, it's possible to send a burst with just "##" in it == empty burst
// DONT Radius limit bursts -> don't, need mobs for pulling

// TODO Odd problems with buffing when some of the group cannot take the buff and some can.
// - Unity not being cast, despite some members not having it.
// - Unity being cast despite members using Shield buf.
// - Unity suddenly being cast when I cast it on myself and remove it again.
// - After reload, Unity attempted on members having shield buf, but not on one that doesn't use it --> BOT must be sending that it still has the buff(??)

// Important:
// TODO Modules should reconfigure, not completely reset
// TODO Better parameter handling
// TODO Somekind of lock problem with BOT_UPDATE events

// TODO Donot pull if there's a dead group member

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
// TODO Auto-discover clickies by looking through items
// TODO Way to distinguish spells with same name(!)... Wizard Concussive Burst...
// TODO Automatic /pet get lost (wizard familiar)

// TODO MQ: Attack state (is attack on or off)
// TODO MQ: Think we need to add ${Melee.Combat} and/or ${Melee.Status}

public class AutoEQ {
  private static final Map<String, BotUpdateEvent> LAST_BOT_UPDATE_EVENTS = new HashMap<>();
  private static final Map<Integer, EverquestSession> sessions = new HashMap<>();

  public static void main(String[] args) throws UnknownHostException, IOException, InterruptedException {
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

    ItemDAO itemDAO = new FileItemDAO();

    /*
     * Initialize sessions
     */

    for(;;) {
      for(int port = 7777; port < 7784; port++) {
        try {
          synchronized(sessions) {
            if(!sessions.containsKey(port)) {
              final EverquestSession session = new DefaultEverquestSession(itemDAO, rawSpellData, new File("global.ini"), "localhost", port, "root", "8192");

              sessions.put(port, session);

              session.setBotUpdateHandler(new EventHandler<BotUpdateEvent>() {
                @Override
                public void handle(BotUpdateEvent event) {
                  synchronized(LAST_BOT_UPDATE_EVENTS) {
                    LAST_BOT_UPDATE_EVENTS.put(event.getName(), event);

                    // Remove any events older than 5 seconds
                    for(Iterator<BotUpdateEvent> iterator = LAST_BOT_UPDATE_EVENTS.values().iterator(); iterator.hasNext();) {
                      BotUpdateEvent e = iterator.next();

                      if(e.getEventAge() > 5000) {
                        System.err.println("Removed old bot event: " + e.getName() + ": " + e);
                        iterator.remove();
                      }
                    }
                  }
                }
              });

              session.setCommandHandler(new EventHandler<ExternalCommandEvent>() {
                @Override
                public void handle(ExternalCommandEvent event) {
                  String command = event.getCommand();
                  int colon = command.indexOf(":");

                  String targetSession = command.substring(0, colon).trim();

                  synchronized(sessions) {
                    for(EverquestSession otherSession : sessions.values()) {
                      if(targetSession.equalsIgnoreCase("all") ||
                        (targetSession.equalsIgnoreCase("group") && otherSession.getGroupMemberNames().contains(session.getMe().getName())) ||
                        (targetSession.equalsIgnoreCase("others") && otherSession != session) ||
                        (targetSession.equalsIgnoreCase("zone") && otherSession.getZoneId() == session.getZoneId()) ||
                        (targetSession.equalsIgnoreCase("!zone") && otherSession.getZoneId() != session.getZoneId()) ||
                        (targetSession.equalsIgnoreCase("near") && otherSession.getZoneId() == session.getZoneId() && otherSession.getMeSpawn() != null && session.getMeSpawn() != null && otherSession.getMeSpawn().getDistance(session.getMeSpawn().getX(), session.getMeSpawn().getY()) < 200) ||
                        (otherSession.getMeSpawn() != null && otherSession.getMeSpawn().getName().equalsIgnoreCase(targetSession))) {

                        otherSession.addExternalCommand(command.substring(colon + 1).trim());
                      }
                    }
                  }
                }
              });

              new Thread(new PulseThread(session), "PulseThread(" + session + ")").start();
            }
          }
        }
        catch(SocketTimeoutException e) {
          // Occurs when connection is rejection when already connected
          System.err.println("EQ running on port " + port + ", but did not accept connection.  Another AutoEQ running?");
        }
        catch(ConnectException e) {
          // Occurs when the port is not listening (ie, no EQ), we ignore this
        }
        catch(Exception e) {
          System.err.println("Exception while creating session");
          e.printStackTrace();
        }
      }

      Thread.sleep(10000);
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
            Map<String, BotUpdateEvent> lastBotUpdateEvents;

            synchronized(LAST_BOT_UPDATE_EVENTS) {
              lastBotUpdateEvents = new HashMap<>(LAST_BOT_UPDATE_EVENTS);
            }

            session.pulse(lastBotUpdateEvents.values());
          }
          catch(SessionEndedException e) {
            break;
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

      synchronized(sessions) {
        sessions.remove(session.getPort());
      }

      System.err.println("EQ Session ended, pulse thread exiting: " + session);
    }
  }
}
