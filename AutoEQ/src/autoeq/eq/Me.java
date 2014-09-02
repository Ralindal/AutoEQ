package autoeq.eq;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import autoeq.effects.Effect;
import autoeq.effects.Effect.Type;
import autoeq.expr.Parser;
import autoeq.expr.SyntaxException;
import autoeq.modules.target.TargetModule;

public class Me extends Spawn {
  private static final int SPELL_GEMS = 12;

//  private final SpellProperty[] gems;
  private final Spell[] gems = new Spell[SPELL_GEMS];
  private final boolean[] gemReadyList = new boolean[SPELL_GEMS];
  private final long[] getTimers = new long[SPELL_GEMS];
  private final List<Integer> extendedTargetIDs = new ArrayList<>();
  private final List<Integer> extendedTargetAggro = new ArrayList<>();
  private final List<Integer> extendedTargetType = new ArrayList<>();

  private final List<Integer> lruSpellSlots = new LinkedList<>();

  private final HistoryValue<Integer> manaHistory = new HistoryValue<>(7500);
  private final HistoryValue<Integer> dmgHistory = new HistoryValue<>(10000);

  private final int maxSpellSlots;
  private final Group group;

  private String meleeStatus;
  private String cursor;

  private int mana;
  private int maxMana;
  private int hitPoints;
  private int maxHitPoints;
  private int endurance;
  private int maxEndurance;
  private int combatState;
  private int weight;
  private int xp;
  private int aaxp;
  private int aaSaved;
  private int aaCount;
  private boolean invisible;
//  private int castingID;
  private boolean activelyCasting;

  private int aggro;
  private int secondaryAggro;
  private Spawn secondary;

  private long mobLastGateCastMillis;
  private long lastCastStartMillis;
  private long lastCastMillis;
  private long lastOOCMillis;

  private int runningCastID;
  private int runningDiscID;

  public Me(EverquestSession session, int id) {
    super(session, id);

    group = new Group(session);
    maxSpellSlots = session.getCharacterDAO().getSpellSlots();

    for(int i = 1; i <= maxSpellSlots; i++) {
      lruSpellSlots.add(i);
    }

    session.addChatListener(new ChatListener() {
      private final Pattern PATTERN = Pattern.compile(".* begins to cast the gate spell\\.");

      @Override
      public Pattern getFilter() {
        return PATTERN;
      }

      @Override
      public void match(Matcher matcher) {
        mobLastGateCastMillis = System.currentTimeMillis();
      }
    });

    session.addChatListener(new ChatListener() {
      private final Pattern PATTERN = Pattern.compile(".* YOU for ([0-9]+) points of damage\\.");

      @Override
      public Pattern getFilter() {
        return PATTERN;
      }

      @Override
      public void match(Matcher matcher) {
        dmgHistory.add(Integer.parseInt(matcher.group(1)));
      }
    });

//    session.registerExpression("${Melee.DiscID}", new ExpressionListener() {
//      @Override
//      public void stateUpdated(String result) {
//        runningDiscID = Integer.parseInt(result);
//      }
//    });
  }

  public String getMeleeStatus() {
    return meleeStatus;
  }

  public String getCursor() {
    return cursor;
  }

  /*
   * Actions
   */

  private final Map<Integer, Integer> lockedSpellSlots = new HashMap<>();  // Slot Number, Priority

  public void lockSpellSlot(int slot, int lockPriority) {
    Integer currentLockPriority = lockedSpellSlots.get(slot);

    if(currentLockPriority == null || currentLockPriority < lockPriority) {
      lockedSpellSlots.put(slot, lockPriority);
    }
  }

  public void unlockAllSpellSlots() {
    lockedSpellSlots.clear();
  }

  private int getPreferredGem(int priority) {
//    List<String> possibleSlots = Arrays.asList(defaultGems.split(" "));

    for(int slot = 1; slot <= maxSpellSlots; slot++) {
      if(getGem(slot) == null) {
        return slot;
      }
    }

    int bestSlot = 0;
    int bestPriority = Integer.MAX_VALUE;

    for(int slot : session.getMe().getLRUSlots()) {
      Integer lockPriority = lockedSpellSlots.get(slot);

      if(lockPriority == null) {
        lockPriority = Integer.MIN_VALUE;
      }

      if(lockPriority < priority && lockPriority < bestPriority) {
        bestSlot = slot;
        bestPriority = lockPriority;
      }
    }

    if(bestSlot != 0) {
      session.getMe().updateLRUSlots(bestSlot);
    }

    return bestSlot;
  }

  /**
   * Memorizes a spell into a spell slot.  If slot is 0, the least recently used spell slot will be used.
   */
  public boolean memorize(Gem... gems) {
    boolean first = true;
    boolean bookNeedsClosing = false;

    for(Gem gem : gems) {
      final int slot = gem.getSlot() != 0 && gem.getSlot() <= maxSpellSlots ? gem.getSlot() : getPreferredGem(gem.getPriority());

      if(slot != 0) {
        final Spell spell = gem.getSpell();

        if(first) {
          session.doCommand("/autoinventory");

          session.doCommand("/book");
          session.delay(1000, "${Window[SpellBookWnd].Open}");

          bookNeedsClosing = true;
        }

        first = false;

        session.log("Memorizing spell: " + spell.getName());
        session.doCommand("/memspell " + slot + " \"" + spell.getName() + "\"");
        session.delay(1000, new Condition() {
          @Override
          public boolean isValid() {
            return Me.this.gems[slot - 1] == null;
          }
        });
        session.delay(5000, new Condition() {
          @Override
          public boolean isValid() {
            return spell.equals(Me.this.gems[slot - 1]);
          }
        });

  //      session.doCommand("/memorize " + spellId + "|gem" + slot);
  //      session.delay(1000, new Condition() {
  //        @Override
  //        public boolean isValid() {
  //          return session.translate("${Cast.Status}").contains("M");
  //        }
  //      });
  //      session.delay(5000, new Condition() {
  //        @Override
  //        public boolean isValid() {
  //          return session.translate("${Cast.Status}").equals("I");
  //        }
  //      });
  //
  //      gems[slot - 1] = null;
      }
    }

    if(bookNeedsClosing) {
      session.doCommand("/book close");
    }

    return bookNeedsClosing;
  }

  /**
   * Checks whether a spell is ready to cast.  Checks if the spell is memmed and we're not casting.
   */
  public boolean isSpellReady(Spell spell) {
    int gem = getGem(spell);

    if(gem > 0 && !isCasting()) {
      if(gemReadyList[gem - 1] ||
          (isBard() && getTimers[gem - 1] == 0 && !wasCasting(200))) {
        if(getMana() >= spell.getMana() && manaHistory.getValue(3000) >= spell.getMana()) {
          return true;
        }
      }
    }

    return false;
  }

  /**
   * Returns the time in ms before a spell is available.
   *
   * @param spell a spell gem number
   * @return the time in ms before a spell is available
   */
  public long getGemReadyMillis(int gem) {
    if(gem > 0) {
      return getTimers[gem - 1];
    }

    return 60 * 60 * 1000;  // one hour if not memmed
  }

  public void activateEffect(SpellLine spellLine, final Effect effect, List<Spawn> targets) {
    activateEffect(spellLine, effect, targets, 0);
  }

  public void activateEffect(SpellLine spellLine, final Effect effect, List<Spawn> targets, double priority) {
    Spawn intendedTarget = targets.get(0);
    Spawn currentTarget = getTarget();
    long startTime = System.currentTimeMillis();

//    final List<Semaphore> semaphores = new ArrayList<>();
//
//    semaphores.add(session.getCastSemaphore());
//
//    if(!isBard()) {
//      semaphores.add(session.getMovementSemaphore());
//    }
//    if(effect.isTargeted()) {
//      semaphores.add(session.getTargetSemaphore());
//    }
//
//    for(int i = 0; i < semaphores.size(); i++) {
//      if(!semaphores.get(i).tryAcquire()) {
//        for(int j = 0; j < i; j++) {
//          semaphores.get(j).release();
//        }
//
//        throw new ResourceUnavailableException();
//      }
//    }


    if(isBard() && effect.getType() == Type.SONG) {
      session.doCommand("/stopcast");
    }
    else if(!isStandingStill() && effect.getCastTime() > 0 && !isBard()) {
      session.echo("Halting movement @ " + (System.currentTimeMillis() - startTime) + " ms");
      session.doCommand("/nomod /keypress forward");
      session.doCommand("/nomod /keypress left hold");
      session.delay(250);
      session.doCommand("/nomod /keypress left");
      session.delay(500, new Condition() {
        @Override
        public boolean isValid() {
          return isStandingStill();
        }
      });
      session.delay(1000);
      session.echo("Standing still @ " + (System.currentTimeMillis() - startTime) + " ms");
    }

    /*
     * Change Target for spell if the spell is a targeted spell and the current target is wrong.
     * For spells with a default target, target is changed if the intended target is a PC not
     * part of our group, or the current target is a PC not part of our group.
     */

    if(effect.getTargetType().isTargeted() && currentTarget != intendedTarget) {
      if(!effect.getTargetType().hasDefaultTarget()
          || !intendedTarget.isGroupMember()
          || (currentTarget != null && currentTarget.isPC() && !currentTarget.isGroupMember())) {
        session.doCommand("/target id " + intendedTarget.getId());
        session.delay(1000, "${Target.ID} == " + intendedTarget.getId());
        currentTarget = intendedTarget;
      }
    }

    if(effect.requiresStanding() && !isStanding()) {
      session.doCommand("/stand");
    }

    if(effect.getType() == Type.ABILITY && effect.getSpell().getName().equals("Beguiler's Directed Banishment")) {

      /*
       * Determine where it was when it was still 120 distance away:
       */

      Location targetLocation = null;
      Spawn mob = targets.get(0);

      for(Location location : mob.getLastLocations()) {
        double distance = getDistance(location.x, location.y);

        if(targetLocation == null || distance > getDistance(targetLocation.x, targetLocation.y)) {
          targetLocation = location;
        }

        if(distance > 120) {
          break;
        }
      }

      System.out.println(">>> BANISHMENT: Banishing towards " + targetLocation + ": " + mob);

      /*
       * Face the direction in the line of current location and the desired location:
       */

      if(targetLocation != null) {
        session.doCommand(String.format("/face fast nolook loc %.2f,%.2f", targetLocation.y - mob.getY() + getY(), targetLocation.x - mob.getX() + getX()));
      }
      else {
        System.out.println(">>> BANISHMENT: No target location for: " + mob);
      }
    }
    else if(effect.getTargetType() == TargetType.BEAM) {
      session.doCommand(String.format("/face fast nolook loc %.2f,%.2f", targets.get(0).getY(), targets.get(0).getX()));
    }

    try {
      if(effect.getType() != Type.DISCIPLINE && effect.getType() != Type.COMMAND && effect.getType() != Type.MELEE_ABILITY) {
        session.getCastResultMonitor().addCurrentCastingSpell(spellLine, effect, targets);
      }

      boolean manaHarvestReady = isEffectTypeReady(EffectType.MANA_HARVEST);
      effect.activate();

      setLastCastStartMillis();  // For instant spells, because update from MQ can take a pulse

      if(effect.getType() != Type.DISCIPLINE && effect.getType() != Type.COMMAND && effect.getType() != Type.MELEE_ABILITY) {
        int castingID = effect.getSpell().getId();  // ID of spell that was cast, initialized here with the intended ID for instant spells only.

        long currentTimeMillis = System.currentTimeMillis();

        String info = "";

        if(spellLine != null) {
          info = spellLine.getName();
          info = info.substring(info.indexOf(".") + 1);

          int indexOfHash = info.indexOf("#");

          if(indexOfHash > 0) {
            info = info.substring(0, indexOfHash);
          }

          info = "[" + info + "]";
        }

        String special = (effect.willUseGOM() ? "\\ay[GOM]\\a-w" : "")
            + (getActiveEffect(EffectType.TWIN_CAST) != null ? "\\ag[TC]\\a-w" : "")
            + (getActiveEffect(EffectType.TWIN_HEAL) != null ? "\\at[TH]\\a-w" : "")
            + (manaHarvestReady ? "\\aw[H]\\a-w" : "")
            + (intendedTarget != null && intendedTarget.isUnderDirectAttack() ? "\\ao[A]\\a-w" : "");

        session.echo(String.format("\\ay[Cast]%s%s %s%s %s%s%s%s p=%5.1f",
          doubleSpaces(String.format("\\aw_%5.1f-%-5.1f\\a-w_%+3.1fs", (double)(currentTimeMillis - lastOOCMillis) / 1000, (double)(currentTimeMillis - lastOOCMillis + (effect.getType() != Effect.Type.SONG && effect.getType() != Effect.Type.SPELL ? 0 : 1500) + effect.getCastTime()) / 1000, (double)effect.getCastTime() / 1000)),
//          doubleSpaces(String.format("\\ar_%3d\\at_%3d\\ay_%3d", getHitPointsPct(), getManaPct(), getEndurancePct())),
          //doubleSpaces(String.format("\\ar_%3d\\at_%3d", getHitPointsPct(), getManaPct())),
          doubleSpaces(String.format("\\ar_%2s\\at_%2s", getHitPointsPct() == 100 ? "FH" : "" + getHitPointsPct(), getManaPct() == 100 ? "FM" : "" + getManaPct())),
          special.isEmpty() ? "" : special + " ",
          getEffectTypeColor(effect.getType()) + effect.getSpell().getName() + "\\a-w" + (!effect.getSpell().isDetrimental() && intendedTarget != null ? " > \\ag" + intendedTarget.getName() + "\\a-w(" + intendedTarget.getHitPointsPct() + ")" : ""),
//          (double)(currentTimeMillis - timeOfUpdate) / 1000,
          info,
          " (${Math.Calc[${EverQuest.CurrentTimeMillis}-" + startTime + "].Int} ms lag)",
          getAggro() > 20 ? " a=" + getAggro() : "",
          effect.getSpell().isDetrimental() && intendedTarget != null ? " ttl=" + intendedTarget.getMobTimeToLive() : "",
          priority
        ));

        if(effect.getCastTime() > 0) {
          // Wait until we've started the cast
          session.delay(1000, new Condition() {
            @Override
            public boolean isValid() {
              return Me.this.getCastedSpell() != null && Me.this.getCastedSpell().getId() == effect.getSpell().getId();
            }
          });

//          castingID = getCastingID();

//          if(isBard()) {
//            // Wait until we've ended the cast
//            session.delay(3000);  // TODO shouldn't this check the ACTUAL cast time??
//            session.getCastResultMonitor().finishedCastingLastSpell();
//
////            lastCastMillis = System.currentTimeMillis();
//          }
//          else {
//            session.delay(30000, new Condition() {
//              @Override
//              public boolean isValid() {
//                return Me.this.getCastingID() == 0;
//              }
//            });
//          }
        }

        if(effect.getType() == Type.SPELL || effect.getType() == Type.SONG) {
          updateLRUSlots(getGem(effect.getSpell()));
        }

        /*
         * Get the result
         */

//        if(isBard() && !effect.getSpell().isDetrimental()) {
//          return "CAST_SUCCESS";
//        }
//
//        session.delay(300 + (effect.getCastTime() < 500 ? 500 - effect.getCastTime() : 0), new Condition() {
//          @Override
//          public boolean isValid() {
//            return getCastResult() != CastResult.SUCCESS;
//          }
//        });
//
//        CastResult result = getCastResult();
//
//        if(effect.getSpell().getId() != castingID) {
//          if(result != CastResult.DID_NOT_TAKE_HOLD && result != CastResult.CANNOT_SEE_TARGET) {
//            session.log("Mismatched cast, expected " + effect.getSpell() + ", but got: " + (castingID == 0 ? "nothing" : session.getSpell(castingID)));
//
//            return "CAST_INTERRUPTED";
//          }
//        }
//
//        session.log(effect.getSpell() + " -> " + result.getCode());
//
//        return result.getCode();
      }

      if(spellLine != null && spellLine.isAnnounce()) {
        String prefix = spellLine.getAnnounceChannelPrefix();

        if(effect.getSpell().getTargetType() == TargetType.PBAE) {
          session.doCommand(prefix + " Casting AE " + effect.getSpell().getName());
        }
        else if(effect.getSpell().getDuration() > 0 && effect.getTargetType().hasDefaultTarget() && currentTarget != null && currentTarget.isPC() && !currentTarget.isGroupMember()) {
          session.doCommand(prefix + " " + effect.getSpell().getName() + " on %t's group !!");
        }
        else if(effect.getSpell().getTargetType().isTargeted() && !effect.getTargetType().hasDefaultTarget()) {
          session.doCommand(prefix + " " + effect.getSpell().getName() + " on %t !!");
        }
        else {
          session.doCommand(prefix + " " + effect.getSpell().getName() + " !!");
        }
      }

      if(spellLine != null) {
        session.doActions(spellLine.getPostActions(), intendedTarget, null, null);
      }
    }
    finally {
    }

    //return "CAST_SUCCESS";
  }

  private static String doubleSpaces(String input) {
    return input.replaceAll(" ", "  ").replaceAll("_", " ");
  }

  private static String getEffectTypeColor(Effect.Type type) {
    return type == Effect.Type.SPELL      ? "\\ao" :
           type == Effect.Type.ABILITY    ? "\\at" :
           type == Effect.Type.DISCIPLINE ? "\\ay" :
           type == Effect.Type.ITEM       ? "\\aw" :
           type == Effect.Type.SONG       ? "\\ap" : "\\ag";
  }

  public void setLastCastStartMillis() {
    lastCastStartMillis = System.currentTimeMillis();
  }

  @Override
  public boolean isCasting() {
    long currentTime = System.currentTimeMillis();

    return currentTime - lastCastStartMillis < 400 || currentTime - lastCastMillis < 50 || activelyCasting;
  }

  public boolean wasCasting(int lastMillis) {
    return System.currentTimeMillis() - lastCastMillis < lastMillis || isCasting();
  }

  public boolean isMounted() {
    return getActiveEffect(EffectType.MOUNTED) != null;
  }

  /**
   * @param gem gem number, 1-12
   * @return Spell memmed in the given gem or null if none
   */
  public Spell getGem(int gem) {
    return gems[gem - 1];
  }

  /**
   * @return 0 if spell not memmed, or 1-12 if memmed
   */
  public int getGem(Spell spell) {
    for(int i = 0; i < SPELL_GEMS; i++) {
      Spell s = gems[i];
      if(s != null && s.equals(spell)) {
        return i + 1;
      }
    }

    return 0;
  }

  public int getMana() {
    return mana;
  }

  public int getMaxMana() {
    return maxMana;
  }

  public HistoryValue<Integer> getManaHistory() {
    return manaHistory;
  }

  public float getExperience() {
    return xp / 330f;
  }

  public float getAAExperience() {
    return aaxp / 330f;
  }

  public int getAASaved() {
    return aaSaved;
  }

  public int getAACount() {
    return aaCount;
  }

  public CombatState getCombatState() {
    switch(combatState) {
    case 0: return CombatState.COMBAT;
    case 1: return CombatState.DEBUFFED;
    case 2: return CombatState.COOLDOWN;
    case 3: return CombatState.ACTIVE;
    case 4: return CombatState.RESTING;
    default: throw new RuntimeException("Unknown CombatState: " + combatState);
    }
  }

  public int getHitPoints() {
    return hitPoints;
  }

  public int getMaxHitPoints() {
    return maxHitPoints;
  }

  public int getEndurance() {
    return endurance;
  }

  public int getMaxEndurance() {
    return maxEndurance;
  }

  public float getWeight() {
    return weight;
  }

  @Override
  public int getAggro() {
    return aggro;
  }

  public int getSecondaryAggro() {
    return secondaryAggro;
  }

  public Spawn getSecondary() {
    return secondary;
  }

  public Set<Spawn> getNearbyGroupMembers(int maxDistanceToBeConsideredPartOfGroup) {
    Set<Spawn> nearbyGroupMembers = new HashSet<>();

    nearbyGroupMembers.add(session.getMe());
    int previousSize = 0;

    while(nearbyGroupMembers.size() != previousSize) {
      previousSize = nearbyGroupMembers.size();

      for(Spawn member : session.getGroupMembers()) {
        if(member.isAlive() && !nearbyGroupMembers.contains(member)) {
          for(Spawn nearbyGroupMember : nearbyGroupMembers) {
            if(member.getDistance(nearbyGroupMember) <= maxDistanceToBeConsideredPartOfGroup) {
              nearbyGroupMembers.add(member);
              break;
            }
          }
        }
      }
    }

    return nearbyGroupMembers;
  }

  /**
   * Generic function that evaluates an expression against every member of your group
   * that is in the same zone.
   *
   * @param expr an expression to evaluate
   * @return the number of members that matched the expression
   */
  public int countMembers(String expr) {
    int count = 0;

    try {
      for(Spawn member : session.getGroupMembers()) {
        if((Boolean)Parser.parse(new ExpressionRoot(session, member, null, null, null), expr)) {
          count++;
        }
      }
    }
    catch(SyntaxException e) {
      session.logErr("CountMembers: " + e);
      return 0;
    }

    return count;
  }

  /**
   * Generic function that averages a value for every member in the same zone matching a condition.
   *
   * @param expr an expression to evaluate
   * @return the avg value of all matching group members; if no members matched returns 0
   */
  public double avgMembers(String expr, String condition) {
    double total = 0;
    int count = 0;

    try {
      for(Spawn member : session.getGroupMembers()) {
        if((Boolean)Parser.parse(new ExpressionRoot(session, member, null, null, null), condition)) {
          count++;
          total += ((Number)Parser.parse(new ExpressionRoot(session, member, null, null, null), expr)).doubleValue();
        }
      }
    }
    catch(SyntaxException e) {
      session.logErr("AvgMembers: " + e);
      return 0;
    }

    return count == 0 ? 0 : total / count;
  }

  public double avgMembers(String expr) {
    return avgMembers(expr, null);
  }

//  /**
//   * Special function which returns the highest health of the 3 lowest health people in the group.
//   * @return
//   */
//  public int getGroupHitPointsPct() {
//    List<Integer> healths = new ArrayList<>(6);
//
//    for(Spawn member : session.getGroupMembers()) {
//      healths.add(member.getHitPointsPct());
//    }
//
//    Collections.sort(healths);
//
//    return healths.get(healths.size() < 3 ? healths.size() - 1 : 2);
//  }

//  public int getAvgGroupHitPointsPct() {
//    int totalPct = 0;
//
//    for(Spawn member : session.getGroupMembers()) {
//      totalPct += member.getHitPointsPct();
//    }
//
//    return totalPct / session.getGroupMembers().size();
//  }

  public int getAvgGroupHealerMana() {
    int healerCount = 0;
    int totalMana = 0;

    for(Spawn member : session.getGroupMembers()) {
      if(member.isPriest()) {
        if(member.isAlive()) {
          totalMana += member.getManaPct();
        }
        healerCount++;
      }
    }

    return healerCount == 0 ? 0 : totalMana / healerCount;
  }

  /**
   * Returns true if all of group is within the given distance.  False is also
   * returned if any member is not in the same zone.
   *
   * @param distance a distance
   * @return true if all of group is within the given distance
   */
  public boolean isGroupNearby(int distance) {
    Set<Spawn> groupMembers = session.getGroupMembers();

    if(session.getGroupMemberNames().size() != groupMembers.size()) { // Not all in same zone
      return false;
    }

    for(Spawn member : groupMembers) {
      if(member.getDistance() > distance) {
        return false;
      }
    }

    return true;
  }

  public boolean isGroupAlive() {
    for(Spawn member : session.getGroupMembers()) {
      if(!member.isAlive()) {
        return false;
      }
    }

    return true;
  }

  public int getGroupSize() {
    return session.getGroupMembers().size();
  }

  public int getMobsInCamp() {
    int mobsInCamp = 0;

    for(Spawn spawn : session.getSpawns()) {
      if(spawn.isEnemy() && spawn.getDistance() <= 75) {
        mobsInCamp++;
      }
    }

    return mobsInCamp;
  }

  /**
   * Estimates how long it would take to kill all the mobs currently agroed.
   *
   * @return an estimation, in seconds
   */
  public int getEstimatedSecondsLeftOnBattle() {
    int estimatedSeconds = 0;

    for(Spawn spawn : getHostileExtendedTargets()) {
      if(!spawn.hasTimeToLiveData()) {
        if(spawn.isNamedMob()) {
          estimatedSeconds += 60;
        }
        else {
          estimatedSeconds += 30;
        }
      }
      else {
        estimatedSeconds += spawn.getMobTimeToLive();
      }

      estimatedSeconds++; // target switching time
    }

    return estimatedSeconds;
  }

  public int getRecentDpsTaken() {
    long tenSecondsAgo = System.currentTimeMillis() - 10 * 1000;
    int totalDmg = 0;

    for(Point<Integer> p : dmgHistory) {
      if(p.getMillis() >= tenSecondsAgo) {
        totalDmg += p.getValue();
      }
    }

    return totalDmg / 10;
  }

  public int getPctRecentDpsTaken() {
    //System.out.println(">>> pctRecentDpsTaken = " + (100 * getRecentDpsTaken() / getMaxHitPoints()));
    return 100 * getRecentDpsTaken() / getMaxHitPoints();
  }

  public boolean isAMobGating() {
    return System.currentTimeMillis() - mobLastGateCastMillis < 5000;
  }

  /**
   * @return true if the situation looks dangerous (ie, lots of mobs, named in camp)
   */
  public boolean isDangerousSituation() {
//    int totalLevel = 0;
//
//    Set<Spawn> spawns = session.getSpawns();
//
//    for(Spawn spawn : spawns) {
//      if(spawn.isEnemy() && spawn.getLevel() >= getLevel() - 10 && spawn.getLevel() <= getLevel() - 5) {
//        totalLevel += getLevel();
//      }
//
//
//    }

    return false;
  }

  public boolean isGroupInCombat() {
    return inCombat() || getExtendedTargetCount() > 0;
  }

  public boolean isEffectTypeReady(EffectType effectType) {
    return getEffectTypeReadyMillis(effectType) == 0;
  }

  public long getEffectTypeReadyMillis(EffectType effectType) {
    long shortest = 60 * 60 * 1000;

    for(Spell spell : gems) {
      if(spell != null && spell.getEffectTypes().contains(effectType)) {
        long millis = getGemReadyMillis(getGem(spell));

        if(millis < shortest) {
          shortest = millis;
        }
      }
    }

    for(Effect effect : session.getKnownEffects()) {
      if(effect != null && effect.getType() == Effect.Type.ABILITY) {
        if(effect.getSpell().getEffectTypes().contains(effectType)) {
          long millis = effect.getReadyMillis();

          if(millis < shortest) {
            shortest = millis;
          }
        }
      }
    }

    return shortest;
  }

  public boolean isEffectReady(String effectDescription) {
    Effect effect = session.getEffect(effectDescription, 10);
    return effect != null && effect.isReady();
  }

  public boolean hasEffect(String effectDescription) {
    return session.getEffect(effectDescription, 10) != null;
  }

  public boolean inCombat() {
    return getCombatState() == CombatState.COMBAT;
  }

  public boolean isInvisible() {
    return invisible;
  }

  public Set<String> getProfiles() {
    return session.getActiveProfiles();
  }

  public boolean isExtendedTarget(Spawn spawn) {
    return extendedTargetIDs.contains(spawn.getId());
  }

  public Spawn getExtendedTarget(int index) {
    return index < extendedTargetIDs.size() ? session.getSpawn(extendedTargetIDs.get(index)) : null;
  }

  public Set<Spawn> getHostileExtendedTargets() {
    Set<Spawn> targets = new HashSet<>();

    for(int id : extendedTargetIDs) {
      Spawn spawn = session.getSpawn(id);

      if(spawn != null && spawn.isEnemy()) {
        targets.add(spawn);
      }
    }

    return targets;
  }

  public Set<Spawn> getExtendedTargets() {
    Set<Spawn> targets = new HashSet<>();

    for(int id : extendedTargetIDs) {
      Spawn spawn = session.getSpawn(id);

      if(spawn != null) {
        targets.add(spawn);
      }
    }

    return targets;
  }

  public Set<Spawn> getFriendlyExtendedTargets() {
    Set<Spawn> targets = new HashSet<>();

    for(int id : extendedTargetIDs) {
      Spawn spawn = session.getSpawn(id);

      if(spawn != null && spawn.isFriendly()) {
        targets.add(spawn);
      }
    }

    return targets;
  }

  public int getExtendedTargetCount() {
    int autoHaters = 0;

    for(Integer type : extendedTargetType) {
      if(type == 1) {
        autoHaters++;
      }
    }

    return autoHaters;
  }

  public boolean isExtendedTargetFull() {
    return extendedTargetIDs.size() >= 10;
  }

  public int getExtendedTargetCountWithinRange(int range) {
    int count = 0;

    for(int spawnId : extendedTargetIDs) {
      Spawn spawn = session.getSpawn(spawnId);

      if(spawn != null && spawn.getDistance() <= range) {
        count++;
      }
    }

    return count;
  }

  /**
   * Generic function that evaluates a condition against every extended target.
   *
   * @param expr a condition to evaluate
   * @return the number of members that matched the condition
   */
  public int countExtendedTargets(String condition) {
    int count = 0;

    TargetModule targetModule = (TargetModule)session.getModule("TargetModule");
    Spawn mainTarget = null;

    if(targetModule != null) {
      mainTarget = targetModule.getMainAssistTarget();
    }

    try {
      for(Spawn extendedTarget : getExtendedTargets()) {
        if((Boolean)Parser.parse(new ExpressionRoot(session, extendedTarget, mainTarget, null, null), condition)) {
          count++;
        }
      }
    }
    catch(SyntaxException e) {
      session.logErr("countExtendedTargets: " + e);
      return 0;
    }

    return count;
  }

  public double sumExtendedTargets(String expr, String condition) {
    double total = 0;

    try {
      for(Spawn extendedTarget : getExtendedTargets()) {
        if((Boolean)Parser.parse(new ExpressionRoot(session, extendedTarget, null, null, null), condition)) {
          total += ((Number)Parser.parse(new ExpressionRoot(session, extendedTarget, null, null, null), expr)).doubleValue();
        }
      }
    }
    catch(SyntaxException e) {
      session.logErr("sumExtendedTargets: " + e);
      return 0;
    }

    return total;
  }

  private static final Pattern COMMA = Pattern.compile(",");

  //                                                                                  1         2        3         4        5        6         7         8         9         10        11        12       13       14        15        16        17        18                19                 20                21         22                 23                  24                  25                  26
  //                                                                        Name      HP        Max HP   Mana      Max Mana End      Max End   Weight    XP        AAXP      AAsaved   AA        TargId   Combat   Invis     AutoAtk   CastingID DiscID    Melee.Status      Memmed Spells      Aggro Info        Cursor     Buffs              Short Buffs         Extended Target     Auras               TargetBuffs
  public static final Pattern PATTERN = Pattern.compile("#M [-0-9]+ [-0-9]+ [A-Za-z]+ ([-0-9]+) ([0-9]+) ([-0-9]+) ([0-9]+) ([0-9]+) ([-0-9]+) ([-0-9]+) ([-0-9]+) ([-0-9]+) ([-0-9]+) ([-0-9]+) ([0-9]+) ([0-9]+) ([-0-9]+) ([-0-9]+) ([-0-9]+) ([-0-9]+) ((?:[A-Za-z]+ )+) ?M\\[([-0-9, ]+)\\] A\\[([-0-9 ]+)\\] C\\[(.*?)\\] B\\[([-0-9: ]+)\\] SB\\[([-0-9: ]+)\\] XT\\[([-0-9: ]*)\\] A\\[([^\\]]*)\\](?: TB\\[(.*)\\])?");
  // (?: TB\\[([0-9: ]+)\\])?  <-- sometimes result is TB]... bugged.

  private int counter = 0;
  private long timeOfUpdate;

  protected void updateMe(String info, long timeOfUpdate) {
    dmgHistory.add(0);
    Matcher matcher = PATTERN.matcher(info);


    // Changes: Added after 15, stand state
    // Added more spell and buff slots
    if(matcher.matches()) {
      this.timeOfUpdate = timeOfUpdate;

      updateMeStats(
        Integer.parseInt(matcher.group(1)), Integer.parseInt(matcher.group(2)),
        Integer.parseInt(matcher.group(3)), Integer.parseInt(matcher.group(4)),
        Integer.parseInt(matcher.group(5)), Integer.parseInt(matcher.group(6))
      );

      this.weight = Integer.parseInt(matcher.group(7));
      this.xp = Integer.parseInt(matcher.group(8));
      this.aaxp = Integer.parseInt(matcher.group(9));
      this.aaSaved = Integer.parseInt(matcher.group(10));
      this.aaCount = Integer.parseInt(matcher.group(11));
      this.combatState = Integer.parseInt(matcher.group(13));  // Combat State

      Spell castedSpell = getCastedSpell();

      if(combatState != 0 && (castedSpell == null || !castedSpell.isDetrimental())) {
        lastOOCMillis = System.currentTimeMillis();
      }

      this.invisible = Integer.parseInt(matcher.group(14)) > 0;  // Invis?
      Integer.parseInt(matcher.group(15));  // Auto-Attack?

      this.runningCastID = Integer.parseInt(matcher.group(16));
      updateActivelyCasting(runningCastID >= 0);
      this.runningDiscID = Integer.parseInt(matcher.group(17));

      this.meleeStatus = matcher.group(18).trim();

      updateGems(matcher.group(19).split(" "));  // Memmed spells

      String[] aggroInfo = matcher.group(20).trim().split(" ");

      this.aggro = Integer.parseInt(aggroInfo[2]);
      this.secondaryAggro = Integer.parseInt(aggroInfo[3]);
      this.secondary = session.getSpawn(Integer.parseInt(aggroInfo[4]));

//      if(counter-- == 0) {
//        session.log(matcher.group(24));  // 6627 0 14 100 6312
//        counter = 20;
//      }

      this.cursor = matcher.group(21);

      updateBuffsAndDurations(matcher.group(22).trim() + " " + matcher.group(23).trim(), true, Source.DIRECT);  // Buffs, accurate to 6 seconds

      extendedTargetIDs.clear();
      extendedTargetAggro.clear();
      extendedTargetType.clear();

      //System.out.println(info);

      if(!matcher.group(24).isEmpty()) {
        for(String xtargetInfo : matcher.group(24).split(" ")) {
          String[] parts = xtargetInfo.split(":");

          extendedTargetIDs.add(Integer.parseInt(parts[0]));
          extendedTargetAggro.add(Integer.parseInt(parts[1]));
          extendedTargetType.add(Integer.parseInt(parts[2]));
        }
        //session.log(matcher.group(21) + " -> " + extendedTargetAggro);
      }

      updateTarget(Integer.parseInt(matcher.group(12)), extendedTargetIDs.contains(Integer.parseInt(matcher.group(12))));  // target id

//        session.echo("XT : " + matcher.group(22) + "<");
//      }

      String targetBuffs = matcher.group(26);

      if(targetBuffs != null && !targetBuffs.trim().isEmpty() && getTarget() != null) {
//        session.log("" + session.hasInpsectBuffs() + ": "+getGroupSize()+":" + getTarget() + " : " + targetBuffs);
        if(getTarget().getType() == SpawnType.PC || (session.hasInspectBuffs() && session.getGroupMemberNames().size() >= 3)) {
          getTarget().updateBuffsAndDurations(targetBuffs, false, Source.DIRECT);  // target's buffs, accurate to the second
        }
      }

      updateUnderDirectAttack(hasAggro(90));
    }
    else {
      System.err.println("WARNING, INVALID DATA: " + info);
    }
  }

  public void updateMeStats(int hitPoints, int maxHitPoints, int mana, int maxMana, int endurance, int maxEndurance) {
    this.maxHitPoints = maxHitPoints;
    this.hitPoints = hitPoints;
    updateHealth(maxHitPoints != 0 ? hitPoints * 100 / maxHitPoints : 100, Source.DIRECT);

    this.mana = mana;
    this.maxMana = maxMana;
    updateMana(maxMana != 0 ? mana * 100 / maxMana : 100);

    this.maxEndurance = maxEndurance;
    this.endurance = endurance;
    updateEndurance(maxEndurance != 0 ? endurance * 100 / maxEndurance : 100);

    manaHistory.add(this.mana);
  }

  public void updateGems(String[] gemStates) {
    for(int i = 0; i < gemStates.length; i++) {
      String[] gemState = COMMA.split(gemStates[i]);
      int spellID = Integer.parseInt(gemState[0]);

      this.gems[i] = spellID <= 0 ? null : session.getSpell(spellID);
      this.gemReadyList[i] = gemState[1].equals("1");
      this.getTimers[i] = Long.parseLong(gemState[2]) * 1000;
    }
  }

//  protected void updateMeTypeL(String info) {
//    Matcher matcher = Pattern.compile("#L ([-0-9]+) ([-0-9]+) ([A-Za-z]+) ([-0-9\\.]+),([-0-9\\.]+),([-0-9\\.]+) ([-0-9\\.]+) ([-0-9]+)").matcher(info);
//
//    if(matcher.matches()) {
//      if(Integer.parseInt(matcher.group(2)) == getId()) {
//        this.combatState = Integer.parseInt(matcher.group(8));
//        updateLocation(Float.parseFloat(matcher.group(4)), Float.parseFloat(matcher.group(5)), Float.parseFloat(matcher.group(6)), Float.parseFloat(matcher.group(7)));
//
//
////        sprintf(buf, "#L %d %d %s %.2f,%.2f,%.2f %.2f %d",
////            pCharInfo->zoneId, pCharInfo->pSpawn->SpawnID, pCharInfo->Name,
////            pCharInfo->pSpawn->X, pCharInfo->pSpawn->Y, pCharInfo->pSpawn->Z,
////            pCharInfo->pSpawn->Heading,
////            ((PCPLAYERWND)pPlayerWnd)->CombatState
////          );
//      }
//    }
//  }

//  public int getCastingID() {
//    return castingID;
//  }

//  public void updateCastingID(int castingID) {
//    this.castingID = castingID;
//
////    if(castingID != 0 && !isBard()) {
////      lastCastMillis = System.currentTimeMillis();
////    }
//  }

  public void updateActivelyCasting(boolean casting) {
    if(casting || (!casting && activelyCasting)) {
      lastCastMillis = System.currentTimeMillis();
    }

    this.activelyCasting = casting;
  }

  public void updateLRUSlots(int slot) {
    lruSpellSlots.remove((Object)slot);
    lruSpellSlots.add(slot);
  }

  public List<Integer> getLRUSlots() {
    return lruSpellSlots;
  }

  public boolean hasProfile(String profileName) {
    return session.getActiveProfiles().contains(profileName);
  }

  /**
   * Checks whether memorizing a spell would be a safe action.
   *
   * @return true if memorizing a spell would be safe
   */
  public boolean isSafeToMemorizeSpell() {
//    if(isMounted()) {
//      return true;
//    }

    if(hasAggro(50)) {
      return false;
    }

//    if(inCombat()) {
//      return false;
//    }

    return true;
  }

  public int getExtendedTargetAggro(Spawn spawn) {
    for(int i = 0; i < getExtendedTargetCount(); i++) {
      if(extendedTargetIDs.get(i) == spawn.getId()) {
        return extendedTargetAggro.get(i);
      }
    }

    return 0;
  }

  public Group getGroup() {
    return group;
  }

  /**
   * Returns <code>true</code> if aggro on any known target is atleast the given level.
   *
   * @param level the aggro level
   * @return <code>true</code> if aggro on any known target is atleast the given level
   */
  public boolean hasAggro(int level) {
    for(int i = 0; i < getExtendedTargetCount(); i++) {
      int aggro = extendedTargetAggro.get(i);
      if(aggro >= level) {
        return true;
      }
    }

    if(getAggro() >= level) {
      return true;
    }

    return false;
  }

  /**
   * Returns <code>true</code> if aggro on any known target is less than the given level.
   *
   * @param level the aggro level
   * @return <code>true</code> if aggro on any known target is less than the given level.
   */
  public boolean isAggroOnAllAtleast(int level) {
    for(int i = 0; i < getExtendedTargetCount(); i++) {
      int aggro = extendedTargetAggro.get(i);
      if(aggro < level) {
        return false;
      }
    }

    return true;
  }

  public long getLastOOCMillis() {
    return lastOOCMillis;
  }

  @Override
  public boolean willStack(Spell spell) {
    if(runningDiscID <= 0 || (spell.willStack(session.getSpell(runningDiscID)) && spell.getId() != runningDiscID)) {
      return super.willStack(spell);
    }

    return false;
  }

  public String getRunningDiscipline() {
    return runningDiscID <= 0 ? "" : session.getSpell(runningDiscID).getName().replaceAll(" Rk\\. *II+", "");
  }
}
