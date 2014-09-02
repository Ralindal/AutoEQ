package autoeq.modules.target;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import autoeq.ExpressionEvaluator;
import autoeq.TargetPattern;
import autoeq.ThreadScoped;
import autoeq.commandline.CommandLineParser;
import autoeq.eq.Command;
import autoeq.eq.Condition;
import autoeq.eq.EverquestSession;
import autoeq.eq.ExpressionRoot;
import autoeq.eq.Me;
import autoeq.eq.Module;
import autoeq.eq.Spawn;
import autoeq.eq.UserCommand;
import autoeq.ini.Section;
import autoeq.modules.camp.CampModule;
import autoeq.modules.target.TargetConf.Mode;

import com.google.inject.Inject;

@ThreadScoped
public class TargetModule implements Module {
  private static final double TARGET_OVERRIDE_SCORE = 1000000;  // Switches to the highest scoring target if that target minus the current target score is above this score
  private static final double TARGET_IN_CAMP_OVERRIDE_SCORE = 10000;  // Switches to highest scoring target if above this score and if current target is not above this score
  private static final double OVERRIDE_ALWAYS_PRI1 = TARGET_OVERRIDE_SCORE * 8;
  private static final double OVERRIDE_ALWAYS_PRI2 = TARGET_OVERRIDE_SCORE * 4;
  private static final double OVERRIDE_ALWAYS_PRI3 = TARGET_OVERRIDE_SCORE * 2;
  private static final double OVERRIDE_IN_CAMP_PRI1 = TARGET_IN_CAMP_OVERRIDE_SCORE * 8;
  private static final double OVERRIDE_IN_CAMP_PRI2 = TARGET_IN_CAMP_OVERRIDE_SCORE * 4;
  private static final double OVERRIDE_IN_CAMP_PRI3 = TARGET_IN_CAMP_OVERRIDE_SCORE * 2;
  private static final double TARGET_PRI1 = TARGET_IN_CAMP_OVERRIDE_SCORE / 2;
  private static final double TARGET_PRI2 = TARGET_IN_CAMP_OVERRIDE_SCORE / 4;
  private static final double TARGET_PRI3 = TARGET_IN_CAMP_OVERRIDE_SCORE / 8;

  private final EverquestSession session;

  private final String validTargets;
  private final List<String> conditions;
  private final List<String> assistConditions;

  private TargetConf conf = new TargetConf();
  private String[] names;
  private long targetDelay;

  private final CampModule campModule;

  @Inject
  public TargetModule(final EverquestSession session, CampModule campModule) {
    this.session = session;
    this.campModule = campModule;

    Section section = session.getIni().getSection("Assist");

    if(section == null) {
      section = session.getIni().getSection("Target");

      if(section == null) {
        section = new Section("Assist", new ArrayList<Section>());
      }
    }

    if(section.get("Active") != null) {
      System.err.println("WARN: Target/Active is deprecated, set Mode to 'off' if you donot want targetting");
    }

    conf.setMode(Mode.valueOf(section.getDefault("Mode", "assist").toUpperCase()));

    validTargets = section.getDefault("ValidTargets", "war pal shd mnk rog ber rng bst brd clr shm dru enc mag nec wiz pet");
    conditions = section.getAll("Condition");
    assistConditions = section.getAll("AssistCondition");
    names = section.getDefault("Names", "").split(",");

    session.addUserCommand("target", Pattern.compile(".+"), CommandLineParser.getHelpString(TargetConf.class), new UserCommand() {
      @Override
      public void onCommand(Matcher matcher) {
        TargetConf newConf = new TargetConf(conf);

        CommandLineParser.parse(newConf, matcher.group(0));

        conf = newConf;

        session.echo("==> Target acquirement is " + (conf.getMode() == Mode.OFF ? "off" : "on") + ".  Mode is: " + conf.getMode() + ".  Range is " + conf.getRange() + ". ZRange is " + conf.getZRange() + "." + (conf.getDelay() > 0 ? " Delay is " + conf.getDelay() + "s." : "") + (conf.isFriendly() ? " Targetting friendly targets." : "") + (conf.getMainAssist() != null && !conf.getMainAssist().isEmpty() ? " Main Assist is " + conf.getMainAssist() : ""));
      }
    });
  }

  @Override
  public List<Command> pulse() {
    return null;
  }

  private boolean isValidTarget(Spawn spawn) {
    if(spawn.isEnemy()) {
      if(TargetPattern.isValidTarget(validTargets, spawn)) {
        if(ExpressionEvaluator.evaluate(conditions, new ExpressionRoot(session, spawn, null, null, null), this)) {
          if((conf.getMode() != Mode.ASSIST && conf.getMode() != Mode.ASSIST_STRICT) || ExpressionEvaluator.evaluate(assistConditions, new ExpressionRoot(session, spawn, null, getMainAssist(), null), this)) {
            return true;
          }
        }
      }
    }

    return false;
  }

  @Override
  public int getBurstCount() {
    return 8;
  }

  private Spawn currentTarget;

  /**
   * Returns the current main target as targetted by the main assist (if in ASSIST mode), or
   * determines a target (if one wasn't determined already).
   *
   * @return a main target
   */
  public Spawn getMainAssistTarget() {
    Spawn bestTarget = currentTarget;

    if(conf.getMode() != Mode.OFF && conf.getMode() != Mode.MANUAL) {
      Me me = session.getMe();
      boolean campSet = false;
      float x = me.getX();
      float y = me.getY();
      int range = conf.getRange();

      if(campModule != null) {
        campSet = campModule.isCampSet();
        if(campSet) {
          x = campModule.getCampX();
          y = campModule.getCampY();
          range = campModule.getCampSize();
        }
      }

      if(conf.getMode() == Mode.ASSIST) {
        Spawn target = getFromAssist();
//        System.out.println(">>> mainassist has target: " + target);

        if(target != null) {
          if(Math.abs(target.getZ() - me.getZ()) < conf.getZRange() && target.getDistanceFromGroup(35) < conf.getRange()) {
            if(isValidTarget(target)) {
              bestTarget = target;
            }
          }
        }
      }
      else if(conf.getMode() == Mode.ASSIST_STRICT) {
        Spawn target = getFromAssist();

        if(target != null && Math.abs(target.getZ() - me.getZ()) < conf.getZRange() && target.getDistanceFromGroup(35) < conf.getRange() && isValidTarget(target)) {
          bestTarget = target;
        }
        else {
          bestTarget = null;
        }
      }
      else {
        if(bestTarget != null) {
          bestTarget = session.getSpawn(bestTarget.getId());
        }

        if(bestTarget != null && (!bestTarget.isEnemy() || !bestTarget.isAlive() || (bestTarget.getDistance() > conf.getRange() && !me.isExtendedTarget(bestTarget)))) {
          bestTarget = null;
        }

        if(bestTarget == null && bestTarget != currentTarget && conf.getDelay() > 0) {
          currentTarget = null;
          targetDelay = System.currentTimeMillis() + conf.getDelay() * 1000;
          session.echo("TARGET: Current Target died, delaying for " + conf.getDelay() + "s");
        }

        if(targetDelay < System.currentTimeMillis()) {
          Spawn currentBestTarget = getTargetBasedOnMode(bestTarget, me, x, y, range);
          if(bestTarget == null || !bestTarget.equals(currentBestTarget)) {
            bestTarget = currentBestTarget;

            if(bestTarget != null) {
              session.echo("TARGET: Best Target [ " + bestTarget.getName() + " ].  Level " + bestTarget.getLevel() + (bestTarget.isUnmezzable() ? ", Unmezzable" : "") + (bestTarget.isNamedMob() ? ", Named" : "") + ".");
            }

            currentTarget = bestTarget;
    //
    //          if(currentTarget != null) {
    //            session.doCommand("/target id " + currentTarget.getId());
    //          }
          }

          if(bestTarget != null) {
            bestTarget.increaseTankTime(2000000);  // We're assuming that when we are picking targets, we also tank them.  This may change in the future.
          }
        }
      }
    }
    else if(conf.getMode() != Mode.MANUAL) {
      currentTarget = null;
      return session.getMe().getTarget();
    }
    else if(conf.getMode() == Mode.MANUAL) {
      if(conf.isSelect()) {
        bestTarget = session.getMe().getTarget();
        currentTarget = bestTarget;
        conf.clearSelect();
      }
    }

    return bestTarget;
  }

  protected Spawn getTargetBasedOnMode(Spawn currentTarget, Me me, float x, float y, int range) {
    Spawn bestTarget = currentTarget;
    double currentTargetScore = getScore(currentTarget, me, x, y, range);
    double bestScore = Double.NEGATIVE_INFINITY;

    for(Spawn target : session.getSpawns()) {
      double score = getScore(target, me, x, y, range);

      if(score > bestScore &&
          (score - currentTargetScore >= TARGET_OVERRIDE_SCORE || (score - currentTargetScore >= TARGET_IN_CAMP_OVERRIDE_SCORE && (currentTargetScore % TARGET_OVERRIDE_SCORE) < TARGET_IN_CAMP_OVERRIDE_SCORE))) {
        bestTarget = target;
        bestScore = score;
      }
    }

    return bestTarget;
  }

  private double getScore(Spawn target, Me me, float x, float y, int range) {

    /*
     * Scoring works by assigning a value to a spawn.  If the target scores TARGET_OVERRIDE_SCORE points better, than it is preferred even over the current target.
     * By returning scores that are within TARGET_OVERRIDE_SCORE points of each other, the algorithm will always stick with the current target over any new target.  By
     * returning scores that are always TARGET_OVERRIDE_SCORE points or more distant from each other, the algorithm will always switch to the most desirable target.
     */

    if(target != null) {
      boolean isExtendedTarget = me.isExtendedTarget(target);
      double distanceToMe = target.getDistance(me.getX(), me.getY());

      /*
       * Target must be on extended target unless we also target friendly targets.
       */

      if(conf.isFriendly() || isExtendedTarget) {

        /*
         * Target must be within zrange and either be an extended target or within range of us and the camp (if set)
         */

        if(Math.abs(target.getZ() - me.getZ()) < conf.getZRange() && ((target.getDistance(x, y) < conf.getRange() && distanceToMe < conf.getRange()) || isExtendedTarget)) {
          if(isValidTarget(target)) {
            if(conf.getMode() == Mode.OLDEST) {

              /*
               * Always favor oldest target.
               */

              return -target.getCreationDate().getTime() * TARGET_OVERRIDE_SCORE;
            }
            else if(conf.getMode() == Mode.HIGHEST_LEVEL) {

              /*
               * Favor highest level, but stays on current if target within 4 levels.
               */

              return target.getLevel() * (TARGET_OVERRIDE_SCORE / 5);
            }
            else if(conf.getMode() == Mode.SMART) {
              if(distanceToMe < 30) {
                distanceToMe = 0;
              }

              /*
               * Switches for:
               * 1) Targets that are aggro
               * 2) Priority Targets
               * 3) Unmezzables (if group has CC)
               *
               * Then:
               * - Favors mobs in camp
               *
               * If not in camp:
               * 1) Nameds
               * 2) Non-pets
               * 3) Current target
               *
               * Basically this switches whenever there is a high priority target, like an unmezzable (often nameds), an
               * named-spawned add that needs immediate killing or when something is on extended while the current target
               * is not.
               *
               * Furthermore, it will attempt to choose the first mob that will arrive in camp soon.  When everything is
               * in camp already, it will continue to favor the high priority targets but will choose amongst the mobs in
               * camp in the order of named, non-pet, current target.
               */

              return target.getLevel() - (distanceToMe / 100)
                  + (isExtendedTarget ? OVERRIDE_ALWAYS_PRI1 : 0)
                  + (target.isPriorityTarget() ? OVERRIDE_ALWAYS_PRI2 : 0)
                  + ((target.isUnmezzable() && me.getGroup().hasClass("BRD", "ENC")) ? OVERRIDE_ALWAYS_PRI3 : 0)
                  + ((target.getDistance() <= 35 || (target.getUnitsMoved(500) > 10 && target.getDistance() <= 70)) ? OVERRIDE_IN_CAMP_PRI1 : 0)
                  + (target.isNamedMob() ? TARGET_PRI1 : 0)
                  + (!target.isPet() ? TARGET_PRI2 : 0)
                  + (target == me.getTarget() ? TARGET_PRI3 : 0);
            }
            else if(conf.getMode() == Mode.CAMP) {

              /*
               * Favors nearest target to camp, but stays on current if difference is less than 10.
               */

              return -target.getDistance(x, y) * (TARGET_OVERRIDE_SCORE / 10);
            }
            else if(conf.getMode() == Mode.NEAREST) {

              /*
               * Favors nearest target (but within camp if set), but stays on current if difference is less than 10.
               */

              return -distanceToMe * (TARGET_OVERRIDE_SCORE / 10);
            }
            else {
              throw new IllegalStateException("Unknown targetSelection: " + conf.getMode());
            }
          }
        }
      }
    }

    return Double.NEGATIVE_INFINITY;
  }

  private long lastAgroMillis;
  private long lastAssistMillis;
  private Spawn lastMainTarget;
  private Spawn lastAgroTarget;

  public Spawn getMainAssist() {
    if(conf.getMode() == Mode.ASSIST || conf.getMode() == Mode.ASSIST_STRICT) {
      String confMainAssist = conf.getMainAssist();

      if(confMainAssist != null && !confMainAssist.isEmpty()) {
        Spawn spawn = session.getSpawn(conf.getMainAssist());

        if(spawn != null) {
          return spawn;
        }
      }

      for(String mainAssist : names) {
        Spawn spawn = session.getSpawn(mainAssist);

        if(spawn != null) {
          return spawn;
        }
      }

      return null;
    }

    return session.getMe();
  }

  /**
   * Gets the target currently targetted by the main assist (which can be yourself).
   *
   * @return the target currently targetted by the main assist
   */
  public Spawn getFromAssist() {
    Spawn mainAssist = getMainAssist();

    if(mainAssist == null) {
      return null;
    }

    Spawn target = null;

    if(session.getBots().contains(mainAssist)) {
      target = mainAssist.getTarget();
    }
    else if(lastAssistMillis + 5000 < System.currentTimeMillis()) {
      boolean mobNearby = false;

      for(Spawn nearbySpawn : session.getSpawns()) {
        if(nearbySpawn.isEnemy() && nearbySpawn.getDistance() < 100 && nearbySpawn.inLineOfSight()) {
          //System.err.println("assisting cause of : " + nearbySpawn);
          mobNearby = true;
          break;
        }
      }

      if(mobNearby && mainAssist.getDistance() < 50) {  // Only assist if main assist nearby, otherwise assist command fails and we'll assume that whatever we had targetted must be the main target(!!)
        final String currentTarget = session.translate("${Target.ID}");

        lastAssistMillis = System.currentTimeMillis();

        session.doCommand("/assist " + mainAssist.getName());
        session.delay(500, new Condition() {
          @Override
          public boolean isValid() {
            return !session.translate("${Target.ID}").equals(currentTarget);
          }
        });

        // System.out.println("Assisting no bot (" + mainAssist + ", bots = " + session.getBots() + ")");
        session.log("Assisting no bot (" + mainAssist.getName() + ", bots = " + session.getBots() + ")");

        String result = session.translate("${Target.ID}");
        target = result.equals("NULL") ? null : session.getSpawn(Integer.parseInt(result));
      }
    }
    else {
      target = lastMainTarget;
    }

    if(target != null && target.isEnemy() && ((target.isExtendedTarget() || mainAssist.isTargetExtendedTarget()) || session.getMe().isExtendedTargetFull() || target.isValidObjectTarget())) {

      /*
       * Update agro
       */

      if(mainAssist.isFacing(target, 45)) {
        if(target.equals(lastAgroTarget)) {
          long timeSpentAgroing = System.currentTimeMillis() - lastAgroMillis;
          target.increaseTankTime(timeSpentAgroing);
        }

        lastAgroTarget = target;
        lastAgroMillis = System.currentTimeMillis();
      }
      else {
        lastAgroTarget = null;
      }

      /*
       * Return target
       */

      lastMainTarget = target;
      return target;
    }

    return null;
  }
}
