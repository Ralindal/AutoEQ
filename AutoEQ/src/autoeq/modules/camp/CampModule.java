package autoeq.modules.camp;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import autoeq.ThreadScoped;
import autoeq.eq.Command;
import autoeq.eq.EverquestSession;
import autoeq.eq.Location;
import autoeq.eq.Me;
import autoeq.eq.Module;
import autoeq.eq.Spawn;
import autoeq.eq.SpawnType;
import autoeq.eq.UserCommand;
import autoeq.ini.Section;
import autoeq.modules.pull.MoveUtils;
import autoeq.modules.pull.MoveModule.Mover;

import com.google.inject.Inject;


@ThreadScoped
public class CampModule implements Module {
  public enum CampMode {NONE, CAMP, STAY_NEAR}

  private final EverquestSession session;

  private long lastFaceMillis;

  private boolean maintainFellowshipCamp;
  private long fellowshipCampExpiryTime;
  private boolean campSet;
  private CampMode campMode;
  private String campFollowName;
  private float campX;
  private float campY;
  private int campZoneId;

  private Mover mover;

  private int radius;
  private int maxRadius;
  private String face;

  @Inject
  public CampModule(final EverquestSession session) {
    this.session = session;

    Section section = session.getIni().getSection("Camp");

    radius = Integer.parseInt(section.get("Radius"));
    maxRadius = Integer.parseInt(section.get("MaxRadius"));
    face = section.get("Face").toLowerCase();

    session.addUserCommand("camp", Pattern.compile("(on|off|stay near ([A-Za-z]+)|status|fs)"), "(on|off|status|stay near <PC name>|fs)", new UserCommand() {
      @Override
      public void onCommand(Matcher matcher) {
        if(matcher.group(1).equals("on")) {
          campMode = CampMode.CAMP;
          campSet = true;
          campX = session.getMe().getX();
          campY = session.getMe().getY();
          campZoneId = session.getZoneId();
        }
        else if(matcher.group(1).equals("off")) {
          campMode = CampMode.NONE;
          campSet = false;
        }
        else if(matcher.group(1).equals("fs")) {
          maintainFellowshipCamp = !maintainFellowshipCamp;
          fellowshipCampExpiryTime = Long.MIN_VALUE;
        }
        else if(matcher.group(1).startsWith("stay near ")) {
          campMode = CampMode.STAY_NEAR;
          campFollowName = matcher.group(2).trim();
          campSet = false;
        }

        String additionalText = "";

        if(maintainFellowshipCamp) {
          additionalText = " Maintaining fellowship camp.";
        }

        if(campMode == CampMode.CAMP) {
          session.doCommand("/echo ==> Camp is on " + String.format("(%.2f, %.2f)", campX, campY) + "." + additionalText);
        }
        else if(campMode == CampMode.STAY_NEAR) {
          session.doCommand("/echo ==> Camp is near " + campFollowName + ".");
        }
        else {
          session.doCommand("/echo ==> Camp is off.");
        }
      }
    });
  }

  public float getCampX() {
    return campX;
  }

  public float getCampY() {
    return campY;
  }

  public boolean isCampSet() {
    return campSet;
  }

  public int getPriority() {
    return 0;
  }

  @Override
  public boolean isLowLatency() {
    return false;
  }

  @Override
  public List<Command> pulse() {
    Me me = session.getMe();

    if(!me.isAlive()) {
      return null;
    }

    if(session.tryLockMovement()) {
      try {
        if(campMode == CampMode.CAMP) {
          if(campSet) {
            if(!me.isMoving() && !me.isCasting()) {
              if(campZoneId != session.getZoneId()) {
                session.log("CAMP: We zoned.  Turning camp off.");
                campSet = false;
                campMode = CampMode.NONE;
              }
              else {
                double distanceFromCamp = me.getDistance(campX, campY);

                if((!me.inCombat() && distanceFromCamp > radius) || distanceFromCamp > maxRadius) {
                  if(distanceFromCamp > 400) {
                    session.log("CAMP: We're very far away from camp.  Resetting camp location.");
                    campSet = false;
                  }
                  else {
                    session.log("CAMP: We're outside camp radius, moving back");

                    if(distanceFromCamp < radius * 2) {
                      MoveUtils.moveBackwardsTo(session, campX, campY);
                    }
                    else {
                      MoveUtils.moveTo(session, campX, campY);
                    }
                  }
                }
                else if(!me.inCombat() && maintainFellowshipCamp && fellowshipCampExpiryTime < System.currentTimeMillis()) {
                  fellowshipCampExpiryTime = System.currentTimeMillis() + 30 * 60 * 1000;

                  session.doCommand("/windowstate FellowshipWnd open");
                  session.doCommand("/nomodkey /notify FellowshipWnd FP_Subwindows tabselect 2");
                  session.delay(500);
                  session.doCommand("/nomodkey /notify FellowshipWnd FP_RefreshList leftmouseup");
                  session.delay(500);
                  session.doCommand("/nomodkey /notify FellowshipWnd FP_CampsiteKitList listselect 1");
                  session.delay(500);
                  session.doCommand("/nomodkey /notify FellowshipWnd FP_CampsiteKitList leftmouse 1");
                  session.delay(500);
                  session.doCommand("/nomodkey /notify FellowshipWnd FP_DestroyCampsite leftmouseup");

                  if(session.delay(1000, "${Window[ConfirmationDialogBox].Open}")) {
                    session.doCommand("/nomodkey /notify ConfirmationDialogBox Yes_Button leftmouseup");
                    session.delay(1000);
                  }

                  session.delay(500);
                  session.doCommand("/nomodkey /notify FellowshipWnd FP_CreateCampsite leftmouseup");
                  session.delay(500);
                  session.doCommand("/windowstate FellowshipWnd close");

                  session.echo("CAMP: Set new fellowship camp");
                }
              }

              Spawn nearestEnemy = null;
              double nearestDistance = Double.MAX_VALUE;

              for(Spawn spawn : session.getSpawns()) {
                if(spawn.isEnemy()) {
                  if(spawn.getDistance() < nearestDistance) {
                    nearestDistance = spawn.getDistance();
                    nearestEnemy = spawn;
                  }
                }
              }

              if(face.equals("nearest") && System.currentTimeMillis() - lastFaceMillis > 5000) {
                if(nearestEnemy != null && nearestEnemy.getDistance() < 50 && nearestEnemy.inLineOfSight() && !me.isSitting() && me.getType() == SpawnType.PC) {
                  float heading = me.getHeading();
                  float direction = nearestEnemy.getDirection();

                  float diff = heading - direction;
                  diff = Math.abs(diff > 180 ? diff - 360 : diff);

                  // System.out.println("Facing "  + nearestEnemy +  "; My angle = " + me.getHeading() + "; wanted = " + direction + "; diff = " + diff);
                  if(diff > 20) {
                    session.doCommand("/squelch /face id " + nearestEnemy.getId() + " nolook");
                    lastFaceMillis = System.currentTimeMillis();
                  }
                }
              }
            }
          }
        }
        else if(campMode == CampMode.STAY_NEAR) {
          if(!me.isCasting()) {
            // Algorithm should be something like:
            // 1) Wait until PC you want to stay near is outside of 50 range.
            // 2) When outside of range, look at last know positions of Spawn, find the last one that is just outside 50 range.
            // 3) Follow the path, sticking near the PC.
            Spawn leader = session.getSpawn(campFollowName);

            if(leader != null) {
              if(leader.getDistance() > 50) {
                if(mover == null) {
                  mover = session.obtainResource(Mover.class);

                  if(mover != null) {
                    session.doCommand("/echo CAMP: " + campFollowName + " moving out of range, following.");
                    List<Location> lastLocations = leader.getLastLocations();
                    int start = lastLocations.size() - 1;

                    for(int i = 0; i < lastLocations.size(); i++) {
                      if(me.getDistance(lastLocations.get(i).x, lastLocations.get(i).y) < 30) {
                        start = i;
                        break;
                      }
                    }

                    for(int i = start; i >= 0; i--) {
                      mover.addWayPoint(lastLocations.get(i).x, lastLocations.get(i).y);
                    }
                  }
                }
              }

              if(mover != null) {
                if(leader.getDistance() < 20) {
                  mover.clear();
                  session.releaseResource(mover);
                  mover = null;
                  session.doCommand("/echo CAMP: Reached " + campFollowName + ", holding.");
                }
                else {
                  mover.addWayPoint(leader.getX(), leader.getY());
                }
              }
            }
            else {
              session.log("CAMP: Can't follow '" + campFollowName + "'");

//              for(Spawn s : session.getSpawns()) {
//                System.err.println(s);
//              }

              if(mover != null) {
                session.releaseResource(mover);
                mover = null;
              }
            }
          }
        }
      }
      finally {
        session.unlockMovement();
      }
    }

    return null;
  }
}
