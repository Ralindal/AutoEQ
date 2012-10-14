package autoeq.modules.meditation;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import autoeq.ThreadScoped;
import autoeq.eq.CombatState;
import autoeq.eq.Command;
import autoeq.eq.EverquestSession;
import autoeq.eq.Me;
import autoeq.eq.Module;
import autoeq.eq.UserCommand;

import com.google.inject.Inject;


@ThreadScoped
public class MeditationModule implements Module {
  private final EverquestSession session;
  private final long sitDelay = 3000;
  
  private long delayUntil;
  private boolean wasSitting;
  private boolean active = true;
  
  @Inject
  public MeditationModule(final EverquestSession session) {
    this.session = session;
    
    session.addUserCommand("med", Pattern.compile("(on|off|status)"), "(on|off|status)", new UserCommand() {
      @Override
      public void onCommand(Matcher matcher) {
        if(matcher.group(1).toLowerCase().equals("on")) {
          active = true;
        }
        else if(matcher.group(1).toLowerCase().equals("off")) {
          active = false;
        }
      
        session.doCommand("/echo ==> Medding is " + (active ? "on" : "off") + ".");
      }
    });
  }

  public int getPriority() {
    return 9;
  }
  
  @Override
  public List<Command> pulse() {
    Me me = session.getMe();
    
    if(!active) {
      return null;
    }
    
    if(me.isMounted()) {
      return null;
    }
    
    // TODO isCasting is never true here... cause nobody is allowed to do module stuff while casting.
    
    if(me.wasCasting(3500) || me.isMoving() || me.getCombatState() == CombatState.COMBAT) {
      delayUntil = System.currentTimeMillis() + sitDelay;
    }
    
    if(delayUntil < System.currentTimeMillis()) {
      boolean isSitting = me.isSitting();
      
      // System.out.println("isSitting = " + isSitting);
      
      // Seems broken, me isSitting...
      
      if(isSitting) {
        if(!wasSitting) {
          delayUntil = System.currentTimeMillis() + sitDelay;
        }
        else if(me.getMana() >= me.getMaxMana() && me.getEndurance() >= me.getMaxEndurance() && me.getHitPoints() >= me.getMaxHitPoints()) {
          session.log("MEDITATION: Standing up");
          session.doCommand("/stand");
        }
      }
      else {
        if(wasSitting) {
          delayUntil = System.currentTimeMillis() + sitDelay;
        }
        else if((me.getMana() < me.getMaxMana() * 0.98 && !me.isBard()) || (me.getEndurance() < me.getMaxEndurance() * 0.90 && me.getCombatState() == CombatState.ACTIVE) || (me.getMana() < me.getMaxMana() * 0.90 && me.isBard() && me.getCombatState() == CombatState.ACTIVE) || (me.getHitPointsPct() < 90 && me.getCombatState() == CombatState.ACTIVE)) {
          // Endurance is checked for >90% cause when out of food/drink endurances fluctuates a bit between 100% and 93%.
          //&& "WAR MNK ROG BER".contains(me.getClassShortName()
          session.log("MEDITATION: Sitting down (isSitting = " + isSitting + ")");
          session.doCommand("/sit on");
          delayUntil = System.currentTimeMillis() + sitDelay;
        }
      }
      
      wasSitting = isSitting;
    }
    
    return null;
  }
  
  @Override
  public boolean isLowLatency() {
    return false;
  }
}
