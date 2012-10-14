package autoeq.eq;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import autoeq.DebuffCounter;
import autoeq.SpellData;

// TODO Spawns are too permanent... EQ likely reuses id's
public class Spawn {
  private static final Pattern PATTERN = Pattern.compile("#S-[0-9]+ [0-9]+ ([0-9]+) ([-0-9]+) ([0-9]+) ([-0-9]+) ([0-9]+) ([0-9]+) ([-0-9\\.]+),([-0-9\\.]+),([-0-9\\.]+) ([-0-9\\.]+) ([-0-9\\.]+) ([-0-9]+) ([-0-9]+) ([-0-9]+) ([-0-9]+) ([-0-9]+) ([-0-9]+) ([0-9]+) (.*)");

  protected final EverquestSession session;

  private final int id;

  private final Map<Integer, SpellEffectManager> spellEffectManagers = new HashMap<>();

  private String name;
  private String fullName;

  private int classId;
  private int bodyType;
  private int race;
  private int deity;

  private int hitPointsPct;
  private int level;
  private int type;

  private float lastX, lastY;  //, lastZ;

  private float x, y, z;
  private float heading;

  private int masterId;
  private int petId;
  private int targetId;

//  private float speedRun;
  private int standState;
  private Spell casting;
  private boolean lineOfSight;
  private boolean mercenary;

  private long tankTime;
  private int myAgro;
  private final LinkedList<Location> locations = new LinkedList<>();

  private final Date creationDate = new Date();
  private Date timeOfDeath;

  public Spawn(EverquestSession session, int id) {
    this.session = session;
    this.id = id;
  }

  public Date getCreationDate() {
    return creationDate;
  }

  public EverquestSession getSession() {
    return session;
  }

  public int getId() {
    return id;
  }

  public String getName() {
    return name;
  }

  public boolean isAlive() {
    SpawnType type = getType();

    return type != SpawnType.NPC_CORPSE && type != SpawnType.PC_CORPSE;
  }

  public SpawnType getType() {
    switch(type) {
    case 0:
      return SpawnType.PC;
    case 1:
      if(mercenary) {
        return SpawnType.PC;
      }

      if(name.endsWith("`s Familiar")) {
        return SpawnType.FAMILIAR;
      }

      if(name.endsWith("`s Mount")) {
        return SpawnType.MOUNT;
      }

      if(name.matches("Eye of [A-Z][a-z]{3,}")) {
        // TODO add body type here
        return SpawnType.SCOUT_EYE;
      }

      // TODO familairs?
      if(bodyType == 1 && classId == 1 && race == 567) {
        return SpawnType.CAMP;
      }

//      if(name.contains("anner")) {
//        System.err.println(name + ": AURA: classId = " + classId + "; race = " + race + " bodyType = " + bodyType);
//      }

      if(name.startsWith("Guild Banner of ")) {
//      if(classId == 1 && bodyType == 1 && (race == 553 || race == 556)) {
        return SpawnType.CAMP;
      }

      if(classId == 62 && bodyType == 11 && race == 127) {
        // Circle of Divinity: AURA: classId = 62; race = 127
        // Learners Aura: AURA: classId = 62; race = 127
        return SpawnType.AURA;
      }

      if(classId == 62) { // LDON object
        //else if(bodyType == 5 && classId == 62 && race == 514) { // Spirit Idol
        return SpawnType.OBJECT;
      }

      if(classId == 1 && bodyType == 5 && race == 376) { // A box
        return SpawnType.OBJECT;
      }

      if(bodyType == 100 || bodyType == 101 || bodyType == 102 || bodyType == 103) {
        return SpawnType.UNTARGETABLE;
      }

      if(name.trim().length() == 0) {
        return SpawnType.UNTARGETABLE;
      }

      return SpawnType.NPC;
    case 2:
    case 3:
      return deity == 0 ? SpawnType.NPC_CORPSE : SpawnType.PC_CORPSE;
    default:
      throw new RuntimeException("Spawn " + this + " has unknown spawn type: " + type);
    }
  }
  /*
static inline eSpawnType GetSpawnType(PSPAWNINFO pSpawn)
{
    switch(pSpawn->Type)
    {
    case SPAWN_PLAYER:
        {
            return PC;
        }
    case SPAWN_NPC:
        if (strstr(pSpawn->Name,"s_Mount"))
        {
            return MOUNT;
        }
        if (pSpawn->MasterID)
            return PET;

        switch(GetBodyType(pSpawn))
        {
        case 0:
      if (pSpawn->Class==62)
                return OBJECT;
            return NPC;
    case 1:
      if ((pSpawn->Class==1) && (pSpawn->Race==567))
        return CAMPFIRE;
      if ((pSpawn->Class==8) && ((pSpawn->Race==553) || (pSpawn->Race==556)))
        return BANNER;
      return NPC;
        //case 3:
        //    return NPC;
        case 5:
      if (strstr(pSpawn->Name,"Idol") || strstr(pSpawn->Name,"Poison") || strstr(pSpawn->Name,"Rune"))
                return AURA;
      if (pSpawn->Class==62)
          return OBJECT;
            return NPC;
        case 11:
      if (strstr(pSpawn->Name,"Aura") || strstr(pSpawn->Name,"Circle_of") || strstr(pSpawn->Name,"Guardian_Circle") || strstr(pSpawn->Name,"Earthen_Strength"))
                return AURA;
            return UNTARGETABLE;
        //case 21:
        //    return NPC;
        //case 23:
        //    return NPC;
        case 33:
            return CHEST;
        //case 34:
        //    return NPC;
        //case 65:
        //    return TRAP;
        //case 66:
        //    return TIMER;
        //case 67:
        //    return TRIGGER;
        case 100:
            return UNTARGETABLE;
        case 101:
            return TRAP;
        case 102:
            return TIMER;
        case 103:
            return TRIGGER;
        default:
            return NPC;
        }
        return NPC;
    case SPAWN_CORPSE:
        return CORPSE;
    default:
        return ITEM;
    }
}
   */

  public boolean isPet() {
    return masterId > 0;
  }

  public boolean isMyPet() {
    return masterId == session.getMe().getId();
  }

  public boolean inLineOfSight() {
    return lineOfSight;
  }

  public Spawn getPet() {
    return session.getSpawn(petId);
  }

  /**
   * Name is somewhat poorly chosen.  This function returns false if the exact buf is already on the target since
   * technically this "doesn't stack" (or is atleast pointless to cast).
   */
  public boolean willStack(Spell spell) {
    if(spell.getDuration() > 0) {
      Set<Spell> buffs = getSpellEffects();

      for(Spell buff : buffs) {
        if(buff.equals(spell)) {
          return false;
        }
      }

      for(Spell buff : buffs) {
        if(!spell.willStack(buff)) {
//          System.err.println(spell + " not stack with " + buff);
          return false;
        }
      }
    }

    return true;
  }

  public Spawn getMaster() {
    return session.getSpawn(masterId);
  }

  public int getDeity() {
    return deity;
  }

  public boolean isCaster() {
    return classId >= 11 && classId <= 14;
  }

  public boolean isHealer() {
    return classId == 10 || classId == 6 || classId == 2;
  }

  public boolean isTank() {
    return classId == 1 || classId == 3 || classId == 5;
  }

  public String getClassShortName() {
    switch(classId) {
    case 0: return "?";
    case 1: return "WAR";
    case 2: return "CLR";
    case 3: return "PAL";
    case 4: return "RNG";
    case 5: return "SHD";
    case 6: return "DRU";
    case 7: return "MNK";
    case 8: return "BRD";
    case 9: return "ROG";
    case 10: return "SHM";
    case 11: return "NEC";
    case 12: return "WIZ";
    case 13: return "MAG";
    case 14: return "ENC";
    case 15: return "BST";
    case 16: return "BER";
    default:// throw new RuntimeException("Unknown ClassID: " + classId);
      return "?";
    }
  }

  public boolean isMe() {
    return session.getMe().equals(this);
  }

  public boolean isBot() {
    return session.getBotNames().contains(name);
  }

  public boolean isGroupMember() {
    return session.getGroupMembers().contains(this);
  }

  public int getLevel() {
    return level;
  }

  public float getX() {
    return x;
  }

  public float getY() {
    return y;
  }

  public float getZ() {
    return z;
  }

  public Location getLocation() {
    return new Location(x, y, z);
  }

  /**
   * The direction this spawn is facing, in degrees, -180 to 180.
   */
  public float getHeading() {
    return heading;
  }

  /**
   * Distance to me.
   */
  public double getDistance() {
    Spawn me = session.getMe();

    return getDistance(me.x, me.y);
  }

  /**
   * Distance to (x, y)
   */
  public double getDistance(float x, float y) {
    return Math.sqrt((this.x - x) * (this.x - x) + (this.y - y) * (this.y - y));
  }

  /**
   * The direction towards this spawn.
   */
  public float getDirection() {
    return getDirection(session.getMe());
  }

  /**
   * The direction towards the given spawn, -180 to 180 degrees
   */
  public float getDirection(Spawn other) {
    return (float)(Math.atan2(x - other.x, y - other.y) * 180 / Math.PI);
  }

  public int getHitPointsPct() {
    return hitPointsPct;
  }

  public StandState getStandState() {
    switch(standState) {
    case 100: return StandState.STANDING;
    case 102: return StandState.CASTING;
    case 105: return StandState.BIND;
    case 110: return StandState.SITTING;
    case 111: return StandState.DUCKED;
    case 115: return StandState.FEIGNED;
    case 120: return StandState.DEAD;
    default: throw new RuntimeException("Unknown StandState: " + standState);
    }
  }

  public Spawn getTarget() {
    return session.getSpawn(targetId);
  }

  public boolean hasBuff(String name) {
    return getBuffNames().contains(name);
  }

  public Set<String> getBuffNames() {
    Set<String> names = new HashSet<>();

    for(SpellEffectManager manager : spellEffectManagers.values()) {
      if(manager.getDuration() > 0) {
//        System.err.println(manager.getSpell().getName());
        names.add(manager.getSpell().getName());
      }
    }

    return names;
  }

  public boolean isSitting() {
    return getStandState() == StandState.SITTING;
  }

  public boolean isStanding() {
    return getStandState() == StandState.STANDING;
  }

  public boolean isFeigned() {
    return getStandState() == StandState.FEIGNED;
  }

  public boolean isMoving() {
    float deltaX = x - lastX;
    float deltaY = y - lastY;

    if(deltaX * deltaX + deltaY * deltaY > 0.5) {
      return true;
    }
    return false;
  }

  public Spell getCastedSpell() {
    return casting;
  }

  public boolean isCasting() {
    return casting != null;
  }

  /**
   * @return <code>true</code> if spawn is alive and a PC or a PC pet
   */
  public boolean isFriendly() {
    Spawn master = getMaster();

    return master != null ? master.isFriendly() : getType() != null && getType() == SpawnType.PC;
  }

  /**
   * @return <code>true</code> if spawn is alive and an NPC or an NPC pet
   */
  public boolean isEnemy() {
    Spawn master = getMaster();

    return getType() != null && getType() == SpawnType.NPC &&
        (master == null || (master.getType() != null && (master.getType() == SpawnType.NPC || master.getType() == SpawnType.NPC_CORPSE)));
  }

  /**
   * Returns whether this spawn is facing the other spawn (within a certain angle).
   */
  public boolean isFacing(Spawn other, int angle) {
    float diff = heading - other.getDirection(this);
    diff = Math.abs(diff > 180 ? diff - 360 : diff);

    return diff <= angle;
  }

  public int getDebuffCounters(DebuffCounter.Type type) {
    for(SpellEffectManager manager : spellEffectManagers.values()) {
      if(manager.getDuration() > 0) {
        DebuffCounter debuffCounters = session.getRawSpellData(manager.getSpell().getId()).getDebuffCounters();

        if(debuffCounters != null && debuffCounters.getType() == type) {
          return debuffCounters.getCounters();
        }
      }
    }

    return 0;
  }

  public int getPoisonCounters() {
    return getDebuffCounters(DebuffCounter.Type.POISON);
  }

  public int getDiseaseCounters() {
    return getDebuffCounters(DebuffCounter.Type.DISEASE);
  }

  public int getCurseCounters() {
    return getDebuffCounters(DebuffCounter.Type.CURSE);
  }

  public int getCorruptionCounters() {
    return getDebuffCounters(DebuffCounter.Type.CORRUPTION);
  }

  public void increaseTankTime(long millis) {
    tankTime += millis;
  }

  /**
   * Returns the amount of agro the tank has on this spawn.  Can be negative initially.
   */
  public int getTankAgro() {
    int tankAPS = Integer.parseInt(session.getIni().getSection("General").getDefault("TankAPS", "500"));
    return (int)((tankTime - 5000) * tankAPS / 1000);
  }

  private int getTotalDotAgro() {
    int totalAgro = 0;

    for(SpellEffectManager manager : spellEffectManagers.values()) {
      if(manager.getSpell().getDuration() > 0) {
        SpellData spellData = manager.getSpell().getRawSpellData();
        long dmgPerTick = 0;

        for(int i = 0; i < 12; i++) {
          if(spellData.getAttrib(i) == SpellData.ATTRIB_DAMAGE) { // 0 = Damage
            dmgPerTick += spellData.getMax(i);
          }
        }

        totalAgro += (manager.getTotalDuration() * dmgPerTick) / 6000;
      }
    }

    return totalAgro;
  }

  /**
   * Returns the amount of agro you have on this spawn.
   */
  public int getMyAgro() {
    return myAgro + getTotalDotAgro();
  }

  /**
   * Increases the amount of agro you have on this spawn.
   */
  public void increaseAgro(int agro) {
    int currentAgro = getMyAgro();
    int tankAPS = Integer.parseInt(session.getIni().getSection("General").getDefault("TankAPS", "500"));

    if(getMyAgro() + agro < -tankAPS * 5) {
      myAgro = -currentAgro - tankAPS * 5;
    }
    else {
      myAgro += agro;
    }
  }

  private final HistoryValue<Integer> damageHistory = new HistoryValue<>(0, 3 * 60 * 1000);

//  private final LinkedList<HealthDataPoint> dataPoints = new LinkedList<HealthDataPoint>();
//
//  private long getLastStable(double maxDmg, long period) {
//    if(dataPoints.isEmpty()) {
//      return 0;
//    }
//
//    HealthDataPoint lastDataPoint = null;
//    double totalDamage = 0;
//
//    Iterator<HealthDataPoint> it = dataPoints.iterator();
//    HealthDataPoint startPoint = it.next();
//
//    outer:
//    for(HealthDataPoint dataPoint : dataPoints) {
//      while(startPoint.millis - dataPoint.millis >= period) {
//        if(totalDamage < maxDmg) {
//          break outer;
//        }
//
//        HealthDataPoint newStartPoint = it.next();
//        if(startPoint.hp < newStartPoint.hp) {
//          totalDamage -= newStartPoint.hp - startPoint.hp;
//        }
//        startPoint = newStartPoint;
//      }
//
//      if(lastDataPoint != null && lastDataPoint.hp < dataPoint.hp) {
//        totalDamage += dataPoint.hp - lastDataPoint.hp;
//      }
//
//      lastDataPoint = dataPoint;
//    }
//
//    return System.currentTimeMillis() - startPoint.millis;
//  }

  public double getTopDPS(long range, long period) {
    return getTopDPS(range, period, damageHistory);
  }

  public double getAvgDPS(long range, long period) {
    long time = tankTime;

    if(time < 5000) {
      time = 5000;
    }
    if(time > range) {
      time = range;
    }
    return getAvgDPS(time, period, damageHistory);
  }

  /**
   * Gets the maximum DPS done in <code>range</code> millis done in <code>period</code> millis.
   */
  public static double getTopDPS(long range, long period, HistoryValue<Integer> damageHistory) {
    double topDPS = 0.0;

    for(long t = 0; t < range - period; t += 250) {
      double dps = (double)(damageHistory.getValue(t) - damageHistory.getValue(t + period)) / period * 1000;

      if(dps > topDPS) {
        topDPS = dps;
      }
    }

    return topDPS;
  }

  /**
   * Gets the average DPS done in <code>range</code> millis done in <code>period</code> millis.
   */
  public static double getAvgDPS(long range, long period, HistoryValue<Integer> damageHistory) {
    double totalDPS = 0.0;
    int count = 0;

    for(long t = 0; t < range - period; t += 250) {
      double dps = (double)(damageHistory.getValue(t) - damageHistory.getValue(t + period)) / period * 1000;

      totalDPS += dps;
      count++;
    }

    return totalDPS / count;
  }


//  /**
//   * Gets the average DPS done in percentage points in the last <code>millis</code> milliseconds.
//   */
//  public double getDPS(long millis) {
//    if(millis == 0) {
//      return 0;
//    }
//
//    long currentMillis = System.currentTimeMillis();
//    HealthDataPoint previousDataPoint = null;
//    double totalDamage = 0;
//
//    for(HealthDataPoint dataPoint : dataPoints) {
//      if(currentMillis - dataPoint.millis >= millis) {
//        break;
//      }
//
//      if(previousDataPoint != null && previousDataPoint.hp < dataPoint.hp) {
//        totalDamage += dataPoint.hp - previousDataPoint.hp;
//      }
//
//      previousDataPoint = dataPoint;
//    }
//
//    // System.out.println("TD = " + totalDamage + " DPS = " + (int)(totalDamage / millis * 1000) + "  Oldest datapoint = " + (currentMillis - dataPoints.getLast().millis) + " " + getName() + ":: " + dataPoints);
//
//    return totalDamage / millis * 1000;
//  }

//  /**
//   * @param scanPeriod how far back to scan
//   * @param period size of period to average
//   * @return
//   */
//  public double getWorstDPS(long scanPeriod, long period) {
//    long currentMillis = System.currentTimeMillis();
//    HealthDataPoint lastDataPoint = null;
//    double totalDamage = 0;
//
//    for(HealthDataPoint dataPoint : dataPoints) {
//      if(currentMillis - dataPoint.millis > millis) {
//        break;
//      }
//
//      if(lastDataPoint != null && lastDataPoint.hp < dataPoint.hp) {
//        totalDamage += dataPoint.hp - lastDataPoint.hp;
//      }
//
//      lastDataPoint = dataPoint;
//    }
//
////    System.out.println("TD = " + totalDamage + " DPS = " + (int)(totalDamage / millis * 1000) + "  Oldest datapoint = " + (currentMillis - dataPoints.getLast().millis) + " " + getName() + ":: " + dataPoints);
//
//    return totalDamage / millis * 1000;
//  }
//

  public int getTimeToHitPointsPct(int hitPointsPct) {
    if(this.hitPointsPct <= hitPointsPct) {
      return 0;
    }

    double dps = getAvgDPS(30000, 3000);

    if(dps < 0.1) {
      return 120;
    }

    int timeToPct = (int)((this.hitPointsPct - hitPointsPct) / dps);

    if(timeToPct > 120) {
      return 120;
    }
    else {
      return timeToPct;
    }
  }

  public int getMinTimeToHitPointsPct(int hitPointsPct) {
    if(this.hitPointsPct <= hitPointsPct) {
      return 0;
    }

    double dps = getTopDPS(30000, 3000);

    if(dps < 0.1) {
      return 120;
    }

    int timeToPct = (int)((this.hitPointsPct - hitPointsPct) / dps);

    if(timeToPct > 120) {
      return 120;
    }
    else {
      return timeToPct;
    }
  }

  public int getTimeToLive() {
    return getTimeToHitPointsPct(0);
  }

  public int getMinTimeToLive() {
    return getMinTimeToHitPointsPct(0);
  }

  private int previousHitPointsPct = 100;

  protected void updateTTL() {
    int delta = previousHitPointsPct - hitPointsPct;

    if(delta > 0) {
//      System.err.println("Adding : " + delta + " -- " + hitPointsPct);
      damageHistory.add(damageHistory.getMostRecent() + delta);
    }

    previousHitPointsPct = hitPointsPct;
  }

  public void updateHealth(int hitPointsPct) {
    this.hitPointsPct = hitPointsPct;
  }

  public void updateTarget(int targetId) {
    this.targetId = targetId;
  }

  private static final Pattern SPACE = Pattern.compile(" ");

  public void updateBuffs(String buffPart) {
    if(buffPart.trim().length() > 0) {
      Set<Integer> toBeRemoved = new HashSet<>(spellEffectManagers.keySet());

      for(String b : SPACE.split(buffPart.trim())) {
        int spellId = Integer.parseInt(b);

        if(spellId > 0) {
          SpellEffectManager manager = getSpellEffectManager(spellId);
          toBeRemoved.remove(spellId);

          manager.setDuration(60000);
        }
      }

      for(int spellId : toBeRemoved) {
        SpellEffectManager manager = getSpellEffectManager(spellId);

        if(manager.isRemoveable()) {
          spellEffectManagers.remove(spellId);
        }
      }
    }
  }

  public List<Location> getLastLocations() {
    return locations;
  }

  private final List<LocationListener> locationListeners = new ArrayList<>();

  public synchronized void addLocationListener(LocationListener listener) {
    if(!locations.isEmpty()) {
      listener.updateLocation(locations.getFirst());
    }
    locationListeners.add(listener);
  }

  public synchronized void removeLocationListener(LocationListener listener) {
    locationListeners.remove(listener);
  }

  private synchronized void updateLocation(float newX, float newY, float newZ, float newHeading) {
    this.lastX = x;
    this.lastY = y;
//    this.lastZ = z;
    this.x = newX;
    this.y = newY;
    this.z = newZ;

    float h = newHeading * 360 / 512;

    if(h > 180) {
      h -= 360;
    }

    this.heading = h;

    if(locations.isEmpty() || locations.getFirst().x != newX || locations.getFirst().y != newY) {
      Location loc = new Location(newX, newY, newZ);

      locations.addFirst(loc);
      if(locations.size() > 120) {
        locations.removeLast();
      }

      notifyLocationListeners(loc);
    }
  }

  private void notifyLocationListeners(Location loc) {
    for(LocationListener listener : locationListeners) {
      listener.updateLocation(loc);
    }
  }

  private static final Pattern NEG_10000 = Pattern.compile("-1\\.#J");
  private static final Pattern CLEAN_NUMBERS = Pattern.compile("[#0-9]");
  private static final Pattern CLEAN_UNDERSCORE = Pattern.compile("_");

  protected void updateSpawn(String infoParameter) {
    String info = NEG_10000.matcher(infoParameter).replaceAll("-10000");
    Matcher matcher = PATTERN.matcher(info);

    if(matcher.matches()) {
      boolean wasAlive = isAlive();

      this.type = Integer.parseInt(matcher.group(5));

      // Pattern.compile(regex).matcher(this).replaceAll(replacement)
      this.fullName = matcher.group(19);
      String name = CLEAN_UNDERSCORE.matcher(CLEAN_NUMBERS.matcher(fullName).replaceAll("")).replaceAll(" ");
//      String name = fullName.replaceAll("[#0-9]", "").replaceAll("_", " ");

//      if(type == 2 || type == 3) {
//        name += "'s corpse";
//      }

      this.name = name;
      this.level = Integer.parseInt(matcher.group(1));
      if(!isMe() && !isBot()) {
        // Health returned for Me is actual hitpoints, not a percentage
        updateHealth(Integer.parseInt(matcher.group(2)));
      }
      this.classId = Integer.parseInt(matcher.group(3));
      this.deity = Integer.parseInt(matcher.group(4));
      this.standState = Integer.parseInt(matcher.group(6));
      updateLocation(Float.parseFloat(matcher.group(7)), Float.parseFloat(matcher.group(8)), Float.parseFloat(matcher.group(9)), Float.parseFloat(matcher.group(10)));
//      this.speedRun = Float.parseFloat(matcher.group(11));
      this.masterId = Integer.parseInt(matcher.group(13));
      this.petId = Integer.parseInt(matcher.group(14));
      this.bodyType = Integer.parseInt(matcher.group(15));
      this.race = Integer.parseInt(matcher.group(16));
      this.lineOfSight = matcher.group(17).equals("1");
      this.mercenary = matcher.group(18).equals("1");

      int castingID = Integer.parseInt(matcher.group(12));

      casting = castingID <= 0 ? null : session.getSpell(castingID);

      if(wasAlive && !isAlive()) {
        timeOfDeath = new Date();
      }
    }
    else {
      System.err.println("WARNING: Unable to parse: " + infoParameter);
    }
  }

  public Date getTimeOfDeath() {
    return timeOfDeath;
  }

  public SpellEffectManager getSpellEffectManager(int spellId) {
    SpellEffectManager manager = spellEffectManagers.get(spellId);

    if(manager == null) {
      manager = new SpellEffectManager(session.getSpell(spellId), this);
      spellEffectManagers.put(spellId, manager);
    }

    return manager;
  }

  public SpellEffectManager getSpellEffectManager(Spell spell) {
    return getSpellEffectManager(spell.getId());
  }

//  public void addSpellEffect(Spell spell) {
//    if(session.getMe().isBard() && spell.getDuration() <= 30) {
//      System.err.println("Adding timer for " + spell + " : " + spell.getDuration());
//      spellEffectTimers.put(spell.getId(), new SpellEffectTimer(System.currentTimeMillis() + spell.getDuration() * 1000, spell.getId(), true));
//    }
//    else {
//    // TODO +6000 is for Promised Renewal... need a way to make sure it has triggered before refreshing.
//      spellEffectTimers.put(spell.getId(), new SpellEffectTimer(System.currentTimeMillis() + spell.getDuration() * 1000 + 6000, spell.getId(), false));
//    }
//  }
//
//  public void addSpellEffect(Spell spell, int millis) {
//    spellEffectTimers.put(spell.getId(), new SpellEffectTimer(System.currentTimeMillis() + millis, spell.getId(), true));
//  }
//
//  public long getDuration(Spell spell) {
//    SpellEffectTimer spellEffectTimer = spellEffectTimers.get(spell.getId());
//
//    if(spellEffectTimer != null && !spellEffectTimer.isExpired()) {
//      return spellEffectTimer.getTimeLeft();
//    }
//    return 0;
//  }

  /**
   * Returns all effects present on this spawn.
   *
   * @return all effects present on this spawn
   */
  public Set<Spell> getSpellEffects() {
    Set<Spell> buffs = new HashSet<>();

    for(SpellEffectManager manager : spellEffectManagers.values()) {
      if(manager.getDuration() > 0) {
        buffs.add(manager.getSpell());
      }
    }

    return buffs;
  }

  public boolean isMezzed() {
    for(Spell spell : getSpellEffects()) {
      if(spell.isMez()) {
        return true;
      }
    }

    return false;
  }

  private Map<String, Object> data = new HashMap<>();

  public Object getUserValue(String name) {
    return data.get(name);
  }

  public Object getDefaultUserValue(String name, Object defaultValue) {
    if(data.containsKey(name)) {
      return data.get(name);
    }

    return defaultValue;
  }

  public void setUserValue(String name, Object value) {
    data.put(name, value);
  }

  @Override
  public String toString() {
    return "Spawn(\"" + name + "\", " + id + ", range " + (int)getDistance() + ")";
  }

//  private static class HealthDataPoint {
//    private final long millis;
//    private final int hp;
//
//    public HealthDataPoint(int hp) {
//      this.millis = System.currentTimeMillis();
//      this.hp = hp;
//    }
//
//    @Override
//    public String toString() {
//      return "HDP[" + hp + "]";
//    }
//  }
}
