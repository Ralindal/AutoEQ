package autoeq.eq;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import autoeq.Attribute;
import autoeq.DebuffCounter;
import autoeq.DebuffCounter.Type;
import autoeq.SpellData;
import autoeq.effects.Effect;
import autoeq.modules.target.TargetModule;
import autoeq.spelldata.effects.EffectDescriptor;

// TODO Spawns are too permanent... EQ likely reuses id's
public class Spawn {
  private static final Pattern PATTERN = Pattern.compile("#S-[0-9A-F]+ [0-9A-F]+ ([0-9A-F]+) ([-0-9A-F]+) ([-0-9A-F]+) ([-0-9A-F]+) ([0-9A-F]+) ([-0-9A-F]+) ([0-9A-F]+) ([0-9A-F]+) ([-0-9A-F]+),([-0-9A-F]+),([-0-9A-F]+) ([-0-9A-F]+) ([-0-9A-F]+) ([-0-9A-F]+) ([-0-9A-F]+) ([-0-9A-F]+) ([-0-9A-F]+) ([-0-9A-F]+) ([-0-9A-F]+) ([0-9A-F]+) ([-0-9A-F]+) (.*)");

  protected final EverquestSession session;

  private final int id;

  private final Map<Integer, SpellEffectManager> spellEffectManagers = new HashMap<>();

  private String name;
  private String fullName;

  private int classId;
  private int bodyType;
  private int race;
  private int deity;

  private int hitPointsPct = 100;  // initialized to 100% to prevent heals being casted on fresh spawns
  private int manaPct;
  private int endurancePct;
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
  private final LinkedList<Location> locations = new LinkedList<>();
  private final HistoricValue<Location> historicLocations = new HistoricValue<>(10 * 1000);

  private final Date creationDate = new Date();
  private Date timeOfDeath;

  private long lastMovementMillis;

  public Spawn(EverquestSession session, int id) {
    this.session = session;
    this.id = id;
  }

  public Date getCreationDate() {
    return creationDate;
  }

  public boolean isOlderThan(int seconds) {
    return (new Date().getTime() - creationDate.getTime()) / 1000 > seconds;
  }

  public boolean isDeathOlderThan(int seconds) {
    return (new Date().getTime() - timeOfDeath.getTime()) / 1000 > seconds;
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

  public boolean matches(String pattern) {
    return name.matches("(?i)" + pattern);
  }

  public boolean isAlive() {
    SpawnType type = getType();

    return type != SpawnType.NPC_CORPSE && type != SpawnType.PC_CORPSE;
  }

  public int getClassId() {
    return classId;
  }

  public int getBodyType() {
    return bodyType;
  }

  public int getRace() {
    return race;
  }

  /**
   * Gets the PC Spawn belonging to a Corpse, if in zone.
   *
   * @return the PC Spawn belonging to a Corpse, if in zone
   */
  public Spawn getPC() {
    return session.getSpawn(getName().replaceAll("'s corpse", ""));
  }

  private SpawnType cachedSpawnType = SpawnType.PC;

  public SpawnType getType() {
    return cachedSpawnType;
  }

  private static SpawnType determineType(String name, int type, int classId, int deity, int bodyType, int race, boolean mercenary) {
    if(name.endsWith("`s Familiar") || name.endsWith("`s familiar")) {
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

//    if(name.contains("anner")) {
//      System.err.println(name + ": AURA: classId = " + classId + "; race = " + race + " bodyType = " + bodyType);
//    }

    if(name.startsWith("Guild Banner of ")) {
//    if(classId == 1 && bodyType == 1 && (race == 553 || race == 556)) {
      return SpawnType.CAMP;
    }

    if((classId == 62 || classId == 255) && bodyType == 11 && race == 127) {
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

    switch(type) {
    case 0:
      return SpawnType.PC;
    case 1:
      if(mercenary) {
        return SpawnType.PC;
      }

      if(name.endsWith("'s corpse")) {
        System.err.println("Warning, wrong detected NPC: " + name + "; cid = " + classId + "; bt = " + bodyType + "; r = " + race);
          // cid = 1, bt = 11, r = 467
          // cid = 1, bt = 11, r = 457
          // a shiliskin soldier`s corpse
          // Assistant Savil`s corpse --> "fake" corpses in Stoneroot Falls
      }
      return name.endsWith("'s corpse") ? SpawnType.NPC_CORPSE : SpawnType.NPC;
    case 2:
    case 3:
      return deity == 0 ? SpawnType.NPC_CORPSE : SpawnType.PC_CORPSE;
    default:
      throw new RuntimeException("Spawn " + name + " has unknown spawn type: " + type);
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

  public List<Spawn> getGroupMembers() {
    if(isGroupMember()) {
      Set<Spawn> groupMembers = new HashSet<>(session.getGroupMembers());

      groupMembers.remove(this);

      List<Spawn> groupMemberList = new ArrayList<>();

      groupMemberList.add(this);
      groupMemberList.addAll(groupMembers);

      return groupMemberList;
    }

    return Collections.singletonList(this);
  }

  public int getAggro() {
    return session.getMe().getExtendedTargetAggro(this);
  }

  /**
   * Name is somewhat poorly chosen.  This function returns false if the exact buf is already on the target since
   * technically this "doesn't stack" (or is atleast pointless to cast).  If however an auto casted spell is missing
   * while another is present, then this function will return that it stacks.
   */
  public boolean willStack(Spell spell) {
    return getNoStackReason(spell) == null;
  }

  public String getNoStackReason(Spell spell) {
    if(spell != null) {
      Set<Spell> buffs = getSpellEffects();

      for(Spell buff : buffs) {
        if(buff.equals(spell) || !spell.willStack(buff)) {
//          System.out.println(">>> exit 1: vs " + buff);
          return "StackConflict(" + buff + ")";
        }
      }

      int sameAutoCastedSpellCount = 0;

      for(Spell autoCastedSpell : spell.getAutoCastedSpells()) {
 //       System.out.println(">>> autocasted = " + autoCastedSpell);
        for(Spell buff : buffs) {
       //   System.out.println(">>> buff = " + buff);
          if(!autoCastedSpell.willStack(buff)) {
            sameAutoCastedSpellCount++;  // non-stacking is counted as part of a set of auto casted spells that is considered 'fulfilled'
            break;
//            System.out.println(">>> exit 2: " + autoCastedSpell + " vs " + buff);
//            return false;
          }

          if(buff.equalOrOfDifferentRank(autoCastedSpell)) {
            sameAutoCastedSpellCount++;
            break;  // break because some spells like Blessing of Fervor can stack multiple times, but should only be counted once
          }
        }
      }

      if(sameAutoCastedSpellCount > 0 && sameAutoCastedSpellCount == spell.getAutoCastedSpells().size()) {
//        System.out.println(">>> exit 3: " + sameAutoCastedSpellCount + " vs " + spell.getAutoCastedSpells().size());
        return "StackSelf";
      }
    }

    return null;
  }

  public Spawn getMaster() {
    return session.getSpawn(masterId);
  }

  public int getDeity() {
    return deity;
  }

  /*
   * By arche type
   */

  public boolean isPureMelee() {
    return classId == 1 || classId == 7 || classId == 9 || classId == 16;
  }

  public boolean isHybrid() {
    return classId == 3 || classId == 4 || classId == 5 || classId == 8 || classId == 15;
  }

  public boolean isPureCaster() {
    return classId >= 11 && classId <= 14;
  }

  public boolean isPriest() {
    return classId == 10 || classId == 6 || classId == 2;
  }

  /*
   * By general type
   */

  public boolean isMelee() {
    return isHybrid() || isPureMelee();
  }

  public boolean isCaster() {
    return isPriest() || isPureCaster();
  }

  /*
   * By role
   */

  public boolean isTank() {
    return classId == 1 || classId == 3 || classId == 5;
  }

  public boolean isCC() {
    return isBard() || isEnchanter();
  }

  public boolean isMeleeDPS() {
    return !isTank() && (isPureMelee() || isHybrid());
  }

  public boolean isCasterDPS() {
    return isPureCaster() && !isCC();
  }

  /*
   * Specifics
   */

  public boolean isBard() {
    return classId == 8;
  }

  public boolean isEnchanter() {
    return classId == 14;
  }

  public boolean isCleric() {
    return classId == 2;
  }

  public boolean isShaman() {
    return classId == 10;
  }

  public boolean isOfClass(String... classes) {
    for(String cls : classes) {
      if(cls.equals(getClassShortName())) {
        return true;
      }
    }

    return false;
  }

  public boolean isManaUser() {
    return !isPureMelee() && !isBard();
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
      return getHitPointsPct() > 0 ? "WAR" : "?";
    }
  }

  public String getClassLongName() {
    switch(classId) {
    case 0: return "?";
    case 1: return "Warrior";
    case 2: return "Cleric";
    case 3: return "Paladin";
    case 4: return "Ranger";
    case 5: return "Shadow Knight";
    case 6: return "Druid";
    case 7: return "Monk";
    case 8: return "Bard";
    case 9: return "Rogue";
    case 10: return "Shaman";
    case 11: return "Necromancer";
    case 12: return "Wizard";
    case 13: return "Magician";
    case 14: return "Enchanter";
    case 15: return "Beastlord";
    case 16: return "Berzerker";
    default:// throw new RuntimeException("Unknown ClassID: " + classId);
      return "?";
    }
  }

  public boolean isMainAssist() {
    TargetModule targetModule = (TargetModule)session.getModule("TargetModule");

    if(targetModule != null) {
      Spawn mainAssist = targetModule.getMainAssist();

      return mainAssist != null && mainAssist.equals(this);
    }

    return false;
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

  public boolean isAlly() {
    return isBot() || isGroupMember() || isMe();
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
   * Distance to any member considered part of the group
   */
  public double getDistanceFromGroup(int maxDistanceToBeConsideredPartOfGroup) {
    Set<Spawn> nearbyGroupMembers = session.getMe().getNearbyGroupMembers(maxDistanceToBeConsideredPartOfGroup);
    double shortestDistance = Double.MAX_VALUE;

    for(Spawn member : nearbyGroupMembers) {
      double distance = getDistance(member);

      if(distance < shortestDistance) {
        shortestDistance = distance;
      }
    }

    //System.out.println(">>> " + this + ": " + shortestDistance + " : " + nearbyGroupMembers);

    return shortestDistance;
  }

  /**
   * Distance to me.
   */
  public double getDistance() {
    Spawn me = session.getMe();

    return getDistance(me.x, me.y);
  }

  public double getDistance(Spawn spawn) {
    return getDistance(spawn.x, spawn.y);
  }

  public double getDistanceToNearestExtendedTarget() {
    Me me = session.getMe();
    double nearestDistance = 1000;

    for(Spawn spawn : me.getExtendedTargets()) {
      if(spawn.isEnemy() && !spawn.equals(this) && getDistance(spawn) < nearestDistance) {
        nearestDistance = getDistance(spawn);
      }
    }

    return nearestDistance;
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

  public final int getHitPointsPct() {
    return hitPointsPct;
  }

  public final int getManaPct() {
    return manaPct;
  }

  public final int getEndurancePct() {
    return endurancePct;
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

  public int hasBuff(String name, int value) {
    return getBuffNames().contains(name) ? value : 0;
  }

  public Set<String> getBuffNames() {
    Set<String> names = new HashSet<>();

    for(SpellEffectManager manager : spellEffectManagers.values()) {
      if(manager.getMillisLeft() > 0) {
//        System.err.println(manager.getSpell().getName());
        names.add(manager.getSpell().getName());
      }
    }

    return names;
  }

  public SpellEffectManager getBuff(String name) {
    for(SpellEffectManager manager : spellEffectManagers.values()) {
      if(manager.getMillisLeft() > 0 && manager.getSpell().getName().equalsIgnoreCase(name)) {
        return manager;
      }
    }

    return null;
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

    if(deltaX * deltaX + deltaY * deltaY > 0.1) {
      return true;
    }
    return false;
  }

  public boolean isStandingStill() {
    return isStandingStill(450);
  }

  public boolean isStandingStill(long millis) {
    return System.currentTimeMillis() - lastMovementMillis > millis;
  }

  public double getUnitsMoved(long since) {  // run5 = 50 units / second
    Location lastLocation = null;
    double distanceMoved = 0;

    for(Location location : historicLocations.getMostRecentSubListCopy(since)) {
      if(lastLocation != null) {
        float deltaX = lastLocation.x - location.x;
        float deltaY = lastLocation.y - location.y;

        distanceMoved += Math.sqrt(deltaX * deltaX + deltaY * deltaY);
      }

      lastLocation = location;
    }

    return distanceMoved;
  }

  public boolean isNearAlly(int range) {
    for(Spawn ally : session.getGroupMembers()) {
      if(ally.getDistance(this) < range) {
        return true;
      }
    }

    for(Spawn ally : session.getBots()) {
      if(ally.getDistance(this) < range) {
        return true;
      }
    }

    return false;
  }

  /**
   * Determines if this character is pulling.  Assumptions:
   * - if character is nearby, then it is not pulling
   * - if we are in combat then we're not pulling
   * - if extended target is empty and the spawn is not moving, then we're not pulling
   *
   * @return true if this spawn is considered to be pulling
   */
  public boolean isPulling() {
    if(getDistance() <= 50 || session.getMe().inCombat() || isPet()) {
      return false;
    }

    if(session.getMe().getExtendedTargetCount() == 0 && !isMoving()) {
      return false;
    }

    return true;
  }

  public boolean isNamedMob() {
    return isEnemy() &&
        (name.matches(session.getTrueNameds()) || (fullName.startsWith("#") && !name.matches(session.getFalseNameds())));
  }

  public boolean isPriorityTarget() {
    return name.matches(session.getPriorityTargets());
  }

  public boolean isRaceMezzable() {
    return race != 435   // Dragon (solid looking)
        && race != 189   // Storm Giants
        && race != 188   // Frost Giants
        && race != 626;  // Rallosian Giants
  }

  public boolean isUnmezzable() {
    boolean isUnmezzableInGeneral = !name.matches(session.getMezzables()) && (isNamedMob() || !isRaceMezzable() || name.matches(session.getUnmezzables()));

    return isUnmezzableInGeneral
        || getLevel() > session.getMe().getLevel() + 3;  // TODO not perfect, assumes mobs are not mezzable if 3 or more levels above Me.
  }

  private boolean ignored;

  /**
   * Returns true if the target should be ignored.  Note that if the target is on
   * extended target, then it will no longer be ignored.
   *
   * @return true if the target should be ignored
   */
  public boolean isIgnored() {
    return ignored && !isExtendedTarget();
  }

  /**
   * Returns true if while pulling a target should be ignored (for example, because
   * it is friendly or simply cannot be agroed).
   *
   * @return true if the target should be ignored
   */
  public boolean isPullIgnored() {
    return name.matches(session.getPullIgnoreds());
  }

  public boolean isValidObjectTarget() {
    return getName().matches(session.getValidObjectTargets());
  }

  /**
   * Pets are generally shrunk, so ex-pets can be detected by that.
   */
  public boolean isExPet() {
    for(SpellEffectManager manager : spellEffectManagers.values()) {
      if(manager.isShrink()) {
        return true;
      }
    }

    return false;
  }

  public Spell getCastedSpell() {
    return casting;
  }

  public boolean isCasting() {
    return casting != null;
  }

  private boolean isUnderDirectAttack;
  private long isUnderDirectAttackLastUpdateMillis;
  private long lastTimeUnderDirectAttackMillis;

  /**
   * Returns <code>true</code> if this spawn is the focus of attack of one or
   * more enemies, otherwise <code>false</code>.  This is useful for determining
   * healing priority and allows for more reactive healing.
   *
   * @return <code>true</code> if this spawn is the focus of attack of one or more enemies
   */
  public boolean isUnderDirectAttack() {
    if(isUnderDirectAttackLastUpdateMillis + 3000 > System.currentTimeMillis()) {
      return lastTimeUnderDirectAttackMillis + 2000 > System.currentTimeMillis() ? true : isUnderDirectAttack;
    }

    /*
     * If bot data is not available or is old, fall back on a simplistic system.
     */

    if(isFriendly() && session.getMe().getExtendedTargetCount() > 0) {
      return true;
    }

    return false;
  }

  /**
   * Called by bot update events or from Me.  Both have a more accurate view on whether they are
   * under attack or not.
   *
   * @param isUnderDirectAttack state of attack
   */
  public void updateUnderDirectAttack(boolean isUnderDirectAttack) {
    this.isUnderDirectAttackLastUpdateMillis = System.currentTimeMillis();
    this.isUnderDirectAttack = isUnderDirectAttack;

    if(isUnderDirectAttack) {
      lastTimeUnderDirectAttackMillis = this.isUnderDirectAttackLastUpdateMillis;
    }
  }

  public boolean isPC() {
    return getType() == SpawnType.PC;
  }

  /**
   * @return <code>true</code> if spawn is alive and a PC or a PC pet
   */
  public boolean isFriendly() {
    Spawn master = getMaster();

    return master != null ? master.isFriendly() : getType() == SpawnType.PC;
  }

  /**
   * @return <code>true</code> if spawn is alive and an NPC or an NPC pet
   */
  public boolean isEnemy() {
    Spawn master = getMaster();

    if(getType() == SpawnType.OBJECT || isValidObjectTarget()) {
      return true;
    }

    if(isIgnored() || (master != null && master.isIgnored())) {
      return false;
    }

    return getType() == SpawnType.NPC &&
        (master == null || master.getType() == SpawnType.NPC || master.getType() == SpawnType.NPC_CORPSE);
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
      if(manager.getMillisLeft() > 0) {
        DebuffCounter debuffCounters = manager.getSpell().getRawSpellData().getDebuffCounters();

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

  public int getDamageOverTime() {
    int dot = 0;

    for(SpellEffectManager manager : spellEffectManagers.values()) {
      if(manager.getMillisLeft() > 0) {
        dot += manager.getSpell().getDamageOverTime();
      }
    }

    return dot;
  }

  public void increaseTankTime(long millis) {
    tankTime += millis;
  }

  //                                       level 0    5   10   15   20   25    30    35    40    45    50    55    60    65     70     75     80     85     90     95    100     105     110
  private static final int[] MAX_HP_BY_LEVEL5 = {0, 100, 200, 400, 600, 800, 1000, 1200, 1400, 1700, 2000, 2500, 3000, 7500, 11000, 17000, 25000, 35000, 50000, 65000, 85000, 110000, 140000};

  public boolean needsCure(DebuffCounter.Type... types) {
    List<Type> asList = Arrays.asList(types);

    for(SpellEffectManager manager : spellEffectManagers.values()) {
      if(manager.getSpell().isDetrimental() && manager.getMillisLeft() > 0) {
        DebuffCounter debuffCounters = manager.getSpell().getRawSpellData().getDebuffCounters();

        if(debuffCounters != null && asList.contains(debuffCounters.getType())) {

          /*
           * Determined that there is something cureable on this target, now see if
           * it is worth curing.
           *
           * Note: the level of the spell used here is the character level, but this
           * is not correct when cast by an NPC... there's little that can be done.
           */

          SpellData sd = manager.getSpell().getRawSpellData();

          EffectDescriptor damageEffect = sd.getEffect(Attribute.DAMAGE);

          if(damageEffect != null && damageEffect.getCalculatedBase1(getLevel()) < -MAX_HP_BY_LEVEL5[getLevel() / 5] / 10) {
            return true;
          }

          EffectDescriptor manaEffect = sd.getEffect(Attribute.MANA);
//System.out.println(">>> manaEffect " + (manaEffect != null ? manaEffect.getCalculatedBase1(getLevel()) : ""));
          if(manaEffect != null && manaEffect.getCalculatedBase1(getLevel()) < -MAX_HP_BY_LEVEL5[getLevel() / 5] / 60) {
            return true;
          }

          EffectDescriptor reverseDamageShieldEffect = sd.getEffect(Attribute.REVERSE_DAMAGE_SHIELD);

          if(reverseDamageShieldEffect != null && reverseDamageShieldEffect.getBase1() < -getLevel() * 5) {
            return true;
          }

          EffectDescriptor mezEffect = sd.getEffect(Attribute.MESMERIZE);

          if(mezEffect != null) {
            return true;
          }
        }
      }
    }

    return false;
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

    for(long t = 0; t <= range - period; t += 250) {
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

  public boolean hasTimeToLiveData() {
    return TTL_ANALYZER.hasTimeToLiveData(this);
  }

  public int getMobTimeToLive() {
    return TTL_ANALYZER.getTimeToLive(this);
  }

  public int getTimeToLive() {
    return getTimeToHitPointsPct(0);
  }

  public int getMinTimeToLive() {
    return getMinTimeToHitPointsPct(0);
  }

  private int previousHitPointsPct = 100;

  public void updateTTL() {
    int delta = previousHitPointsPct - hitPointsPct;

    if(delta > 0) {
//      System.err.println("Adding : " + delta + " -- " + hitPointsPct);
      damageHistory.add(damageHistory.getMostRecent() + delta);
    }

    previousHitPointsPct = hitPointsPct;
  }

  private Source healthSource = Source.SPAWN_LIST;
  private long healthLastUpdateMillis;

  public enum Source {
    SPAWN_LIST(0),
    BOT(1),
    DIRECT(2);

    private final int priority;

    Source(int priority) {
      this.priority = priority;
    }

    public int getPriority() {
      return priority;
    }
  }

  public void updateHealth(int hitPointsPct, Source source) {
    if(healthSource.getPriority() <= source.getPriority() || healthLastUpdateMillis + 3000 < System.currentTimeMillis()) {
      healthSource = source;
      healthLastUpdateMillis = System.currentTimeMillis();

      if(hitPointsPct > 100) {
        this.hitPointsPct = 100;
      }
      else if(hitPointsPct < 0) {
        this.hitPointsPct = 0;
      }
      else {
        this.hitPointsPct = hitPointsPct;
      }
    }
  }

  public void updateMana(int manaPct) {
    this.manaPct = manaPct;
  }

  public void updateEndurance(int endurancePct) {
    this.endurancePct = endurancePct;
  }

  private boolean targetIsExtendedTarget;

  public void updateTarget(int targetId, boolean isExtendedTarget) {
    this.targetId = targetId;
    this.targetIsExtendedTarget = isExtendedTarget;
  }

  public boolean isTargetExtendedTarget() {
    return targetIsExtendedTarget;
  }

  private long lastBuffUpdateMillis;

  public long getLastBuffUpdateMillis() {
    return lastBuffUpdateMillis;
  }

  private Source buffsSource = Source.SPAWN_LIST;
  private long buffsLastUpdateMillis;

  public void updateBuffsAndDurations(String buffsStr, boolean includingShortBuffs, Source source) {
    if(buffsSource.getPriority() <= source.getPriority() || buffsLastUpdateMillis + 3000 < System.currentTimeMillis()) {
      buffsSource = source;
      buffsLastUpdateMillis = System.currentTimeMillis();

      if(!buffsStr.trim().isEmpty()) {
        lastBuffUpdateMillis = System.currentTimeMillis();

        Set<Integer> toBeRemoved = new HashSet<>(spellEffectManagers.keySet());

        if(!includingShortBuffs) {
          for(Iterator<Integer> iterator = toBeRemoved.iterator(); iterator.hasNext();) {
            int spellId = iterator.next();

            if(session.getSpell(spellId).getRawSpellData().isShortBuff()) {
              iterator.remove();
            }
          }
        }

        for(String buffStr : buffsStr.trim().split(" ")) {
          String[] buffParts = buffStr.split(":");

          int spellId = Integer.parseInt(buffParts[0]);
          int secondsLeft = Integer.parseInt(buffParts[1]);

          if(secondsLeft == -6) { // Permanent
            secondsLeft = 60 * 60;
          }

          if(spellId > 0) {
            SpellEffectManager manager = getSpellEffectManager(spellId);

            if(secondsLeft >= 0) {
              toBeRemoved.remove(spellId);

              manager.setMillisLeft(secondsLeft * 1000 + 1000);
            }
          }
        }

        for(int spellId : toBeRemoved) {
          SpellEffectManager manager = getSpellEffectManager(spellId);

          manager.clear();
        }
      }
    }
  }

  /**
   * A list of the last 120 locations of this spawn, in order of new to old.
   *
   * @return a list of the last 120 locations
   */
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
    if(x != newX || y != newY) {
      lastMovementMillis = System.currentTimeMillis();
    }

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
      if((locations.size() > 120 && !isAlly()) || locations.size() > 1200) {
        locations.removeLast();
      }

      notifyLocationListeners(loc);

      historicLocations.add(loc);
    }
  }

  private void notifyLocationListeners(Location loc) {
    for(LocationListener listener : locationListeners) {
      listener.updateLocation(loc);
    }
  }

  public boolean isExtendedTarget() {
    return session.getMe().isExtendedTarget(this);
  }

  private final TreeMap<Integer, Long> healthTimes = new TreeMap<>();

  private static final Pattern NEG_10000 = Pattern.compile("-1\\.#J");
  private static final Pattern CLEAN_NUMBERS = Pattern.compile("[#0-9]");
  private static final Pattern CLEAN_UNDERSCORE = Pattern.compile("_");

  protected void updateSpawn(String infoParameter) {
    String info = NEG_10000.matcher(infoParameter).replaceAll("-10000"); // TODO may be broken now
    Matcher matcher = PATTERN.matcher(info);

    if(matcher.matches()) {
      boolean wasAlive = isAlive();
      boolean wasFriendly = isFriendly();

      int type = Integer.parseInt(matcher.group(7), 16);

      if(infoParameter.contains("a_spider_cocoon")) {
      //  System.out.println(infoParameter);
      }
      // Pattern.compile(regex).matcher(this).replaceAll(replacement)
      this.fullName = matcher.group(22);
      String name = CLEAN_UNDERSCORE.matcher(CLEAN_NUMBERS.matcher(fullName).replaceAll("")).replaceAll(" ");
//      String name = fullName.replaceAll("[#0-9]", "").replaceAll("_", " ");

//      if(type == 2 || type == 3) {
//        name += "'s corpse";
//      }


      updateStats(Integer.parseInt(matcher.group(1), 16));

      if(!isMe()) {
        // Health/Mana/End returned for Me is actual hitpoints, not a percentage
        updateHealth(Integer.parseInt(matcher.group(2), 16), isExtendedTarget() || isGroupMember() || (getMaster() != null && getMaster().isGroupMember()) ? Source.DIRECT : Source.SPAWN_LIST);

        updateMana(Integer.parseInt(matcher.group(3), 16));
        updateEndurance(Integer.parseInt(matcher.group(4), 16));
      }

      int classId = Integer.parseInt(matcher.group(5), 16);
      int deity = Integer.parseInt(matcher.group(6), 16);
      this.standState = Integer.parseInt(matcher.group(8), 16);
      updateLocation(
        Integer.parseInt(matcher.group(9), 16) / 100.0f,
        Integer.parseInt(matcher.group(10), 16) / 100.0f,
        Integer.parseInt(matcher.group(11), 16) / 100.0f,
        Integer.parseInt(matcher.group(12), 16) / 100.0f
      );
//      this.speedRun = Float.parseFloat(matcher.group(13));

      this.masterId = Integer.parseInt(matcher.group(15), 16);
      this.petId = Integer.parseInt(matcher.group(16), 16);
      int bodyType = Integer.parseInt(matcher.group(17), 16);
      int race = Integer.parseInt(matcher.group(18), 16);
      this.lineOfSight = matcher.group(19).equals("1");
      boolean mercenary = matcher.group(20).equals("1");

      if(!name.equals(this.name) || type != this.type || classId != this.classId || deity != this.deity || bodyType != this.bodyType || race != this.race || mercenary != this.mercenary) {
        updateFixedStats(name, type, classId, deity, bodyType, race, mercenary);
      }

      Spawn target = getTarget();

      if(target != null && !(target.isGroupMember() || target.isBot())) {
        target.updateTarget(Integer.parseInt(matcher.group(21), 16), false);
      }

      int castingID = Integer.parseInt(matcher.group(14), 16);

      casting = castingID <= 0 ? null : session.getSpell(castingID);

      if(isAlive() || wasAlive) {
        healthTimes.put(!isAlive() ? 0 : getHitPointsPct(), System.currentTimeMillis());
      }

      if(wasAlive && !isAlive()) {
        timeOfDeath = new Date();

        if(healthTimes.size() > 10 && healthTimes.higherKey(95) != null) {
          TTL_ANALYZER.submit(new TreeMap<>(healthTimes), getLevel(), getName());
        }
      }

      if(wasFriendly && !isFriendly()) {

        /*
         * This might be a charm wearing off.  Remove all charm spells.
         */

        for(Iterator<Integer> iterator = spellEffectManagers.keySet().iterator(); iterator.hasNext();) {
          int spellId = iterator.next();

          if(session.getSpell(spellId).isCharm()) {
            iterator.remove();
          }
        }
      }
    }
    else {
      System.err.println("WARNING: Unable to parse: " + infoParameter);
    }
  }

  public void updateStats(int level) {
    this.level = level;
  }

  public void updateFixedStats(String name, int type, int cls, int deity, int bodyType, int race, boolean mercenary) {
    this.cachedSpawnType = determineType(name, type, cls, deity, bodyType, race, mercenary);
    this.ignored = name.matches(session.getIgnoreds());

    this.name = name;
    this.type = type;
    this.classId = cls;
    this.deity = deity;
    this.bodyType = bodyType;
    this.race = race;
    this.mercenary = mercenary;
  }

  private static final TimeToLiveAnalyzer TTL_ANALYZER = new TimeToLiveAnalyzer();

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

  public SpellEffectManager getActiveEffect(EffectType effectType) {
    SpellEffectManager bestEffectManager = null;
    long bestDuration = 0;

    for(SpellEffectManager manager : spellEffectManagers.values()) {
      long duration = manager.getMillisLeft();

      if(duration > bestDuration && manager.getSpell().getEffectTypes().contains(effectType)) {
        bestDuration = duration;
        bestEffectManager = manager;
      }
    }

    return bestEffectManager;
  }

  public EffectDescriptor getActiveSpellEffect(Attribute spellEffect) {
    EffectDescriptor bestEffect = null;
    long bestDuration = 0;

    for(SpellEffectManager manager : spellEffectManagers.values()) {
      long duration = manager.getMillisLeft();

      if(duration > bestDuration) {
        EffectDescriptor effect = manager.getEffectDescriptor(spellEffect);

        if(effect != null) {
          bestDuration = duration;
          bestEffect = effect;
        }
      }
    }

    return bestEffect;
  }

  public SpellEffectManager getEffect(Attribute spellEffect) {
    SpellEffectManager bestEffect = null;
    long bestDuration = 0;

    for(SpellEffectManager manager : spellEffectManagers.values()) {
      long duration = manager.getMillisLeft();

      if(duration > bestDuration) {
        EffectDescriptor effect = manager.getEffectDescriptor(spellEffect);

        if(effect != null) {
          bestDuration = duration;
          bestEffect = manager;
        }
      }
    }

    return bestEffect;
  }

  private static final Pattern DISPELLABLE_BENEFICIAL_BUFFS = Pattern.compile("(Talisman of Bolstering|Cloak of Warspikes|Shout of the Warrider|Virtue of War|Rallos' Imperishable Bulwark|Bastion of War|Strife's Reflection|Counterattack)");

  public boolean hasBeneficialBuffs() {
    for(SpellEffectManager manager : spellEffectManagers.values()) {
      long duration = manager.getMillisLeft();

      if(duration > 0 && !manager.getSpell().isDetrimental()
          && DISPELLABLE_BENEFICIAL_BUFFS.matcher(manager.getSpell().getName()).matches()) {
        return true;
      }
    }

    return false;
  }

  public boolean hasGiftOfMana() {
    for(String name : getBuffNames()) {
      if(name.startsWith("Gift of ") && name.endsWith(" Mana")) {
        return true;
      }
    }

    return false;
  }

  /**
   * Returns all effects present on this spawn.
   *
   * @return all effects present on this spawn
   */
  public Set<Spell> getSpellEffects() {
    Set<Spell> buffs = new HashSet<>();

    for(SpellEffectManager manager : spellEffectManagers.values()) {
      if(manager.getMillisLeft() > 0) {
        buffs.add(manager.getSpell());
      }
    }

    return buffs;
  }

  public boolean isMezzed() {
    for(Spell spell : getSpellEffects()) {
      if(spell.isMez() && getSpellEffectManager(spell).getSecondsLeft() >= 12) {
        return true;
      }
    }

    return false;
  }

  public boolean isSlowed() {
    for(Spell spell : getSpellEffects()) {
      if(spell.isSlow()) {
        return true;
      }
    }

    return false;
  }

  public boolean isValidTypeFor(Effect effect) {
    TargetType targetType = effect.getTargetType();

    return (
      (targetType == TargetType.SELF && isMe() && isAlive()) ||
      (targetType == TargetType.SINGLE || targetType == TargetType.BOLT || targetType == TargetType.BEAM || targetType == TargetType.PBAE || targetType == TargetType.TARGETED_GROUP || targetType == TargetType.TARGETED_AE) && isAlive() && isEnemy() == effect.isDetrimental()) ||
      (targetType == TargetType.CORPSE && getType() == SpawnType.PC_CORPSE) ||
      (targetType == TargetType.GROUP && isGroupMember()
    );  // TODO needs to take targetted group spells into account, just being friendly is not enough
  }

  /**
   * Checks if spell would hold on the target.
   */
  public boolean isValidLevelFor(Spell spell) {
    if(spell.isDetrimental()) {
      if(getLevel() > spell.getMaxTargetLevel()) {
        return false;
      }

      return true;
    }
    else {
      if(spell.getDuration() == 0) {
        return true;  // Spells with no duration always work
      }

      if(spell.getLevel() < 50 || spell.getLevel() >= 250) {
        return true;  // Level 1-49 spells hold on everyone
      }

      if(getLevel() < 61 && spell.getLevel() > 65) {
        return false; // Level 66+ Spells never hold on targets below level 61
      }

      // TODO the 2 rules below are basically the same, with a one level difference.  Need to test if a 55 spell can hold on 42 or 61 on 45 or 63 on 46.
      if(getLevel() > 60 && 93 + (getLevel() - 61) * 2 >= spell.getLevel()) {
        // If target is level 61+ then spells below level 94 will hold.  Beyond that:
        // Assumption: Lvl 93 spell holds on 61, lvl 94 doesn't
        // Assumption: Lvl 95 spell holds on 62, lvl 96 doesn't
        // Assumption: Lvl 97 spell holds on 63, lvl 98 doesn't
        return true;
      }

      if(spell.getLevel() < 66 && 50 + (getLevel() - 40) * 2 >= spell.getLevel()) {
        // For Level 50 - 65 spells there is a special level based rule based on:
        // Assumption: Clarity II(54) holds on level 42
        // Assumption: Aegolism(60) holds on Level 45
        // Assumption: Virtue(62) holds on Level 46
        return true;
      }

      return false;
    }
  }

  private final VariableContext variableContext = new VariableContext();

  public VariableContext getContext() {
    return variableContext;
  }

  @Override
  public String toString() {
    return "Spawn(\"" + name + "\", " + id + ";hp=" + getHitPointsPct() + ";ttl=" + getTimeToLive() + "(" + getMobTimeToLive() + "))";
  }
}
