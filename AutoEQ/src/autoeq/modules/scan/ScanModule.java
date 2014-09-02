package autoeq.modules.scan;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import autoeq.ThreadScoped;
import autoeq.eq.Command;
import autoeq.eq.EffectType;
import autoeq.eq.EverquestSession;
import autoeq.eq.Me;
import autoeq.eq.Module;
import autoeq.eq.Spawn;

import com.google.inject.Inject;

@ThreadScoped
public class ScanModule implements Module {
  private final EverquestSession session;
  private final Map<Spawn, Long> lastScanTimes = new HashMap<>();

  private Spawn previousTarget;
  private long previousTargetMillis;

  @Inject
  public ScanModule(final EverquestSession session) {
    this.session = session;
  }

  @Override
  public int getBurstCount() {
    return 8;
  }

  @Override
  public List<Command> pulse() {
    Me me = session.getMe();

    if(!me.getClassShortName().equals("ENC")) {
      return null;
    }

    if(me.isAlive() && !me.isMoving() && me.getExtendedTargetCount() > 0) {
      Spawn currentTarget = me.getTarget();
      long currentTimeMillis = System.currentTimeMillis();

      /*
       * Checks if whatever we have targeted has been targeted for atleast 500 ms; if so
       * we assume that buffs were updated
       */

      boolean scanNext = false;

      if(currentTarget != null) {
        if(currentTarget.equals(previousTarget)) {
          if(previousTargetMillis + 500 < currentTimeMillis) {
            if(!lastScanTimes.containsKey(currentTarget)) {
              session.log(">>> Scanned: " + currentTarget + "; mez status = " + currentTarget.getActiveEffect(EffectType.MEZ));
            }
            lastScanTimes.put(currentTarget, currentTimeMillis);
            scanNext = true;
          }
        }
        else {
          previousTargetMillis = currentTimeMillis;
          previousTarget = currentTarget;
        }
      }
      else {
        scanNext = true;
        previousTarget = null;
      }

      if(scanNext) {
        for(final Spawn spawn : me.getExtendedTargets()) {
          Long lastScanTime = lastScanTimes.get(spawn);

          /*
           * Check if mob was scanned or if last scan time is over 30 seconds ago:
           */

          if(lastScanTime == null || lastScanTime + 30000 < currentTimeMillis) {

            /*
             * If spawn was updated recently (because another bot had it targetted), then don't scan, otherwise scan first
             * matching extended target:
             */

            if(spawn.getLastBuffUpdateMillis() + 5000 > currentTimeMillis) {
              lastScanTimes.put(spawn, currentTimeMillis);
            }
            else {
              List<Command> commands = new ArrayList<>();

              commands.add(new Command() {
                @Override
                public double getPriority() {
                  return 5000;
                }

                @Override
                public boolean execute(EverquestSession session) {
                  session.doCommand("/target id " + spawn.getId());
                  session.log(">>> Scanning: " + spawn + "; mez status = " + spawn.getActiveEffect(EffectType.MEZ));

                  return false;
                }
              });

              return commands;
            }
          }
        }
      }

      return null;
    }

    lastScanTimes.clear();

    return null;
  }

  public void reset() {
    lastScanTimes.clear();
  }
}
