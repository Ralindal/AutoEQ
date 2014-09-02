package autoeq.modules.loot;

import java.io.IOException;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import autoeq.Item;
import autoeq.ThreadScoped;
import autoeq.commandline.CommandLineParser;
import autoeq.eq.ChatListener;
import autoeq.eq.Command;
import autoeq.eq.EverquestSession;
import autoeq.eq.Me;
import autoeq.eq.Module;
import autoeq.eq.RollingAverage;
import autoeq.eq.Spawn;
import autoeq.eq.SpawnType;
import autoeq.eq.UserCommand;
import autoeq.modules.loot.LootConf.Mode;
import autoeq.modules.pull.MoveUtils;

import com.google.inject.Inject;

@ThreadScoped
public class LootModule implements Module {
  private static final int RADIUS = 70;

  private final EverquestSession session;
  private final LootManager lootManager;
  private final Set<Spawn> looted = new HashSet<>();
  private final Map<Spawn, Long> delayedCorpses = new HashMap<>();
  private final Map<Spawn, Integer> delayedCorpsesAttempts = new HashMap<>();

  private LootConf conf = new LootConf();

  private long lootDelayUntil;
  private boolean lootCorpses;
  private final Map<Integer, TreeSet<Integer>> slotToHPMap = new HashMap<>();

  private final RollingAverage platinumCorpseValues = new RollingAverage(65 * 60 * 1000);
  private final RollingAverage platinumLootValues = new RollingAverage(65 * 60 * 1000);

  @Inject
  public LootModule(final EverquestSession session) throws IOException {
    this.session = session;
    this.lootManager = new IniLootManager(session.getGlobalIni().getValue("Global", "Path"));

    session.addChatListener(new ChatListener() {
      private final Pattern PATTERN = Pattern.compile("You receive (.+) from the corpse\\.");

      @Override
      public void match(Matcher matcher) {
        platinumCorpseValues.add(((double)extractCopperValue(matcher.group(1))) / 1000);
      }

      @Override
      public Pattern getFilter() {
        return PATTERN;
      }
    });

    session.addChatListener(new ChatListener() {
      private final Pattern PATTERN = Pattern.compile("[A-Z].+ tells you, 'I'll give you ((?:[0-9]+ (?:platinum|gold|silver|copper) )+)per (.+)'");

      @Override
      public void match(Matcher matcher) {
        String itemName = matcher.group(2);
        LootType lootType = lootManager.getLootType(itemName);
        if(lootType != null) {
          lootManager.addLoot(itemName, lootType, extractCopperValue(matcher.group(1)));
        }
      }

      @Override
      public Pattern getFilter() {
        return PATTERN;
      }
    });

    session.addUserCommand("loot", Pattern.compile(".+"), CommandLineParser.getHelpString(LootConf.class), new UserCommand() {
      @Override
      public void onCommand(Matcher matcher) {
        LootConf newConf = new LootConf(conf);

        CommandLineParser.parse(newConf, matcher.group(0));

        try {
          Pattern.compile(newConf.getPattern());
        }
        catch(PatternSyntaxException e) {
          session.echo("==> Bad pattern, ignorning: " + newConf.getPattern());
          newConf.setPattern("off");
        }

        conf = newConf;

        lootCorpses = !conf.getPattern().equals("off") || conf.getNormal() == Mode.ON || conf.getUpgrades() == Mode.ON || conf.getLore() == Mode.ON;

        session.echo(
          String.format("==> Looting is %s.%s%s%s Delay is %ds.%s",
            lootCorpses ? "on" : "off",
            conf.getPattern().equals("off") ? "" : " Specifically looting " + conf.getPattern() + (conf.isUnique() ? ", no duplicates." : ", duplicates allowed."),
            conf.getUpgrades() == Mode.OFF ? "" : " Looting upgrades.",
            conf.getLore() == Mode.OFF ? "" : " Looting lore items.",
            conf.getDelay(),
            conf.getMaxStack() > 0 ? " Stack max. is " + conf.getMaxStack() + "." : ""
          )
        );
      }
    });

    for(int i = 0; i <= 32; i++) {
      addItem(session.translate("${Me.Inventory[" + i + "].WornSlot[1]}^${Me.Inventory[" + i + "].HP}^${Me.Inventory[" + i + "].ID}^${Me.Inventory[" + i + "].Name}^${Me.Inventory[" + i + "].NoDrop}^${Me.Inventory[" + i + "].Lore}^${Me.Inventory[" + i + "].Attuneable}"));
    }

    for(int i = 23; i <= 32; i++) {
      for(int slotStart = 0; slotStart < 40; slotStart += 8) {
        String translateString = "";

        for(int slot = slotStart; slot < slotStart + 8; slot++) {
          if(!translateString.isEmpty()) {
            translateString += ";";
          }

          for(String fieldName : new String[] {"WornSlot[1]", "HP", "ID", "Name", "NoDrop", "Lore", "Attuneable"}) {
            translateString += "${Me.Inventory[" + i + "].Item[" + slot + "]." + fieldName + "}^";
          }

          translateString = translateString.substring(0, translateString.length() - 1);
        }

        for(String itemInfo : session.translate(translateString).split(";")) {
          addItem(itemInfo);
        }
      }
    }
  }

  private static final Pattern COIN_VALUE_PATTERN = Pattern.compile("([0-9]+) (platinum|gold|silver|copper)");
  private static final Map<String, Long> COIN_MULTIPLIERS = new HashMap<>();

  static {
    COIN_MULTIPLIERS.put("platinum", 1000L);
    COIN_MULTIPLIERS.put("gold", 100L);
    COIN_MULTIPLIERS.put("silver", 10L);
    COIN_MULTIPLIERS.put("copper", 1L);
  }

  private static long extractCopperValue(String text) {
    Matcher matcher = COIN_VALUE_PATTERN.matcher(text);
    long copperValue = 0;

    while(matcher.find()) {
      long value = Long.valueOf(matcher.group(1));
      String coinType = matcher.group(2);

      copperValue += value * COIN_MULTIPLIERS.get(coinType);
    }

    return copperValue;
  }

  private void addItem(String itemInfo) {
    String[] results = itemInfo.split("\\^");

    if(!results[0].equals("NULL") && !results[1].equals("NULL")) {
      int slot = Integer.parseInt(results[0]);
      int hp = Integer.parseInt(results[1]);

      TreeSet<Integer> hps = slotToHPMap.get(slot);

      if(hps == null) {
        hps = new TreeSet<>();
        slotToHPMap.put(slot, hps);
      }

      hps.add(hp);
    }

    if(!results[2].equals("NULL")) {
      Item item = new Item(Integer.parseInt(results[2]), results[3], Integer.parseInt(results[1]), results[4].equals("TRUE"), results[5].equals("TRUE"), results[6].equals("TRUE"));

      session.getItemDAO().addItem(item);
    }
  }

  @Override
  public List<Command> pulse() {
    Me me = session.getMe();

    if(lootCorpses && me.isAlive() && !me.isMoving() && (!me.isCasting() || me.isBard()) && me.getExtendedTargetCount() == 0 && lootDelayUntil < System.currentTimeMillis() && session.tryLockMovement()) {
      try {
        Set<Spawn> spawns = session.getSpawns();

        looted.retainAll(spawns);
        delayedCorpses.keySet().retainAll(spawns);
        delayedCorpsesAttempts.keySet().retainAll(spawns);

        float startX = me.getX();
        float startY = me.getY();
        boolean moved = false;

        // Check if there's a corpse needing looting
        for(Spawn spawn : spawns) {
          Spawn currentSpawn = session.getSpawn(spawn.getId());

          /*
           * Check if spawn still exists, is a corpse, was not recently looted and is nearby.
           */

          if(currentSpawn != null && currentSpawn.equals(spawn) && spawn.getType() == SpawnType.NPC_CORPSE && !looted.contains(spawn) && spawn.getDistance(startX, startY) < RADIUS && Math.abs(spawn.getZ() - me.getZ()) < 25) {
            Long delayUntil = delayedCorpses.get(spawn);
            Date timeOfDeath = spawn.getTimeOfDeath();

            if(conf.getDelay() == 0 || timeOfDeath == null || (new Date().getTime() - timeOfDeath.getTime() > conf.getDelay() * 1000)) {
              if(delayUntil == null || delayUntil < System.currentTimeMillis()) {
                if(!session.translate("${Cursor.ID}").equals("NULL")) {
                  session.echo("LOOT: Can't loot with an item on cursor");
                  session.doCommand("/autoinventory");
                  lootDelayUntil = System.currentTimeMillis() + 60000;
                  break;
                }

                session.delayUntilUpdate();  // Give chance to update before looting each corpse
                session.echo(String.format("LOOT: Looting [ %s ] -- %.3f pp/h, %.3f pp/corpse, %.3f sell loot pp/h",
                  spawn.getName(),
                  platinumCorpseValues.computeAverageOverTime(60 * 60 * 1000),
                  platinumCorpseValues.computeAveragePerEntry(60 * 60 * 1000),
                  platinumLootValues.computeAverageOverTime(60 * 60 * 1000)
                ));
                session.doCommand("/target id " + spawn.getId());
                session.delay(300);

                // Now we move to the corpse
                MoveUtils.moveTo(session, spawn.getX(), spawn.getY(), null);
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
                        int itemsInStack = Integer.parseInt(session.translate("${Corpse.Item[" + itemNo + "].Stack}"));

                        boolean noDrop = session.translate("${Corpse.Item[" + itemNo + "].NoDrop}").equals("TRUE");
                        boolean lore = session.translate("${Corpse.Item[" + itemNo + "].Lore}").equals("TRUE");
                        boolean loot = !conf.getPattern().equals("off") && itemName.matches("(?i)" + conf.getPattern());

                        if(conf.getLore() == Mode.ON && lore) {
                          loot = true;
                        }

                        if(conf.getUpgrades() == Mode.ON && noDrop) {
                          String hp = session.translate("${Corpse.Item[" + itemNo + "].HP}");
                          String type = session.translate("${Corpse.Item[" + itemNo + "].Type}");
                          String wornSlot = session.translate("${Corpse.Item[" + itemNo + "].WornSlot[1]}");
                          String classes = session.translate("${Corpse.Item[" + itemNo + "].Class[1]},${Corpse.Item[" + itemNo + "].Class[2]},${Corpse.Item[" + itemNo + "].Class[3]},${Corpse.Item[" + itemNo + "].Class[4]},${Corpse.Item[" + itemNo + "].Class[5]},${Corpse.Item[" + itemNo + "].Class[6]},${Corpse.Item[" + itemNo + "].Class[7]},${Corpse.Item[" + itemNo + "].Class[8]},${Corpse.Item[" + itemNo + "].Class[9]},${Corpse.Item[" + itemNo + "].Class[10]},${Corpse.Item[" + itemNo + "].Class[11]},${Corpse.Item[" + itemNo + "].Class[12]},${Corpse.Item[" + itemNo + "].Class[13]},${Corpse.Item[" + itemNo + "].Class[14]},${Corpse.Item[" + itemNo + "].Class[15]},${Corpse.Item[" + itemNo + "].Class[16]}");

                          if(classes.contains(me.getClassLongName())) {  // Useable by me?
                            if(type.equals("Augmentation")) {
                              session.echo("LOOT: '" + itemName + "' is an augmentation... looting.");
                              loot = true;
                            }
                            else if(!hp.equals("NULL") && !wornSlot.equals("NULL") && !hp.equals("NULL")) {
                              int slot = Integer.parseInt(wornSlot);
                              int itemHP = Integer.parseInt(hp);
                              TreeSet<Integer> hps = slotToHPMap.get(slot);

                              if(!hps.isEmpty()) {
                                int bestHP = hps.last();

                                if(slot == 3 || slot == 4 || slot == 9 || slot == 10 || slot == 15 || slot == 16) {
                                  // 1,4  9,10  15,16
                                  // 13,14 (mainhand offhand)

                                  if(hps.size() > 1) {
                                    bestHP = hps.lower(bestHP);
                                  }
                                  else {
                                    bestHP = 0;
                                  }
                                }

                                if(bestHP < itemHP) {
                                  session.echo("LOOT: '" + itemName + "' looks better... looting.");
                                  loot = true;
                                  hps.add(itemHP);
                                }
                              }
                              else {
                                // Nothing in that slot, so loot
                                session.echo("LOOT: '" + itemName + "' fits in an empty slot... looting.");
                                loot = true;
                                hps = new TreeSet<>();
                                hps.add(itemHP);
                                slotToHPMap.put(slot, hps);
                              }
                            }
                          }
                        }

                        if(conf.getNormal() == Mode.ON || loot) {
//                        if(conf.getPattern().equals("off") || itemName.matches("(?i)" + conf.getPattern())) {
                          LootType lootType = loot ? LootType.KEEP : lootManager.getLootType(itemName);

                          if(lootType == null) {
                            lootType = noDrop ? LootType.IGNORE : LootType.KEEP;
                            lootManager.addLoot(itemName, lootType, null);
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
                            // session.echo("LOOT: Availabe slots for ${Corpse.Item[" + itemNo + "]}: " + availableSlots);

                            int stackCount = Integer.parseInt(session.translate("${FindItemCount[=${Corpse.Item[" + itemNo + "].Name}]}"));
                            int maxStackSize = conf.getMaxStack() > 0 ? conf.getMaxStack() : 10000;

                            if(stackCount < maxStackSize) {
                              if(stackCount == 0 || !conf.isUnique() || !loot) {
                                // When keeping items, we must make sure there's space.  Special rules are followed
                                // for stackable and lore items.
                                boolean canLoot = session.evaluate(
                                  "((!${FindItem[${Corpse.Item[" + itemNo + "]}].ID} && !${FindItemBank[${Corpse.Item[" + itemNo + "]}].ID}) || !${Corpse.Item[" + itemNo + "].Lore}) && " +
                                  "(" + availableSlots + " || (${FindItemCount[=${Corpse.Item[" + itemNo + "].Name}]} && ${Corpse.Item[" + itemNo + "].Stackable} && ${Corpse.Item[" + itemNo + "].FreeStack} >= ${Corpse.Item[" + itemNo + "].Stack}))"
                                );

                                if(!canLoot) {
                                  session.echo("LOOT: Left ${Corpse.Item[" + itemNo + "]} on corpse, no more space");
                                  continue;
                                }
                              }
                              else {
                                session.echo("LOOT: Left ${Corpse.Item[" + itemNo + "]} on corpse, duplicate");
                                continue;
                              }
                            }
                            else {
                              session.echo("LOOT: Left ${Corpse.Item[" + itemNo + "]} on corpse, stack size limit reached");
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

                            if(lootType == LootType.KEEP) {
                              Long lootValue = lootManager.getLootValue(itemName);

                              if(lootValue != null) {
                                lootValue *= itemsInStack;
                                session.echo(String.format("LOOT: Looted '%s' %swith value %7.3f", itemName, itemsInStack > 1 ? "(" + itemsInStack + ") " : "", ((double)lootValue) / 1000.0));
                                platinumLootValues.add(((double)lootValue) / 1000.0);
                              }
                              else {
                                session.echo(String.format("LOOT: Looted '%s' of unknown value", itemName));
                              }
                            }

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
                  Long millis = delayedCorpses.get(spawn);
                  Integer attempts = delayedCorpsesAttempts.get(spawn);

                  if(attempts == null) {
                    attempts = 2;
                  }
                  else {
                    attempts++;
                  }

                  long retryTime = (long)((Math.pow(2, attempts) + (Math.random() - 0.5) * Math.pow(2, attempts - 1)) * 1000);
                  millis = System.currentTimeMillis() + retryTime;

                  session.echo(String.format("LOOT: Unable to loot [ %s ].  Retrying in %.1f seconds.", spawn.getName(), retryTime / 1000.0));

                  delayedCorpses.put(spawn, millis);
                  delayedCorpsesAttempts.put(spawn, attempts);
                }
              }
            }
          }
        }

        if(moved) {
          // Move back
          MoveUtils.moveBackwardsTo(session, startX, startY, null);
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
  public int getBurstCount() {
    return 8;
  }
}
