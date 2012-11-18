package autoeq.modules.loot;

import java.io.IOException;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import autoeq.ThreadScoped;
import autoeq.eq.Command;
import autoeq.eq.EverquestSession;
import autoeq.eq.Me;
import autoeq.eq.Module;
import autoeq.eq.Spawn;
import autoeq.eq.SpawnType;
import autoeq.eq.UserCommand;
import autoeq.modules.pull.MoveUtils;

import com.google.inject.Inject;


@ThreadScoped
public class LootModule implements Module {
  private static final int RADIUS = 70;

  private final EverquestSession session;
  private final LootManager lootManager;
  private final Set<Spawn> looted = new HashSet<>();
  private final Map<Spawn, Long> delayedCorpses = new HashMap<>();

  private long lootDelayUntil;
  private String lootPattern;
  private String alsoLootPattern;
  private boolean lootCorpses;
  private int lootDelay;

  @Inject
  public LootModule(final EverquestSession session) throws IOException {
    this.session = session;
    this.lootManager = new IniLootManager(session.getGlobalIni().getValue("Global", "Path"));

    // Section section = session.getIni().getSection("Loot");

    session.addUserCommand("loot", Pattern.compile("(on|off|also (.+)|only (.+)|delay ([0-9]+)|status)"), "(on|off|also <pattern>|only <pattern>|delay <seconds>|status)", new UserCommand() {
      @Override
      public void onCommand(Matcher matcher) {
        if(matcher.group(1).equals("on")) {
          lootCorpses = true;
          lootPattern = null;
          alsoLootPattern = null;
        }
        else if(matcher.group(1).equals("off")) {
          lootCorpses = false;
          lootPattern = null;
          alsoLootPattern = null;
        }
        else if(matcher.group(1).startsWith("also ")) {
          lootCorpses = true;
          lootPattern = null;
          alsoLootPattern = matcher.group(2);
        }
        else if(matcher.group(1).startsWith("only ")) {
          lootCorpses = true;
          lootPattern = matcher.group(3);
          alsoLootPattern = null;
        }
        else if(matcher.group(1).startsWith("delay ")) {
          lootDelay = Integer.parseInt(matcher.group(4));
        }

        if(lootPattern != null) {
          session.doCommand("/echo ==> Loot is on, only looting " + lootPattern + "; delay is " + lootDelay + " seconds.");
        }
        else if(alsoLootPattern != null) {
          session.doCommand("/echo ==> Loot is on, also looting " + alsoLootPattern + "; delay is " + lootDelay + " seconds.");
        }
        else {
          session.doCommand("/echo ==> Loot is " + (lootCorpses ? "on" : "off") + "; delay is " + lootDelay + " seconds.");
        }
      }
    });
  }

  @Override
  public List<Command> pulse() {
    Me me = session.getMe();

    if(lootCorpses && me.isAlive() && !me.isMoving() && !me.isCasting() && !me.inCombat() && lootDelayUntil < System.currentTimeMillis() && session.tryLockMovement()) {
      try {
        Set<Spawn> spawns = session.getSpawns();

        looted.retainAll(spawns);
        delayedCorpses.keySet().retainAll(spawns);

        float startX = me.getX();
        float startY = me.getY();
        boolean moved = false;

        // Check if there's a corpse needing looting
        for(Spawn spawn : spawns) {
          if(spawn.getType() == SpawnType.NPC_CORPSE && !looted.contains(spawn) && spawn.getDistance(startX, startY) < RADIUS && Math.abs(spawn.getZ() - me.getZ()) < 25) {
            Long delayUntil = delayedCorpses.get(spawn);
            Date timeOfDeath = spawn.getTimeOfDeath();

            if(lootDelay == 0 || timeOfDeath == null || (new Date().getTime() - timeOfDeath.getTime() > lootDelay * 1000)) {
              if(delayUntil == null || delayUntil < System.currentTimeMillis()) {
                if(!session.translate("${Cursor.ID}").equals("NULL")) {
                  session.echo("LOOT: Can't loot with an item on cursor");
                  session.doCommand("/autoinventory");
                  lootDelayUntil = System.currentTimeMillis() + 60000;
                  break;
                }

                session.echo("LOOT: Looting [ " + spawn.getName() + " ]");
                session.doCommand("/target id " + spawn.getId());
                session.delay(300);

                // Now we move to the corpse
                MoveUtils.moveTo(session, spawn.getX(), spawn.getY());
                moved = true;

                session.doCommand("/loot");

                if(session.delay(6000, "${Corpse.Open}")) {
                  try {
                    session.delay(1500);  // fixed delay here to allow for items to be populated

                    String itemCountStr = session.translate("${Corpse.Items}");

                    if(!itemCountStr.equals("NULL")) {
                      int itemCount = Integer.parseInt(itemCountStr);

                      for(int i = 0; i < itemCount; i++) {
                        final int itemNo = i + 1;

                        String itemName = session.translate("${Corpse.Item[" + itemNo + "].Name}");
                        String itemId = session.translate("${Corpse.Item[" + itemNo + "].ID}");
                        boolean noDrop = session.translate("${Corpse.Item[" + itemNo + "].NoDrop}").equals("TRUE");

                        if(lootPattern == null || itemName.matches("(?i)" + lootPattern)) {
                          LootType lootType = lootPattern != null || itemName.matches("(?i)" + alsoLootPattern) ? LootType.KEEP : lootManager.getLootType(itemName);

                          if(lootType == null) {
                            lootType = noDrop ? LootType.IGNORE : LootType.KEEP;
                            lootManager.addLoot(itemName, lootType);
                            session.echo("LOOT: Adding new loot: " + itemName);
                          }

                          // The Logic for looting items when inventory is full:

                          // /if (
                          //       (
                          //         ${Corpse.Item[${i}].Lore} &&
                          //         !${FindItem[${Corpse.Item[${i}]}].ID} ||
                          //         !${Corpse.Item[${i}].Lore}
                          //       ) &&
                          //       (
                          //         ${Me.FreeInventory} ||
                          //         (
                          //           ${FindItemCount[=${Corpse.Item[${i}].Name}]} &&
                          //           ${Corpse.Item[${i}].Stackable} &&
                          //           ${Corpse.Item[${i}].FreeStack}
                          //         )
                          //       ) &&
                          //       (
                          //         ${Ini[Loot.ini,"${Corpse.Item[${i}].Name.Left[1]}","${CurrentItem}"].Equal[Keep]} ||
                          //         ${Ini[Loot.ini,"${Corpse.Item[${i}].Name.Left[1]}","${CurrentItem}"].Equal[Sell]}
                          //       )
                          //     ) /call LootItem ${i} Keep right

                          if(lootType == LootType.DESTROY) {

                            // When destroying items, check if we can pick them up first:

                            boolean canLoot = session.evaluate(
                              "(!${FindItem[${Corpse.Item[" + itemNo + "]}].ID} && !${FindItemBank[${Corpse.Item[" + itemNo + "]}].ID}) || !${Corpse.Item[" + itemNo + "].Lore}"
                            );

                            if(!canLoot) {
                              continue;
                            }
                          }

                          if(lootType == LootType.KEEP) {
                            String result = session.translate(
                              "${Corpse.Item[" + itemNo + "].Tradeskills} ${Corpse.Item[" + itemNo + "].Size}," +
                              "${InvSlot[23].Item.Type};${InvSlot[23].Item.Container};${InvSlot[23].Item.Items};${InvSlot[23].Item.SizeCapacity}:" +
                              "${InvSlot[24].Item.Type};${InvSlot[24].Item.Container};${InvSlot[24].Item.Items};${InvSlot[24].Item.SizeCapacity}:" +
                              "${InvSlot[25].Item.Type};${InvSlot[25].Item.Container};${InvSlot[25].Item.Items};${InvSlot[25].Item.SizeCapacity}:" +
                              "${InvSlot[26].Item.Type};${InvSlot[26].Item.Container};${InvSlot[26].Item.Items};${InvSlot[26].Item.SizeCapacity}:" +
                              "${InvSlot[27].Item.Type};${InvSlot[27].Item.Container};${InvSlot[27].Item.Items};${InvSlot[27].Item.SizeCapacity}:" +
                              "${InvSlot[28].Item.Type};${InvSlot[28].Item.Container};${InvSlot[28].Item.Items};${InvSlot[28].Item.SizeCapacity}:" +
                              "${InvSlot[29].Item.Type};${InvSlot[29].Item.Container};${InvSlot[29].Item.Items};${InvSlot[29].Item.SizeCapacity}:" +
                              "${InvSlot[30].Item.Type};${InvSlot[30].Item.Container};${InvSlot[30].Item.Items};${InvSlot[30].Item.SizeCapacity}:" +
                              "${InvSlot[31].Item.Type};${InvSlot[31].Item.Container};${InvSlot[31].Item.Items};${InvSlot[31].Item.SizeCapacity}:" +
                              "${InvSlot[32].Item.Type};${InvSlot[32].Item.Container};${InvSlot[32].Item.Items};${InvSlot[32].Item.SizeCapacity}"
                            );

                            int availableSlots = 0;
                            String[] results = result.split(",");
                            String[] itemData = results[0].split(" ");

                            for(String bag : results[1].split(":")) {
                              String[] info = bag.split(";");

                              if(!info[1].equals("NULL") && !info[2].equals("NULL")) {
                                if(itemData[0].equals("TRUE") || !"*UnknownCombine58".equals(info[0])) {  // if tradeskill item or not a tradeskill specific bag:
                                  if(itemData[1].compareTo(info[3]) <= 0) {  // if size fits
                                    availableSlots += Integer.parseInt(info[1]) - Integer.parseInt(info[2]);
                                  }
                                }
                              }
                            }

                            // AvailableSlots now contains total slots this item would fit in.
                            session.echo("LOOT: Availabe slots for ${Corpse.Item[" + itemNo + "]}: " + availableSlots);

                            // When keeping items, we must make sure there's space.  Special rules are followed
                            // for stackable and lore items.
                            boolean canLoot = session.evaluate(
                              "((!${FindItem[${Corpse.Item[" + itemNo + "]}].ID} && !${FindItemBank[${Corpse.Item[" + itemNo + "]}].ID}) || !${Corpse.Item[" + itemNo + "].Lore}) && " +
                              "(" + availableSlots + " || (${FindItemCount[=${Corpse.Item[" + itemNo + "].Name}]} && ${Corpse.Item[" + itemNo + "].Stackable} && ${Corpse.Item[" + itemNo + "].FreeStack}))"
                            );

                            if(!canLoot) {
                              session.echo("LOOT: Left ${Corpse.Item[" + itemNo + "]} on corpse, no more space");
                              continue;
                            }
                          }

                          if(lootType == LootType.KEEP || lootType == LootType.DESTROY) {
                            session.doCommand("/nomodkey /shift /itemnotify Loot" + itemNo + " " + (lootType == LootType.KEEP ? "rightmouseup" : "leftmouseup"));

                            // Confirm no drop box if required
                            if(noDrop) {
                              if(session.delay(2500, "${Window[ConfirmationDialogBox].Open}")) {
                                session.doCommand("/nomodkey /notify ConfirmationDialogBox Yes_Button leftmouseup");
                              }
                            }

                            // Wait until item looted
                            session.delay(2500, "!${Corpse.Item[" + itemNo + "].ID}");

                            // Destroy item on cursor if needed
                            if(lootType == LootType.DESTROY) {
                              if(session.delay(2500, "${Cursor.ID} == " + itemId)) {
                                session.doCommand("/destroy");
                              }
                            }
                          }
                          else {
                            session.echo("LOOT: Left on corpse: " + itemName);
                          }
                        }
                      }

                      looted.add(spawn);
                    }
                  }
                  finally {
                    do {
                      session.doCommand("/nomodkey /notify LootWnd LW_DoneButton leftmouseup");
                    } while(!session.delay(500, "!${Corpse.Open}"));
                  }

                  session.delay(500);
                }

                if(!looted.contains(spawn)) {
                  session.echo("LOOT: Unable to loot [ " + spawn.getName() + " ].  Retrying in 2 minutes.");

                  delayedCorpses.put(spawn, System.currentTimeMillis() + 150 * 1000);
                }
              }
            }
          }
        }

        if(moved) {
          // Move back
          MoveUtils.moveBackwardsTo(session, startX, startY);
        }
      }
      finally {
        session.unlockMovement();
      }
    }

    return null;
  }

  public int getPriority() {
    return 0;
  }

  @Override
  public boolean isLowLatency() {
    return false;
  }
}
