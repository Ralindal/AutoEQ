package autoeq;

import autoeq.eq.CombatState;
import autoeq.eq.EverquestSession;
import autoeq.eq.Spawn;

public class TargetPattern {
  public static boolean isValidTarget(String targetPattern, Spawn target) {
    EverquestSession session = target.getSession();
    String tempPattern = " " + targetPattern.toLowerCase() + " ";

    if(tempPattern.indexOf(" standing ") >= 0 && session.getMe().isSitting()) {
      return false;
    }
    if(tempPattern.indexOf(" combat ") >= 0 && session.getMe().getCombatState() != CombatState.COMBAT) {
      return false;
    }
    if(tempPattern.indexOf(" " + target.getClassShortName().toLowerCase() + " ") >= 0 && !target.isPet()) {
      return true;
    }
    if(tempPattern.indexOf(" self ") >= 0 && target.isMe()) {
      return true;
    }
        
    if(tempPattern.indexOf(" mypet ") >= 0 && target.isMyPet()) {
      return true;
    }
    if(tempPattern.indexOf(" pet ") >= 0 && target.isPet()) {
      return true;
    }
    
    return false;    
  }
}
