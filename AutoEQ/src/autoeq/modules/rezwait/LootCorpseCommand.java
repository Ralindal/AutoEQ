package autoeq.modules.rezwait;

import java.util.ArrayList;
import java.util.List;

import autoeq.eq.Command;
import autoeq.eq.EverquestSession;
import autoeq.eq.Spawn;


public class LootCorpseCommand implements Command {

  public boolean execute(EverquestSession session) {
    boolean corpseTooFar = false;
    
    /*
     * Create a list of nearby corpses.
     */
    
    List<Spawn> nearbyCorpses = new ArrayList<Spawn>();
    
//    for(Spawn spawn : session.getSpawns()) {
//      if(spawn.getName().equals(session.getMe().getName() + "'s corpse")) {
//        if(spawn.getDistance() < 75) {
//          nearbyCorpses.add(spawn);
//        }
//        else {
          corpseTooFar = true;
//        }
//      }
//    }
    
    if(nearbyCorpses.size() > 0) {
    
//      for(int i = 0; i < 15; i++) {
//        session.getLogger().info("My loc " + session.getMe().getX() + ", " + session.getMe().getY() + ", " + session.getMe().getZ());
//        System.err.println("My loc " + session.getMe().getX() + ", " + session.getMe().getY() + ", " + session.getMe().getZ());
//        System.err.println("Weight = " + session.getMe().getWeight());
//        System.err.println("Name = " + session.getMe().getName());
//        System.err.println("ID = " + session.getMe().getId());
//        session.delay(1000);
//      }
      
      /*
       * Target the nearby corpses one-by-one and summon them with /corpse.
       */
      
//      for(Spawn corpse : nearbyCorpses) {
//        session.doCommand("/target id " + corpse.getId());
//        
//        if(session.delay(1000, "${Target.ID} == " + corpse.getId())) {
//          session.doCommand("/corpse");
//          session.delay(500);
//        }
//      }
    
      /*
       * Loot all corpses that are at our feet.
       */
      
//      for(Spawn corpse : nearbyCorpses) {
//        if(corpse.getDistance() < 20) {
//          session.doCommand("/target id " + corpse.getId());
//          
//          if(session.delay(1000, "${Target.ID} == " + corpse.getId())) {
//            session.doCommand("/lootall");
//            session.delay(2500, "${Corpse.Open}");
//            session.delay(30000, "!${Corpse.Open}");
//          }
//        }
//      }
    }

    if(corpseTooFar) {
     // session.echo("REZWAIT: Atleast one corpse was too far away, please loot manually.");
      session.delay(5000);
    }
    
    return true;
  }

  public double getPriority() {
    return -1000;
  }
  
  public Spawn getNearestCorpse(EverquestSession session) {
    Spawn closestCorpse = null;
    double closestDistance = Double.MAX_VALUE;
    
    for(Spawn spawn : session.getSpawns()) {
      if(spawn.getName().equals(session.getMe().getName() + "'s corpse")) {
        if(spawn.getDistance() < closestDistance) {
          closestDistance = spawn.getDistance();
          closestCorpse = spawn;
        }
      }
    }
    
    return closestCorpse;
  }
}
