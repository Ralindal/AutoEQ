package autoeq.modules.melee;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


import autoeq.ExpressionEvaluator;
import autoeq.TargetPattern;
import autoeq.ThreadScoped;
import autoeq.eq.ChatListener;
import autoeq.eq.Command;
import autoeq.eq.EverquestSession;
import autoeq.eq.ExpressionRoot;
import autoeq.eq.Listener;
import autoeq.eq.Me;
import autoeq.eq.Module;
import autoeq.eq.Spawn;
import autoeq.eq.SpawnType;
import autoeq.eq.UserCommand;
import autoeq.ini.Section;
import autoeq.modules.target.TargetModule;

import com.google.inject.Inject;

@ThreadScoped
public class MeleeModule implements Module {
  private static final Pattern MELEE_PROBLEM = Pattern.compile("(Your target is too far away, get closer!)");
//You cannot see your target\\.

  private final EverquestSession session;
  private final TargetModule targetModule;

  private final String validTargets;
  private final List<String> conditions;

  private Spawn currentTarget;
  private int resetCount;
  private long petAttackLockout;
  private int range = 50;
  private boolean attacking;
  private boolean active;
  private boolean petOnly;

  @Inject
  public MeleeModule(final EverquestSession session, TargetModule targetModule) {
    this.session = session;
    this.targetModule = targetModule;

    Section section = session.getIni().getSection("Melee");

    active = section.getDefault("Active", "true").toLowerCase().equals("true");
    petOnly = section.getDefault("PetOnly", "false").toLowerCase().equals("true");
    validTargets = section.getDefault("ValidTargets", "war pal shd mnk rog ber rng bst brd clr shm dru enc mag nec wiz pet");
    conditions = section.getAll("Condition");

    session.addChatListener(new ChatListener() {
      @Override
      public Pattern getFilter() {
        return MELEE_PROBLEM;
      }

      @Override
      public void match(Matcher matcher) {
        resetCount++;
      }
    });

    session.onZoned().call(new Listener() {
      @Override
      public void execute() {
        currentTarget = null;
        resetCount = 0;
        if(attacking) {
          session.unlockMovement();
          attacking = false;
        }
      }
    });

    session.addUserCommand("melee", Pattern.compile("(off|on|petonly|status|range ([0-9]+))"), "(off|on|petonly|status|range <range>)", new UserCommand() {
      @Override
      public void onCommand(Matcher matcher) {
        if(matcher.group(1).equals("off")) {
          active = false;
          petOnly = false;
          if(attacking) {
            session.unlockMovement();
            attacking = false;
          }
        }
        else if(matcher.group(1).equals("on")) {
          active = true;
          petOnly = false;
        }
        else if(matcher.group(1).equals("petonly")) {
          active = true;
          petOnly = true;
        }
        else if(matcher.group(1).startsWith("range ")) {
          range = Integer.parseInt(matcher.group(2));
        }

        session.echo("==> Melee is " + (active ? (petOnly ? "on (pet only)" : "on") : "off") + ".  Range is " + range + ".");
      }
    });
  }

  @Override
  public List<Command> pulse() {
    Me me = session.getMe();

    if((!me.isMoving() || me.isBard()) && me.getType() == SpawnType.PC && active && session.tryLockMovement()) {
      try {
        Spawn mainAssistTarget = targetModule.getMainAssistTarget();
        String meleeStatus = me.getMeleeStatus();

        if(currentTarget != null) {
          currentTarget = session.getSpawn(currentTarget.getId());   // Regets the target by ID from the actual spawns in the zone.  If spawn depopped, then this returns null.
        }

        if((currentTarget == null && (attacking || meleeStatus.contains("ENGAGED"))) || (currentTarget != null && (!currentTarget.isEnemy() || !currentTarget.isAlive() || currentTarget.getDistance() > range))) {
          session.echo("MELEE: Resetting (meleeStatus=" + meleeStatus + ")");
          currentTarget = null;
          session.doCommand("/melee reset");

          if(attacking) {
            session.unlockMovement();
            attacking = false;
          }
        }

        if(currentTarget == null || !currentTarget.equals(mainAssistTarget)) {
          if((mainAssistTarget != null && isValidTarget(mainAssistTarget) && mainAssistTarget.getDistance() < range)) {
            currentTarget = mainAssistTarget;
          }
          else {
            currentTarget = null;
          }
        }

        if(currentTarget != null) {
          if(!currentTarget.equals(me.getTarget()) || !meleeStatus.contains("ENGAGED") || (!(meleeStatus.contains("MELEE") || meleeStatus.contains("RANGE")) && "WAR SHD PAL RNG BRD BST MNK BER CLR".contains(me.getClassShortName()))) {
            if(!petOnly) {
              session.doCommand("/stand");
            }

            session.doCommand("/target id " + currentTarget.getId());

            if(session.delay(1000, "${Target.ID} == " + currentTarget.getId())) {
              session.delayUntilUpdate();
              session.echo("MELEE: Attacking [ " + currentTarget.getName() + " ] -- status = " + me.getMeleeStatus());
              if("WAR".contains(me.getClassShortName()) && currentTarget.getDistance() > 50) {
                session.doCommand("/stick 50%"); // --> Causes movement on target switch = bad for casting, need a better solution
              }

              sendInPet();

              session.doCommand("/killthis");
//              session.delay(1000);
//              session.doCommand("/killthis");

              if(!attacking) {
                attacking = true;
                resetCount = 0;

                session.lockMovement();
              }
            }
          }
          else if(attacking) {

            /*
             * Check if pet is attacking
             */

            if(me.getPet() != null && !me.getPet().isFacing(currentTarget, 5)) {
              sendInPet();
            }

            if(resetCount > 7) {
              session.echo("MELEE: Resetting after 8 tries");
              session.doCommand("/melee reset");
              session.delay(100);
              resetCount = 0;
            }
          }
//          else if(attacking) {
//            session.echo("Attack State = " + me.getMeleeStatus());
//          }
        }
      }
      finally {
        session.unlockMovement();
      }
    }

    return null;
  }

  private void sendInPet() {
    Me me = session.getMe();

    if(me.getPet() != null && petAttackLockout < System.currentTimeMillis()) {
      petAttackLockout = System.currentTimeMillis() + 1000;

      if(me.getTarget() != currentTarget) {
        session.doCommand("/target id " + currentTarget.getId());
        session.delay(1000, "${Target.ID} == " + currentTarget.getId());
        session.delayUntilUpdate();
      }

      if(me.getTarget() == currentTarget) {
        session.doCommand("/pet attack");
      }
    }
  }

  private boolean isValidTarget(Spawn spawn) {
    if(spawn.isEnemy()) {
      if(TargetPattern.isValidTarget(validTargets, spawn)) {
        if(ExpressionEvaluator.evaluate(conditions, new ExpressionRoot(session, spawn, null, null, null), this)) {
          return true;
        }
      }
    }

    return false;
  }

  @Override
  public int getBurstCount() {
    return 8;
  }
}
