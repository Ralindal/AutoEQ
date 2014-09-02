package autoeq.eq;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.Socket;
import java.net.UnknownHostException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Semaphore;
import java.util.logging.FileHandler;
import java.util.logging.Formatter;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import autoeq.BotUpdateEvent;
import autoeq.EventHandler;
import autoeq.EverquestModule;
import autoeq.ExpressionEvaluator;
import autoeq.ExternalCommandEvent;
import autoeq.Item;
import autoeq.ItemDAO;
import autoeq.SessionEndedException;
import autoeq.SpellData;
import autoeq.effects.Effect;
import autoeq.expr.Parser;
import autoeq.expr.SyntaxException;
import autoeq.ini.Ini2;
import autoeq.ini.Section;
import autoeq.modules.pull.MoveModule;

import com.google.inject.Guice;
import com.google.inject.Injector;

public class DefaultEverquestSession implements EverquestSession {
  private final Semaphore castSemaphore = new Semaphore(1);
  private final Semaphore movementSemaphore = new Semaphore(1);
  private final Semaphore targetSemaphore = new Semaphore(1);

  private final MyThread thread;
  private final List<Module> modules = new ArrayList<>();
  private final String sessionName;
  private final Map<Integer, SpellData> rawSpellData;
  private final File globalIniFile;

  private final Map<Integer, Spawn> spawns = new HashMap<>();
  private final Map<Integer, Spell> spells = new HashMap<>();
  private final Set<String> groupMemberNames = new HashSet<>();
  private final Set<Spawn> groupMemberSpawns = new HashSet<>();
  private final Set<String> botNames = new HashSet<>();
  private final Map<String, Profile> profiles = new HashMap<>();
  private final Set<ProfileSet> profileSets = new LinkedHashSet<>();
  private final Map<Integer, ClickableItem> clickableItems = new HashMap<>();

  private Ini2 globalIni;
  private Ini2 ini;
  private File allIniFile;
  private File classIniFile;
  private File iniFile;
  private long globalIniLastModified;
  private long allIniLastModified;
  private long classIniLastModified;
  private long iniLastModified;
  private long castLockEndMillis = Long.MIN_VALUE;

  private String charName;
  private String alternateName;
  private int charId;
  private int zoneId;
  private boolean zoning = true;
  private Logger logger;

  private Module activeModule;
  private volatile boolean ioThreadActive = true;
  private boolean debug = false;
  private final int port;
  private final ItemDAO itemDAO;
  private final CharacterDAO characterDAO = new CharacterDAO(this);
  private final EffectsDAO effectsDAO = new EffectsDAO(this, characterDAO);

  public DefaultEverquestSession(ItemDAO itemDAO, Map<Integer, SpellData> rawSpellData, File globalIniFile, String host, int port, String username, String password) throws UnknownHostException, IOException, InterruptedException {
    this.itemDAO = itemDAO;
    this.rawSpellData = rawSpellData;
    this.globalIniFile = globalIniFile;
    this.port = port;

    @SuppressWarnings("resource")
    Socket socket = new Socket(host, port);

    socket.setSoTimeout(1000);

    BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
    PrintWriter writer = new PrintWriter(new OutputStreamWriter(socket.getOutputStream()));

    while(!(reader.read() == ':')) {
    }

    Thread.sleep(300);

    writer.println(username);
    writer.flush();

    while(!(reader.read() == ':')) {
    }

    Thread.sleep(300);

    writer.println(password);
    writer.flush();

    if(!reader.readLine().startsWith("Succe")) {
      throw new RuntimeException("Unable to connect, no welcome message");
    }

    socket.setSoTimeout(0);

    reloadGlobalIni();

    thread = new MyThread(reader, writer);
    thread.start();

    //onFinishedZoning();
    sessionName = "EverquestSession(" + port + ")";
  }

  @Override
  public int getPort() {
    return port;
  }

  public void addModule(Module module) {
    modules.add(module);
  }

  private Lock<Module> moveLock;

  private Lock<Module> getMoveLock() {
    return moveLock;
  }

  @Override
  public void lockMovement() {
    getMoveLock().lock(activeModule);
  }

  @Override
  public boolean tryLockMovement() {
    return getMoveLock().tryLock(activeModule);
  }

  @Override
  public void unlockMovement() {
    getMoveLock().unlock(activeModule);
  }

  @Override
  public Set<String> getGroupMemberNames() {
    return new HashSet<>(groupMemberNames);
  }

  /**
   * @return a set of spawns with all group members in this zone
   */
  @Override
  public Set<Spawn> getGroupMembers() {
    return groupMemberSpawns;
  }

  /**
   * @return a set of spawns with all bots in this zone
   */
  @Override
  public Set<Spawn> getBots() {
    HashSet<Spawn> bots = new HashSet<>();

    for(String name : botNames) {
      Spawn spawn = getSpawn(name);

      if(spawn != null) {
        bots.add(spawn);
      }
    }

    return bots;
  }

  public void setBotNames(Set<String> names) {
    botNames.clear();
    botNames.addAll(names);
  }

  @Override
  public Set<String> getBotNames() {
    return botNames;
  }

  @Override
  public int getZoneId() {
    return zoneId;
  }

  public boolean isCombatZone() {
    if(zoneId == 344 || zoneId == 345 || zoneId == 202 || zoneId == 203) {
      return false;
    }

    return true;
  }

  @Override
  public void setCastLockOut(long ms) {
    castLockEndMillis = System.currentTimeMillis() + ms;
  }

  private final Map<String, UserCommandWrapper> userCommands = new HashMap<>();

  @Override
  public void addUserCommand(String name, Pattern parameters, String helpText, UserCommand command) {
    userCommands.put(name, new UserCommandWrapper(command, parameters, helpText));
  }

  private final List<String> incomingCommands = new ArrayList<>();
  private int dataBurstCounter;

  @Override
  public void pulse(Collection<BotUpdateEvent> lastBotUpdateEvents) {
    if(!ioThreadActive) {
      throw new SessionEndedException();
    }

    try {
      long startTime = System.currentTimeMillis();
      long burstProcessingTime = 0;
      long modulesProcessingTime = 0;

      synchronized(this) {
        if(!zoning) {

          /*
           * Update bot information for this session
           */

          Set<String> botNames = new HashSet<>();

          for(BotUpdateEvent botUpdateEvent : lastBotUpdateEvents) {
            botNames.add(botUpdateEvent.getName());
            botUpdateEvent.applyEvent(this);
          }

          setBotNames(botNames);
        }

        /*
         * Process chat lines
         */

        synchronized(incomingCommands) {
          while(!incomingCommands.isEmpty()) {
            String cmd = incomingCommands.remove(0);

            if(cmd.startsWith("/")) {
              doCommand(cmd);
            }
            else if(commandHandler != null && cmd.matches("[a-zA-Z]+:.+")) {
              commandHandler.handle(new ExternalCommandEvent(cmd));
            }
            else {
              for(String s : userCommands.keySet()) {
                if(cmd.equals(s) || cmd.startsWith(s + " ")) {
                  UserCommandWrapper userCommandWrapper = userCommands.get(s);
                  Matcher m = userCommandWrapper.pattern.matcher(cmd.substring(s.length()).trim());

                  if(m.matches()) {
                    userCommandWrapper.userCommand.onCommand(m);
                  }
                  else {
                    doCommand("/echo JB: syntax error, use: " + s + " " + userCommandWrapper.helpText);
                  }

                  break;
                }
              }
            }
          }
        }

        /*
         * Process data burst & modules
         */

        burstProcessingTime = System.currentTimeMillis();

        DataburstProcessResult result = processDataBursts();

        burstProcessingTime = System.currentTimeMillis() - burstProcessingTime;

        if(result != DataburstProcessResult.NONE) {
          dataBurstCounter++;

          if(result == DataburstProcessResult.FULL && getMeSpawn() != null) {
            if((iniFile.exists() && iniLastModified != iniFile.lastModified()) || (classIniFile.exists() && classIniLastModified != classIniFile.lastModified()) || allIniLastModified != allIniFile.lastModified()) {
              reloadIni();
              reloadModules();
            }
            if(globalIniFile.exists() && globalIniLastModified != globalIniFile.lastModified()) {
              reloadGlobalIni();
            }
          }

          if(!zoning && getMeSpawn() != null) {
            List<Command> commands = new ArrayList<>();

            getMe().unlockAllSpellSlots();

            /*
             * Run modules
             */

            modulesProcessingTime = System.currentTimeMillis();

            for(Module module : modules) {
              activeModule = module;

              if(dataBurstCounter % module.getBurstCount() == 0) {
                List<Command> newCommands = module.pulse();

                if(newCommands != null) {
                  commands.addAll(newCommands);
                }
              }
            }

            modulesProcessingTime = System.currentTimeMillis() - modulesProcessingTime;

            /*
             * Sorts the commands on priority, but respects the original order of the Commands that
             * have the same priorities.
             */

            Collections.sort(commands, new Comparator<Command>() {
              @Override
              public int compare(Command o1, Command o2) {
                double d = o1.getPriority() - o2.getPriority();

                if(d < 0) {
                  return -1;
                }
                else if(d > 0) {
                  return 1;
                }

                return 0;
              }
            });

            // This locks out all commands, since commands can be anything this might be a bit too much.
            if(castLockEndMillis < System.currentTimeMillis()) {
    //          for(Command command : commands) {
    //            log(command.getPriority() + " : " + command);
    //          }

              for(Command command : commands) {
                if(command.execute(this)) {
                  break;
                }
              }
            }
          }
        }
      }

      long time = System.currentTimeMillis() - startTime;

//      if(time > 200) {
//        log("WARNING: Pulse took " + time + " ms; dataBurstCounter = " + dataBurstCounter + "; burstTime = " + burstProcessingTime + " ms; modulesTime = " + modulesProcessingTime + " ms");
//      }
    }
    catch(ZoningException e) {
      try {
        if(!zoning) {
          System.out.println("ZONING DETECTED");
        }

        synchronized(this) {
          spawns.clear();
          zoneId = -1;
          zoning = true;
        }

        Thread.sleep(2000);
      }
      catch(InterruptedException e2) {
        throw new RuntimeException(e2);
      }
    }
  }

  private static final Pattern BOT_PATTERN = Pattern.compile("#B ([A-Za-z]+) ([-0-9]+) ([-0-9]+) ([-0-9]+) ([-0-9]+) ([-0-9]+) ([-0-9]+) B\\[([-0-9 ]*)\\] D\\[([-0-9 ]*)\\] SB\\[([-0-9 ]*)\\]");
  private static final Pattern ME_PATTERN = Pattern.compile("#M ([-0-9]+) ([-0-9]+) ([A-Za-z]+) .+");

  private void handleNormalChatLine(String line) {
    if(line.startsWith("[MQ2] jb-")) {
      synchronized(incomingCommands) {
        incomingCommands.addAll(Arrays.asList(line.substring(10).split(";")));
      }
    }

    for(ChatListener listener : chatListeners) {
      Matcher matcher = listener.getFilter().matcher(line);

      if(matcher.matches()) {
        listener.match(matcher);
      }
    }
  }

  private List<String> partialDataBurst = new ArrayList<>();

  static class DataBurst {
    private final long time;
    private final List<String> data;

    public DataBurst(long time, List<String> data) {
      this.time = time;
      this.data = data;
    }

    public List<String> getData() {
      return data;
    }

    public long getTime() {
      return time;
    }
  }

  private DataBurst gatherDataBurst() {
    synchronized(chatLines) {
      while(!chatLines.isEmpty()) {
        String line = chatLines.removeFirst();

        if(line.startsWith("#")) {
          partialDataBurst.add(line);

          if(line.startsWith("## ")) {
            List<String> dataBurst = partialDataBurst;

            partialDataBurst = new ArrayList<>();

            return new DataBurst(Long.parseLong(line.substring(3)), dataBurst);
          }

          if(line.equals("#Z")) {
            lastZoneTime = System.currentTimeMillis();
            chatLineCounter = 0;
            burstCounter = 0;
          }
        }
        else {
          handleNormalChatLine(line);
        }
      }

      return null;
    }
  }

  private enum DataburstProcessResult {
    NONE, PARTIAL, FULL;
  }

  private DataburstProcessResult processDataBursts() {
    DataBurst dataBurst;
    boolean fullUpdate = false;
    boolean zoning = false;
    boolean processedAtleastOneBurst = false;
    long timeSent = 0;

    while((dataBurst = gatherDataBurst()) != null) {
      boolean containsAllSpawns = false;

      zoning = false;

      if(dataBurst.getData().size() < 1) {
        throw new RuntimeException("Assertion failed: dataBurst.size() = " + dataBurst.getData().size());
      }

      String firstLine = dataBurst.getData().get(0);

      if(firstLine.equals("#Z")) {
        zoning = true;
      }
      else if(firstLine.startsWith("#T ")) {
        processedAtleastOneBurst = true;
        timeSent = Long.parseLong(firstLine.substring(3));
      }
      else {
        logErr("Discarding a databurst of " + dataBurst.getData().size() + " lines, starts with: " + dataBurst.getData().get(0));
        continue;
      }

      if(!zoning) {
        Set<Spawn> newGroupMemberSpawns = new HashSet<>();

        //botNames.clear();
        groupMemberNames.clear();

        for(String line : dataBurst.getData()) {
          if(line.startsWith("#B ")) {
            Matcher matcher = BOT_PATTERN.matcher(line);

            if(matcher.matches()) {
              //botNames.add(matcher.group(1));
            }
          }
          else if(line.equals("#F")) {
            fullUpdate = true;
            containsAllSpawns = true;
          }
          else if(line.startsWith("#G ")) {
            groupMemberNames.addAll(Arrays.asList(line.substring(3).replaceAll("([0-9]+|'s corpse)", "").split(" ")));
            // groupMemberNames.add(line.substring(3).replaceAll("[0-9]+", ""));
          }
          else if(line.startsWith("#A")) {
            String[] results = line.substring(2).split(";");

            if(Integer.parseInt(results[0]) == expressionsVersion) {
              int i = 1;

              for(List<ExpressionListener> listeners : expressionListeners.values()) {
                for(ExpressionListener listener : listeners) {
                  listener.stateUpdated(results[i]);
                }
                i++;
              }
            }
          }
          else if(line.startsWith("#TIMERS ")) {
            String[] results = line.substring(8).split(" ");

            if(results.length == timerListeners.size()) {
              int i = 0;

              for(List<ExpressionListener> listeners : timerListeners.values()) {
                for(ExpressionListener listener : listeners) {
                  listener.stateUpdated(results[i]);
                }
                i++;
              }
            }
          }
          else if(line.startsWith("#CLICKS ")) {  // Gives all available clickey items, space seperated: <item-id>:<cast-time>:<ticks>
            String[] results = line.substring(8).split(" ");

            //System.err.println(line);

            clickableItems.clear();

            for(String result : results) {
              String[] components = result.split(":");

              if(getItemDAO().getItem(Integer.parseInt(components[0])) == null) {
                System.out.println("--WARNING, not found item with id: " + Integer.parseInt(components[0]));
              }

              clickableItems.put(Integer.parseInt(components[0]), new ClickableItem(Integer.parseInt(components[0]), Integer.parseInt(components[1]), Integer.parseInt(components[2]), Integer.parseInt(components[3])));
            }
          }
        }

      //    System.out.println("Processing data burst");
        Set<Integer> foundSpawnIDs = new HashSet<>();
        int spawnCountCheck = 0;
        String previousLine = "(empty)";
        String lastSpawnLine = "(empty)";

        for(String line : dataBurst.getData()) {
          if(line.startsWith("#M ")) {
            Matcher matcher = ME_PATTERN.matcher(line);

            if(matcher.matches()) {
              int zoneId = Integer.parseInt(matcher.group(1));
              int charId = Integer.parseInt(matcher.group(2));
              String charName = matcher.group(3);

              if(charId != this.charId) {
                System.out.println("SESSION: Main Character ID changed: " + this.charId + " -> " + charId);
                this.charId = charId;
              }
              if(zoneId != this.zoneId || !charName.equals(this.charName)) {
                boolean characterChanged = false;

                if(!charName.equals(this.charName)) {
                  System.out.println("SESSION: Changed Characters: " + this.charName + " -> " + charName);
                  characterChanged = true;
                }
                if(zoneId != this.zoneId) {
                  System.out.println("SESSION: Zoned: " + this.zoneId + " -> " + zoneId);
                }
                this.charName = charName;
                this.zoneId = zoneId;
                onFinishedZoning(characterChanged);
              }

              ((Me)getSpawnInternal(charId)).updateMe(line, timeSent);

              foundSpawnIDs.add(charId);
      //        System.out.println("CHAT: " + line);
            }
          }
          else if(line.startsWith("#S-")) {
            int firstSpace = line.indexOf(' ');
            int secondSpace = line.indexOf(' ', firstSpace + 1);
            int spawnID = Integer.parseInt(line.substring(firstSpace + 1, secondSpace), 16);

            if(!line.substring(3, firstSpace).equals(Integer.toHexString(spawnCountCheck++).toUpperCase())) {
              System.err.println("Missing spawn line, #S-" + Integer.toHexString(spawnCountCheck - 1) + "; got: " + line);
              System.err.println("lastspawnline was: " + lastSpawnLine);
              System.err.println("previous line was: " + previousLine);
              spawnCountCheck++;
            }

            foundSpawnIDs.add(spawnID);
            Spawn spawn = getSpawnInternal(spawnID);

            spawn.updateSpawn(line);

            if(groupMemberNames.contains(spawn.getName()) || groupMemberNames.contains(spawn.getName() + "'s corpse") || groupMemberNames.contains(spawn.getName().replaceAll("'s corpse", ""))) {
              newGroupMemberSpawns.add(spawn);
            }

            lastSpawnLine = line;
          }

          previousLine = line;
        }

        groupMemberNames.add(getMeSpawn().getName());
        newGroupMemberSpawns.add(getMeSpawn());

        groupMemberSpawns.clear();
        groupMemberSpawns.addAll(newGroupMemberSpawns);

        if(containsAllSpawns) {
          spawns.keySet().retainAll(foundSpawnIDs);
        }

        for(Spawn spawn : spawns.values()) {
          spawn.updateTTL();
        }

  //      for(String line : dataBurst) {
  //        if(line.startsWith("#L")) {
  //          Me me = getMe();
  //
  //          if(me != null) {
  //            me.updateMeTypeL(line);
  //          }
  //        }
  //      }

        if(castResultMonitor != null) {
          castResultMonitor.pulse();
        }
      }
    }

    if(processedAtleastOneBurst) {
      this.zoning = zoning;

      if(!zoning) {
        Me me = getMe();

        if(me != null && botUpdateHandler != null) {
          Map<Integer, Long> spellDurations = new HashMap<>();

          for(Spell spell : me.getSpellEffects()) {
            SpellEffectManager manager = me.getSpellEffectManager(spell);

            long timeLeft = manager.getMillisLeft();

            if(timeLeft > 0) {
              spellDurations.put(spell.getId(), timeLeft);
            }
          }

          botUpdateHandler.handle(new BotUpdateEvent(getZoneId(), me));

          Spawn pet = me.getPet();

          if(pet != null) {
            // TODO not updating pets atm because NPC targets donot get buffs updated through target window.. causes chain cast of chanter haste for example on a mage pet
//            botUpdateHandler.handle(new BotUpdateEvent(getZoneId(), pet));
          }
        }
      }
    }

    return fullUpdate ? DataburstProcessResult.FULL :
           processedAtleastOneBurst ? DataburstProcessResult.PARTIAL : DataburstProcessResult.NONE;
  }

  private EventHandler<BotUpdateEvent> botUpdateHandler;

  @Override
  public void setBotUpdateHandler(EventHandler<BotUpdateEvent> eventHandler) {
    this.botUpdateHandler = eventHandler;
  }

  private EventHandler<ExternalCommandEvent> commandHandler;

  @Override
  public void setCommandHandler(EventHandler<ExternalCommandEvent> eventHandler) {
    this.commandHandler = eventHandler;
  }

  @Override
  public void addExternalCommand(String command) {
    synchronized(incomingCommands) {
      incomingCommands.addAll(Arrays.asList(command.split(";")));
    }
  }

  private Spawn getSpawnInternal(int spawnID) {
    Spawn spawn = spawns.get(spawnID);

    if(spawn == null || (spawn.getId() == charId && !(spawn instanceof Me))) {
      if(spawnID == charId) {
        spawn = new Me(this, spawnID);
      }
      else {
        spawn = new Spawn(this, spawnID);
      }

      spawns.put(spawnID, spawn);
    }

    return spawn;
  }

  @Override
  public String toString() {
    return sessionName + ":zoneId=" + getZoneId() + ":" + getMeSpawn();
  }

  @Override
  public Ini2 getIni() {
    return ini;
  }

  @Override
  public Ini2 getGlobalIni() {
    return globalIni;
  }

  @Override
  public Logger getLogger() {
    return logger;
  }

  @Override
  public void echo(String text) {
    doCommand("/cechob " + text);
  }

  @Override
  public void log(String text) {
    String logLine = createLogLine(text);
    System.out.println(logLine);
  }

  @Override
  public void logErr(String text) {
    String logLine = createLogLine(text);
    System.err.println(logLine);
  }

  private String createLogLine(String text) {
    Spawn me = getMeSpawn();

    String hp = me == null ? "---" : String.format("%3d", me.getHitPointsPct());
    String mana = me == null ? "---" : String.format("%3d", me.getManaPct());
    String end = me == null ? "---" : String.format("%3d", me.getEndurancePct());

    return String.format("%3s/%3s/%3s %-11s " + text + "%n", hp, mana, end, "<" + charName + ">");
  }

  public void setDebug(boolean debug) {
    this.debug = debug;
  }

  /**
   * Evaluates a MacroQuest script expression to <code>true</code> or <code>false</code>.
   */
  @Override
  public boolean evaluate(String expr) {
    return translate("${If[" + expr + ",TRUE,FALSE]}").equals("TRUE");
  }

  /**
   * Translates a MacroQuest script expression and replaces all ${} constructs with their actual values.  It
   * does not attempt to evaluate the resulting expression.
   */
  @Override
  public String translate(String s) {
    String result = thread.waitForResponse(s);
    if(debug) {
      System.out.println("                " + result + " << " + s);
    }
//    System.out.println("                " + result + " << " + s);
    if(result == null) {
      // We're zoning it seems
      System.out.println(this + " : ZONING DETECTED");
      throw new ZoningException();
    }
    return result;
  }

  @Override
  public void doCommand(String s) {
    thread.doCommand(s);
    //log("CMD: " + s);
    if(logger != null) {
      logger.info("CMD: " + s);
    }
    if(debug) {
    }
    //log(s);
  }

  @Override
  public void delayUntilUpdate() {
    while(processDataBursts() == DataburstProcessResult.NONE) {
      try {
        wait(10);
      }
      catch(InterruptedException e) {
        throw new RuntimeException(e);
      }
    }
  }

  @Override
  public void delay(int timeOut) {
    delay(timeOut, (Condition)null);
  }

  /**
   * @return <code>true</code> if we exited early
   */
  @Override
  public boolean delay(int timeOut, Condition condition) {
    long millis = System.currentTimeMillis();

    while(System.currentTimeMillis() - millis < timeOut) {
      if(condition != null && condition.isValid()) {
        return true;
      }

      try {
        processDataBursts();
        wait(condition != null ? 50 : 10);
      }
      catch(InterruptedException e) {
        throw new RuntimeException(e);
      }
    }

    processDataBursts();

    return false;
  }

  /**
   * @return <code>true</code> if we exited early
   */
  @Override
  public boolean delay(int timeOut, final String expr) {
    return delay(timeOut, new Condition() {
      @Override
      public boolean isValid() {
        boolean result = evaluate(expr);
        // System.out.println("${If[" + expr + ",TRUE,FALSE]} -> " + result);
        return result;
      }
    });
  }

  private final List<ChatListener> chatListeners = new ArrayList<>();
  private final LinkedList<String> chatLines = new LinkedList<>();

  @Override
  public void addChatListener(ChatListener listener) {
    chatListeners.add(listener);
  }

  private long lastZoneTime = System.currentTimeMillis();
  private long chatLineCounter;
  private long burstCounter;

  public void addChatLine(String line) {
    synchronized(chatLines) {
      chatLineCounter++;

      if(line.equals("##")) {
        burstCounter++;
        chatLines.add("## " + System.currentTimeMillis());
      }
      else {
        chatLines.add(line);
      }

      if(chatLines.size() >= 1000 && chatLines.size() % 1000 == 0) {
        long timeSinceLastZone = System.currentTimeMillis() - lastZoneTime;

        String logLine = String.format(
          "WARNING: Chat line buffer contains %d lines (%d lines/s, %d bursts/s, me=%s)",
          chatLines.size(),
          chatLineCounter * 1000 / timeSinceLastZone,
          burstCounter * 1000 / timeSinceLastZone,
          "" + getMeSpawn()
        );

        System.err.println(logLine);

        if(logger != null) {
          logger.fine(logLine);
        }
        if(chatLines.size() >= 100000) {
          System.err.println("WARNING: Discarding all lines to prevent running out of memory");
          chatLines.clear();
        }
      }
    }
  }

  private Map<Class<?>, ResourceProvider<?>> resourceProviders = new HashMap<>();

  public <T> boolean testResource(Class<T> cls) {
    T t = obtainResource(cls);

    if(t == null) {
      return false;
    }
    else {
      releaseResource(t);
      return true;
    }
  }

  @Override
  @SuppressWarnings("unchecked")
  public <T> T obtainResource(Class<T> cls) {
    ResourceProvider<T> resourceProvider = (ResourceProvider<T>)resourceProviders.get(cls);

    return resourceProvider.obtainResource();
  }

  @Override
  @SuppressWarnings("unchecked")
  public <T> void releaseResource(T resource) {
    ResourceProvider<T> resourceProvider = (ResourceProvider<T>)resourceProviders.get(resource.getClass());

    resourceProvider.releaseResource(resource);
  }

  @Override
  public void registerResourceProvider(Class<?> cls, ResourceProvider<?> provider) {
    resourceProviders.put(cls, provider);
  }

  private final Event<? extends EverquestSession> onZoned = new Event<>(this);

  @Override
  public Event<? extends EverquestSession>.Interface onZoned() {
    return onZoned.getInterface();
  }

  private final Map<Integer, String> priorityTargets = new HashMap<>();
  private final Map<Integer, String> unmezzables = new HashMap<>();
  private final Map<Integer, String> mezzables = new HashMap<>();
  private final Map<Integer, String> falseNameds = new HashMap<>();
  private final Map<Integer, String> trueNameds = new HashMap<>();
  private final Map<Integer, String> ignoreds = new HashMap<>();
  private final Map<Integer, String> pullIgnoreds = new HashMap<>();
  private final Map<Integer, String> validObjectTargets = new HashMap<>();

  @Override
  public String getPriorityTargets() {
    String priorityTargets = this.priorityTargets.get(getZoneId());

    return priorityTargets == null ? "" : priorityTargets;
  }

  @Override
  public String getUnmezzables() {
    String unmezzables = this.unmezzables.get(getZoneId());

    return unmezzables == null ? "" : unmezzables;
  }

  @Override
  public String getMezzables() {
    String mezzables = this.mezzables.get(getZoneId());

    return mezzables == null ? "" : mezzables;
  }

  @Override
  public String getFalseNameds() {
    String falseNameds = this.falseNameds.get(getZoneId());

    return falseNameds == null ? "" : falseNameds;
  }

  @Override
  public String getTrueNameds() {
    String trueNameds = this.trueNameds.get(getZoneId());

    return trueNameds == null ? "" : trueNameds;
  }

  @Override
  public String getIgnoreds() {
    String ignoreds = this.ignoreds.get(getZoneId());

    return ignoreds == null ? "" : ignoreds;
  }

  @Override
  public String getPullIgnoreds() {
    String pullIgnoreds = this.pullIgnoreds.get(getZoneId());

    return pullIgnoreds == null ? "" : pullIgnoreds;
  }

  @Override
  public String getValidObjectTargets() {
    String validObjectTargets = this.validObjectTargets.get(getZoneId());

    return validObjectTargets == null ? "" : validObjectTargets;
  }

  private void reloadGlobalIni() {
    log("SESSION: Reloading " + globalIniFile);

    spawns.clear();  // Clear spawns to get rid of any cached pattern matching

    try {
      globalIni = new Ini2(globalIniFile);
      globalIniLastModified = globalIniFile.lastModified();

      for(Section section : globalIni) {
        if(section.getName().startsWith("Zone-")) {
          int zoneId = Integer.parseInt(section.get("ID"));

          String priorityTargets = section.get("PriorityTargets");
          String unmezzables = section.get("Unmezzables");
          String mezzables = section.get("Mezzables");
          String falseNameds = section.get("FalseNameds");
          String trueNameds = section.get("TrueNameds");
          String ignoreds = section.get("Ignoreds");
          String pullIgnoreds = section.get("PullIgnoreds");
          String validObjectTargets = section.get("ValidObjectTargets");

          if(priorityTargets != null) {
            this.priorityTargets.put(zoneId, priorityTargets);
          }
          if(unmezzables != null) {
            this.unmezzables.put(zoneId, unmezzables);
          }
          if(mezzables != null) {
            this.mezzables.put(zoneId, mezzables);
          }
          if(falseNameds != null) {
            this.falseNameds.put(zoneId, falseNameds);
          }
          if(trueNameds != null) {
            this.trueNameds.put(zoneId, trueNameds);
          }
          if(ignoreds != null) {
            this.ignoreds.put(zoneId, ignoreds);
          }
          if(pullIgnoreds != null) {
            this.pullIgnoreds.put(zoneId, pullIgnoreds);
          }
          if(validObjectTargets != null) {
            this.validObjectTargets.put(zoneId, validObjectTargets);
          }
        }
      }
    }
    catch(IOException e) {
      throw new RuntimeException(e);
    }
  }

  private void reloadIni() {
    log("SESSION: Reloading " + iniFile);

    try {
      if(iniFile.exists() || classIniFile.exists()) {
        if(alternateName == null) {
          ini = new Ini2(iniFile, allIniFile, classIniFile);
        }
        else {
          ini = new Ini2(iniFile);
        }
        iniLastModified = iniFile.exists() ? iniFile.lastModified() : 0;
        classIniLastModified = classIniFile.exists() ? classIniFile.lastModified() : 0;
        allIniLastModified = allIniFile.lastModified();
      }
      else {
        ini = new Ini2();
      }

      profiles.clear();
      profileSets.clear();

      if(ini.getSection("Modules") != null) {
        for(String profileSetNames : ini.getSection("Modules").getDefault("Profiles", "").split("\\|")) {
          String[] profiles = profileSetNames.split(",");
          ProfileSet profileSet = new ProfileSet(profiles);

          for(String profileName : profiles) {
            String cleanedProfileName = profileName.replaceAll("\\+", "");

            String defaultProfiles = ini.getSection("Modules").get("Profile." + cleanedProfileName);

            if(defaultProfiles != null) {
              this.profiles.put(cleanedProfileName.toLowerCase(), new Profile(profileSet, cleanedProfileName, defaultProfiles.split(",")));
            }
            else {
              this.profiles.put(cleanedProfileName.toLowerCase(), new Profile(profileSet, cleanedProfileName));
            }
          }

          profileSets.add(profileSet);
        }

        if(ini.getSection("General").get("Ignore") != null) {
          System.err.println("Warning: General/Ignore is no longer used, use global.ini");
        }
      }
    }
    catch(IOException e) {
      throw new RuntimeException(e);
    }
  }

  private Effect debugEffect;

  @Override
  public Effect getDebugEffect() {
    return debugEffect;
  }

  private synchronized void unloadModules() {
    modules.clear();
    resourceProviders.clear();
    userCommands.clear();
    chatListeners.clear();
    expressionListeners.clear();
    timerListeners.clear();
    effectsDAO.clear();
    onZoned.clear();
    spawns.clear();

    addUserCommand("eval", Pattern.compile("(.*)"), "", new UserCommand() {
      @Override
      public void onCommand(Matcher matcher) {
        String expr = matcher.group(1);

        try {
          Object result = Parser.parse(new ExpressionRoot(DefaultEverquestSession.this, getMe().getTarget(), null, null, null), expr);

          echo("==> " + result);
        }
        catch(SyntaxException e) {
          echo("==> Syntax Error: " + e.getToken());
        }
      }
    });

    addUserCommand("set", Pattern.compile("(.*)"), "", new UserCommand() {
      @Override
      public void onCommand(Matcher matcher) {
        String variableName = matcher.group(1).toLowerCase();

        if(variableName.isEmpty()) {
          for(String key : VARIABLE_CONTEXT.keySet()) {
            echo("==> " + key + " -> " + VARIABLE_CONTEXT.getVariable(key));
          }
        }
        else {
          VARIABLE_CONTEXT.setVariable(variableName);
          echo("==> set " + variableName);
        }
      }
    });

    addUserCommand("clear", Pattern.compile("(.*)"), "", new UserCommand() {
      @Override
      public void onCommand(Matcher matcher) {
        String variableName = matcher.group(1).toLowerCase();

        if(variableName.isEmpty()) {
          for(String key : VARIABLE_CONTEXT.keySet()) {
            echo("==> " + key + " -> " + VARIABLE_CONTEXT.getVariable(key));
          }
        }
        else {
          VARIABLE_CONTEXT.clearVariable(variableName);
          echo("==> cleared " + variableName);
        }
      }
    });

    addUserCommand("campgate", Pattern.compile(".*"), "gates to the fellowship camp", new UserCommand() {
      @Override
      public void onCommand(Matcher matcher) {
        doCommand("/nomod /itemnotify ${FindItem[Fellowship Registration Insignia].InvSlot} rightmouseup");
      }
    });

    addUserCommand("debugEffect", Pattern.compile("(.*)"), "<effect> - shows debugging information about why an effect is used or not used", new UserCommand() {
      @Override
      public void onCommand(Matcher matcher) {
        debugEffect = getEffect(matcher.group(1));

        echo("\\ay[DebugEffect]\\a-w Debugging effect: " + debugEffect);
      }
    });

    addUserCommand("load", Pattern.compile(".*"), "", new UserCommand() {
      @Override
      public void onCommand(Matcher matcher) {
        reloadModules();
        echo("==> Reloaded modules.");
      }
    });

    addUserCommand("unload", Pattern.compile(".*"), "", new UserCommand() {
      @Override
      public void onCommand(Matcher matcher) {
        unloadModules();
        echo("==> All modules unloaded.");
      }
    });

    addUserCommand("modules", Pattern.compile("(load|unload)"), "(load|unload)", new UserCommand() {
      @Override
      public void onCommand(Matcher matcher) {
        if(matcher.group(1).equals("load")) {
          reloadModules();
          echo("==> Reloaded modules.");
        }
        else {
          unloadModules();
          echo("==> All modules unloaded.");
        }
      }
    });

    addUserCommand("altini", Pattern.compile("(.*)"), "name", new UserCommand() {
      @Override
      public void onCommand(Matcher matcher) {
        if(matcher.group(1).equals("")) {
          alternateName = null;
          echo("==> Using default ini: " + charName);
          onFinishedZoning(true);
        }
        else {
          alternateName = matcher.group(1);
          echo("==> Using alternate ini: " + matcher.group(1));
          onFinishedZoning(true);
        }
      }
    });

    addUserCommand("profile", Pattern.compile("(.+)"), "(status|<[+-]profile name(s)>)", new UserCommand() {
      @Override
      public void onCommand(Matcher matcher) {
        String profileNames = matcher.group(1).toLowerCase();

        if(!profileNames.equals("status")) {
          for(String profileNameSetting : profileNames.split(" ")) {
            applyProfileCommand(profileNameSetting, 1);
          }
        }

        String availableProfiles = "";

        for(ProfileSet set : profileSets) {
          if(availableProfiles.length() > 0) {
            availableProfiles += " \\ag| ";
          }
          availableProfiles += set.toString();

          if(availableProfiles.length() > 130) {
            echo("\\ay[Profile] \\ag| " + availableProfiles + " \\ag|");
            availableProfiles = "";
          }
        }

        if(availableProfiles.length() > 0) {
          echo("\\ay[Profile] \\ag| " + availableProfiles + " \\ag|");
        }
      }

      private void applyProfileCommand(String profileNameSetting, int level) {
        String profileName = profileNameSetting.startsWith("+") || profileNameSetting.startsWith("-") ? profileNameSetting.substring(1) : profileNameSetting;

        Profile profile = profiles.get(profileName.toLowerCase());

        if(profile != null) {
          if(profileNameSetting.startsWith("+")) {
            if(!isProfileActive(profileName.toLowerCase()) || level == 1) {
              echo(String.format("\\ay[Profile] \\ag%" + level + "sActivated profile: " + profileName, ""));
              profile.activate();

              for(String defaultProfileNameSetting : profile.getDefaultProfiles()) {
                applyProfileCommand(defaultProfileNameSetting, level + 5);
              }
            }
          }
          else if(profileNameSetting.startsWith("-")) {
            if(isProfileActive(profileName.toLowerCase()) || level == 1) {
              echo(String.format("\\ay[Profile] \\ag%" + level + "sDeactivated profile: " + profileName, ""));
              profile.deactivate();
            }
          }
          else {
            echo(String.format("\\ay[Profile] \\ag%" + level + "sToggled profile: " + profileName, ""));

            if(profile.toggle()) {
              for(String defaultProfileNameSetting : profile.getDefaultProfiles()) {
                applyProfileCommand(defaultProfileNameSetting, level + 5);
              }
            }
          }
        }
        else {
          echo("\\ay[Profile] \\arNo such profile exists: " + profileName);
        }
      }
    });
  }

  private CastResultMonitor castResultMonitor;

  @Override
  public CastResultMonitor getCastResultMonitor() {
    return castResultMonitor;
  }

  private synchronized void reloadModules() {
    log("SESSION: Reloading Modules");

    unloadModules();

    castResultMonitor = new CastResultMonitor(this);
    moveLock = new Lock<>();

    Section section = globalIni.getSection("Modules");
    Injector injector = Guice.createInjector(new EverquestModule(this));

    if(section != null) {
      for(String className : globalIni.getSection("Modules").getAll("Class")) {
        try {
          Class<?> cls = Class.forName(className);
          log("Loading " + cls.getSimpleName() + " ...");

          addModule((Module)injector.getInstance(cls));

//          Constructor<?> constructor = cls.getConstructor(EverquestSession.class);
//
//          addModule((Module)constructor.newInstance(this));
          log("Loaded " + cls.getSimpleName());
        }
        catch(ClassNotFoundException e) {
          System.err.println("Class not found: " + className);
        }
//        catch(NoSuchMethodException e) {
//          System.err.println("Incorrect or missing constructor: " + className);
//        }
        catch(Exception e) {
          System.err.println("Couldn't construct: " + className);
          e.printStackTrace();
        }
      }

      addModule(new MoveModule(this));
    }
    else {
      System.err.println("No Modules section in global.ini");
    }

//    registerExpression("${Me.Casting.ID}", new ExpressionListener() {
//      @Override
//      public void stateUpdated(String result) {
//        Me me = getMe();
//
//        if(me != null) {
//          me.updateCastingID(result.matches("[0-9]+") ? Integer.parseInt(result) : 0);
//        }
//      }
//    });

    registerExpression("${Me.Aura[1]}:${Me.Aura[2]}", new ExpressionListener() {
      @Override
      public void stateUpdated(String result) {
        synchronized(auras) {
          auras.clear();

          for(String auraName : result.split(":")) {
            if(!auraName.equals("NULL")) {
              auras.add(auraName.replaceAll(" Rk\\. *II+", ""));
            }
          }
        }
      }
    });

//    registerExpression("${Window[CastingWindow].Open}", new ExpressionListener() {
//      @Override
//      public void stateUpdated(String result) {
//        Me me = getMe();
//
//        if(me != null) {
//          me.updateActivelyCasting("TRUE".equals(result));
//        }
//      }
//    });

    registerExpression("${Me.LAInspectBuffs}", new ExpressionListener() {
      @Override
      public void stateUpdated(String result) {
        hasInspectBuffs = result.matches("(1|2)");
      }
    });
  }

  private boolean hasInspectBuffs;

  @Override
  public boolean hasInspectBuffs() {
    return hasInspectBuffs;
  }

  private void createLogger() {
    logger = Logger.getLogger(charName);

    for(Handler handler : logger.getHandlers()) {
      logger.removeHandler(handler);
    }

    try {
      FileHandler fileHandler = new FileHandler("logs/" + charName + ".%g.txt", 10485760 * 2, 5, true);

      final DateFormat dateFormat = new SimpleDateFormat("dd-MM-yyyy HH:mm:ss.SSS");

      fileHandler.setFormatter(new Formatter() {

        @Override
        public String format(LogRecord record) {
          StringBuilder sb = new StringBuilder();

          sb.append("[");
          sb.append(record.getLevel().getName());
          sb.append(" ");
          sb.append(dateFormat.format(new Date(record.getMillis())));
          sb.append("] ");
          sb.append(record.getMessage());

          while(sb.length() < 80 || sb.length() % 10 != 0) {
            sb.append(" ");
          }

          sb.append("-- " + record.getSourceClassName() + "." + record.getSourceMethodName() + "()\n");
          return sb.toString();
        }
      });

      logger.setLevel(Level.FINEST);
      logger.setUseParentHandlers(false);
      logger.addHandler(fileHandler);
    }
    catch(IOException e) {
      throw new RuntimeException(e);
    }
  }

  public void onFinishedZoning(boolean characterChanged) {
    spawns.clear();

    if(characterChanged) {
      allIniFile = new File(globalIni.getValue("Global", "Path") + "/JB_all.ini");
      classIniFile = new File(globalIni.getValue("Global", "Path") + "/JB_" + translate("${Me.Class}").toLowerCase() + ".ini");
      iniFile = new File(globalIni.getValue("Global", "Path") + "/JB_" + (alternateName == null ? charName : alternateName) + ".ini");

      createLogger();
      reloadIni();
      reloadModules();
      echo("Using ini: " + iniFile.getName() + ", " + classIniFile.getName());
    }
    else {
      onZoned.trigger();
    }

    System.out.println("SESSION: Zoning Finished: " + charName + " (" + charId + ") is in zone " + zoneId);

    logger.info("ZONE: Zoned to " + zoneId);
  }

  @Override
  public Spawn getMeSpawn() {
    return getSpawn(charId);
  }

  @Override
  public Me getMe() {
    return (Me)getMeSpawn();
  }

  @Override
  public Set<Spawn> getSpawns(double range, double zRange) {
    Set<Spawn> set = new HashSet<>();
    float z = getMe().getZ();

    for(Spawn spawn : spawns.values()) {
      if(spawn.getDistance() < range && Math.abs(spawn.getZ() - z) < zRange) {
        set.add(spawn);
      }
    }

    return set;
  }

  @Override
  public Set<Spawn> getSpawns() {
    Set<Spawn> set = new HashSet<>();

    set.addAll(spawns.values());

    return set;
  }

  @Override
  public Spawn getSpawn(int id) {
    return spawns.get(id);
  }

  @Override
  public Spawn getSpawn(String name) {
    for(Spawn spawn : spawns.values()) {
      if(spawn.getName() != null) {   // TODO happend somehow that we got a null spawn in here...
        if(spawn.getName().equals(name)) {
          return spawn;
        }
      }
    }

    return null;
  }

  @Override
  public MySpell getMySpell(SpellData sd) {
    int spellId = sd.getId();
    String[] infos = translate("${Spell[" + spellId + "].Level};${Spell[" + spellId + "].Duration.TotalSeconds};${Spell[" + spellId + "].TargetType};${Spell[" + spellId + "].SpellType};${Spell[" + spellId + "].MyCastTime}").split(";");

    return new MySpell(sd, Integer.parseInt(infos[0]), Integer.parseInt(infos[1]), infos[2].toLowerCase(), infos[3].toLowerCase(), (int)(Double.parseDouble(infos[4]) * 1000));
  }

  public CharacterDAO getCharacterDAO() {
    return characterDAO;
  }

  public Spell getSpellFromBook(String name) {
    return characterDAO.getSpellFromBook(name);
  }

  @Override
  public Spell getSpell(int id) {
    Spell spell = spells.get(id);

    if(spell == null) {
      spell = new Spell(this, id);
      spells.put(id, spell);
    }

    return spell;
  }

  @Override
  public SpellData getRawSpellData(int id) {
    return rawSpellData.get(id);
  }

  @Override
  public Set<String> getActiveProfiles() {
    Set<String> activeProfiles = new HashSet<>();

    for(ProfileSet set : profileSets) {
      String activeProfile = set.getActiveProfile();

      if(activeProfile != null) {
        activeProfiles.add(activeProfile);
      }
    }

    return activeProfiles;
  }

  private final List<String> auras = new ArrayList<>();

  public Set<String> getAuras() {
    synchronized(auras) {
      return new HashSet<>(auras);
    }
  }

  @Override
  public Module getModule(String name) {
    for(Module module : modules) {
      String clsName = module.getClass().getSimpleName();

      clsName = clsName.substring(clsName.lastIndexOf('.') + 1);

      if(clsName.equals(name)) {
        return module;
      }
    }

    return null;
  }

  @Override
  public Collection<Effect> getKnownEffects() {
    return effectsDAO.getKnownEffects();
  }

  public Effect getEffect(String effectDescription) {
    return effectsDAO.getEffect(effectDescription);
  }

  /**
   * Gets an Effect based on a description string.<br>
   *
   * This only creates effects available to the character at the time, ie. spells must be scribed and clickeys
   * must be on the character.
   */
  @Override
  public Effect getEffect(String effectDescription, int aggro) {
    return effectsDAO.getEffect(effectDescription, aggro);
  }

  public boolean hasClass(String className) {
    for(Spawn spawn : getGroupMembers()) {
      if(spawn.getClassShortName().equalsIgnoreCase(className)) {
        return true;
      }
    }

    for(Spawn spawn : getBots()) {
      if(spawn.getClassShortName().equalsIgnoreCase(className)) {
        return true;
      }
    }

    return false;
  }

  public boolean isProfileActive(String profileName) {
    for(ProfileSet set : profileSets) {
      if(profileName.equalsIgnoreCase(set.getActiveProfile())) {
        return true;
      }
    }

    return false;
  }

  @Override
  public boolean isProfileActive(List<String> profiles) {
    if(profiles == null || profiles.isEmpty() || profileSets.isEmpty()) {
      return true;
    }

    for(int i = 0; i < profiles.size(); i++) {
      String expr = profiles.get(i);

      if(!expr.contains("profile(") && !expr.contains("isActive(") && !expr.contains(".")) {
        profiles.set(i, expr.replaceAll(",", " || ").replaceAll("([A-Za-z][a-zA-Z0-9]*)", "isActive(\"$1\")"));
      }
    }

    return ExpressionEvaluator.evaluate(profiles, new ProfileExpressionRoot(profileSets, this), profiles);
  }

  @Override
  public ClickableItem getClickableItem(String name) {
    Item item = getItem(name);

    return item == null ? null : clickableItems.get(item.getId());
  }

  public ClickableItem getClickableItem(int id) {
    return clickableItems.get(id);
  }

  @Override
  public ItemDAO getItemDAO() {
    return itemDAO;
  }

  public Item getItem(String name) {
    return itemDAO.getItem(name);
  }

  private final LinkedHashMap<String, List<ExpressionListener>> expressionListeners = new LinkedHashMap<>();

  private int expressionsVersion;

  @Override
  public synchronized void registerExpression(String expression, ExpressionListener listener) {
    List<ExpressionListener> listeners = expressionListeners.get(expression);

    if(listeners == null) {
      listeners = new ArrayList<>();
      expressionListeners.put(expression, listeners);
    }

    listeners.add(listener);

    String stateString = "";

    for(String s : expressionListeners.keySet()) {
      if(stateString.length() > 0) {
        stateString += ";";
      }

      stateString += s;
    }

    expressionsVersion++;

    log("ExpressionListeners: " + stateString);
    doCommand("##A" + expressionsVersion + ";" + stateString);
  }

  private final LinkedHashMap<String, List<ExpressionListener>> timerListeners = new LinkedHashMap<>();

  @Override
  public synchronized void registerTimer(String expression, ExpressionListener listener) {
    List<ExpressionListener> listeners = timerListeners.get(expression);

    if(listeners == null) {
      listeners = new ArrayList<>();
      timerListeners.put(expression, listeners);
    }

    listeners.add(listener);

    String stateString = "";

    for(String s : timerListeners.keySet()) {
      stateString += " ";
      stateString += s;
    }

    log("TimerListeners: " + stateString);
    doCommand("#T" + stateString);
  }

  public Semaphore getCastSemaphore() {
    return castSemaphore;
  }

  public Semaphore getMovementSemaphore() {
    return movementSemaphore;
  }

  public Semaphore getTargetSemaphore() {
    return targetSemaphore;
  }

  private static final VariableContext VARIABLE_CONTEXT = new VariableContext();

  public VariableContext getContext() {
    return VARIABLE_CONTEXT;
  }

  @Override
  public void doActions(List<String> actions, Spawn target, Spawn mainTarget, Spawn mainAssist) {
    ExpressionRoot root = new ExpressionRoot(this, target, mainTarget, mainAssist, null);

    for(String actionExpr : actions) {
      try {
        Parser.parse(root, actionExpr);
      }
      catch(SyntaxException e) {
        System.err.println("Syntax error in: " + actionExpr);
        System.err.println(e.getMessage());
        break;
      }
    }
  }

  private class MyThread extends Thread {
    private final Map<Integer, String> responses = new HashMap<>();
    private final Map<Integer, String> waiters = new HashMap<>();
    private final BufferedReader reader;
    private final PrintWriter writer;

    private int responseNumber = 1;

    public MyThread(BufferedReader reader, PrintWriter writer) {
      this.reader = reader;
      this.writer = writer;

      setName("IOThread(" + DefaultEverquestSession.this + ")");
      setPriority(Thread.NORM_PRIORITY + 2);
    }

    public void doCommand(String s) {
      synchronized(waiters) {
        writer.println(s);
        writer.flush();
      }
    }

    private long lastProcessedMillis;
    private int lastResponseNumber;

    public String waitForResponse(String s) {
      synchronized(waiters) {
        if(!ioThreadActive) {
          throw new SessionEndedException();
        }

        if(waiters.size() > 0) {
          System.err.println(Thread.currentThread() + ": waiters.size() = " + waiters.size() + ", but should be 0");
        }
        if(responses.size() > 0) {
          System.err.println(Thread.currentThread() + ": responses.size() = " + responses.size() + ", but should be 0");
        }

        int n = responseNumber++;

        lastProcessedMillis = System.currentTimeMillis();
        lastResponseNumber = responseNumber;

        String output = "$" + n + "-" + s;
        writer.println(output);
        writer.flush();
        waiters.put(n, s);

        while(!responses.containsKey(n)) {
          try {
            waiters.wait();

            if(!ioThreadActive) {
              throw new SessionEndedException();
            }
          }
          catch(InterruptedException e) {
            throw new RuntimeException(e);
          }
        }

        String result = responses.remove(n);
        if(logger != null) {
          logger.finest(output + " -> " + result);
        }
        return result;
      }
    }


    private final List<String> debugBuffer = new LinkedList<>();
    private final DateFormat dateFormat = new SimpleDateFormat("dd-MM-yyyy HH:mm:ss.SSS");

    @SuppressWarnings("unused")
    private void debug(String line) {
      synchronized(debugBuffer) {
        debugBuffer.add("[" + dateFormat.format(new Date()) + "] " + line);
        if(debugBuffer.size() > 1500) {
          debugBuffer.remove(0);
        }

        synchronized(waiters) {
          if(waiters.size() > 0 && lastResponseNumber == responseNumber && System.currentTimeMillis() - lastProcessedMillis > 1500) {
            getLogger().warning("RESPONSE_DEBUG: No response: " + DefaultEverquestSession.this.getMeSpawn());
            getLogger().warning("RESPONSE_DEBUG: waiters.size() = " + waiters.size());
            getLogger().warning("RESPONSE_DEBUG: waiters = " + waiters);
            getLogger().warning("RESPONSE_DEBUG: responseNumber = " + responseNumber);

            System.err.println("DUMPING RESPONSE : " + DefaultEverquestSession.this.getMeSpawn());
            System.err.println("waiters.size() = " + waiters.size());
            System.err.println("waiters = " + waiters);
            System.err.println("responseNumber = " + responseNumber);
            for(String s : debugBuffer) {
              getLogger().warning("RESPONSE_DEBUG: " + s);
              System.err.println(s);
            }
            waiters.remove(responseNumber - 1);
            responses.put(responseNumber - 1, "NULL");
            waiters.notifyAll();
          }
        }
      }
    }

    /**
     * Resends data if no response was received in due time.  This is to work around what I think is
     * a bug in the Loopback TCP/IP stack of Windows, which seems to be triggered when data is being
     * simultaneously send and received on the Server side.
     */
    private void resendWorkaround() {
      synchronized(waiters) {
        if(waiters.size() > 0 && lastResponseNumber == responseNumber && System.currentTimeMillis() - lastProcessedMillis > 450) {
          // Resend last request(s)
          for(Integer i : waiters.keySet()) {
            System.err.println(Thread.currentThread() + ": Resending: $" + i + "-" + waiters.get(i));
            writer.println("$" + i + "-" + waiters.get(i));
            writer.flush();
            lastProcessedMillis = System.currentTimeMillis();
          }
        }
      }
    }

    @Override
    public void run() {
      boolean bufSkip = false;

      try {
        for(;;) {
          String line = reader.readLine();

//          debug(line);  // Stores and Activates the Debug Buffer code
          resendWorkaround();

          if(line.startsWith("@BUF=")) {
            bufSkip = true;
          }

          if(line.startsWith("@ENDBUF")) {
            bufSkip = false;
          }

          if(!bufSkip) {
            if(line.startsWith("$")) {
              synchronized(waiters) {
  //              System.out.println("Received response: " + line);
                int dash = line.indexOf('-');

                if(dash > 0) {
                  int responseNo = Integer.parseInt(line.substring(1, dash));
                  if(waiters.remove(responseNo) == null) {
                    getLogger().warning("RESPONSE_DEBUG2: Unexpected response " + responseNo + ": " + line);
                    System.err.println("Unexpected response " + responseNo + ": " + line);
                  }
                  else {
                    responses.put(responseNo, line.substring(dash + 1));
                    waiters.notifyAll();
                  }
                }
                else if(line.equals("$ZONING")) {
                  // Zoning detected.  Terminate all responses.
                  for(int i : waiters.keySet()) {
                    responses.put(i, null);
                  }
                  waiters.clear();
                  waiters.notifyAll();
                }


  //              for(String s : waiters.keySet()) {
  //                if(line.startsWith(s)) {
  //                  int n = waiters.get(s);
  //                  responses.put(n, line.substring(line.indexOf('-') + 1));
  //                  waiters.remove(s);
  //                  waiters.notifyAll();
  //                  break;
  //                }
  //              }
              }
            }
            else {
              addChatLine(line);
            }
          }
        }
      }
      catch(IOException e) {
        logger.log(Level.SEVERE, "Exception while communicating with MQ: " + e, e);
        logger.severe("Commands in queue:");
        System.err.println("Commands in queue:");

        for(int key : waiters.keySet()) {
          logger.severe(" " + key + ": " + waiters.get(key));
          System.err.println(" " + key + ": " + waiters.get(key));
        }

        synchronized(waiters) {
          ioThreadActive = false;
          waiters.notifyAll();
        }

        throw new RuntimeException(e);
      }
      catch(Exception e) {
        logger.log(Level.SEVERE, "IOThread exited because of: " + e, e);
        throw new RuntimeException(e);
      }
      finally {
        ioThreadActive = false;

        try {
          reader.close();  // Make sure the connection is killed, otherwise no reconnect is possible.
        }
        catch(Exception e) {
          // ignore
        }
      }
    }
  }

  private static class UserCommandWrapper {
    private final UserCommand userCommand;
    private final Pattern pattern;
    private final String helpText;

    public UserCommandWrapper(UserCommand userCommand, Pattern pattern, String helpText) {
      this.userCommand = userCommand;
      this.pattern = pattern;
      this.helpText = helpText;
    }
  }
}
