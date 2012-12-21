package autoeq.eq;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import autoeq.SpellData;
import autoeq.effects.Effect;
import autoeq.effects.Effect.Type;

public class Me extends Spawn {
  private static final int SPELL_GEMS = 12;
  private static final Map<String, CastResult> EXACT_MESSAGE_TO_CAST_RESULT = new HashMap<>();
  private static final Map<String, CastResult> MESSAGE_TO_CAST_RESULT = new HashMap<>();

  public enum CastResult {
    SUCCESS("CAST_SUCCESS", 0),
    RECOVERING("CAST_RECOVER", 1),
    INTERRUPTED("CAST_INTERRUPTED", 2),
    CANNOT_SEE_TARGET("CAST_CANNOTSEE", 2),
    DISTRACTED("CAST_DISTRACTED", 3),
    FIZZLED("CAST_FIZZLE", 5),
    IMMUNE("CAST_IMMUNE", 5),
    MISSING_COMPONENTS("CAST_COMPONENTS", 5),
    MISSING_TARGET("CAST_NOTARGET", 5),
    NOT_READY("CAST_NOTREADY", 5),
    INSUFFICIENT_MANA("CAST_OUTOFMANA", 5),
    OUT_OF_RANGE("CAST_OUTOFRANGE", 5),
    RESISTED("CAST_RESIST", 5),
    DID_NOT_TAKE_HOLD("CAST_TAKEHOLD", 5),
    SITTING("CAST_STANDING", 5),
    STUNNED("CAST_STUNNED", 5),

    SELF_RESIST(RESISTED);  // Cancels out a resist

    private final int rootCauseLevel;
    private final String code;
    private final CastResult cancelsOut;

    CastResult(String code, int rootCauseLevel) {
      this.code = code;
      this.rootCauseLevel = rootCauseLevel;
      this.cancelsOut = null;
    }

    CastResult(CastResult cancelsOut) {
      this.cancelsOut = cancelsOut;
      this.code = "CAST_SUCCESS";
      this.rootCauseLevel = 0;
    }

    public String getCode() {
      return code;
    }

    public int getRootCauseLevel() {
      return rootCauseLevel;
    }

    public CastResult getCancelsOut() {
      return cancelsOut;
    }
  }

  static {
    EXACT_MESSAGE_TO_CAST_RESULT.put("You cannot see your target.", CastResult.CANNOT_SEE_TARGET);
    EXACT_MESSAGE_TO_CAST_RESULT.put("You haven't recovered yet...", CastResult.RECOVERING);
    EXACT_MESSAGE_TO_CAST_RESULT.put("You are missing some required components.", CastResult.MISSING_COMPONENTS);
    EXACT_MESSAGE_TO_CAST_RESULT.put("Your spell is interrupted.", CastResult.INTERRUPTED);
    EXACT_MESSAGE_TO_CAST_RESULT.put("You can't cast spells while invulnerable!", CastResult.DISTRACTED);
    EXACT_MESSAGE_TO_CAST_RESULT.put("You must first select a target for this spell!", CastResult.MISSING_TARGET);
    EXACT_MESSAGE_TO_CAST_RESULT.put("You must be standing to cast a spell.", CastResult.SITTING);
    EXACT_MESSAGE_TO_CAST_RESULT.put("Your spell fizzles!", CastResult.FIZZLED);
    EXACT_MESSAGE_TO_CAST_RESULT.put("Your spell is too powerful for your intended target.", CastResult.DID_NOT_TAKE_HOLD);
    EXACT_MESSAGE_TO_CAST_RESULT.put("This spell only works on the undead.", CastResult.IMMUNE);

    MESSAGE_TO_CAST_RESULT.put("You resist the {SPELL_NAME} spell!", CastResult.SELF_RESIST);   //"You resist the {1} spell!"
    MESSAGE_TO_CAST_RESULT.put("Your target resisted the {SPELL_NAME} spell.", CastResult.RESISTED);

    MESSAGE_TO_CAST_RESULT.put("You *CANNOT* cast spells, you have been silenced", CastResult.DISTRACTED);
    MESSAGE_TO_CAST_RESULT.put("Your target cannot be mesmerized", CastResult.IMMUNE);
    MESSAGE_TO_CAST_RESULT.put("This spell only works on ", CastResult.IMMUNE);
    MESSAGE_TO_CAST_RESULT.put("You must first target a group member", CastResult.MISSING_TARGET);
    MESSAGE_TO_CAST_RESULT.put("Spell recast time not yet met", CastResult.NOT_READY);
    MESSAGE_TO_CAST_RESULT.put("Insufficient Mana to cast this spell", CastResult.INSUFFICIENT_MANA);
    MESSAGE_TO_CAST_RESULT.put("Your target is out of range, get closer", CastResult.OUT_OF_RANGE);
    MESSAGE_TO_CAST_RESULT.put("You can't cast spells while stunned", CastResult.STUNNED);
    MESSAGE_TO_CAST_RESULT.put("Your spell did not take hold", CastResult.DID_NOT_TAKE_HOLD);
    MESSAGE_TO_CAST_RESULT.put("Your spell would not have taken hold", CastResult.DID_NOT_TAKE_HOLD);
    MESSAGE_TO_CAST_RESULT.put("You need to be in a more open area to summon a mount", CastResult.DID_NOT_TAKE_HOLD);
    MESSAGE_TO_CAST_RESULT.put("You can only summon a mount on dry land", CastResult.DID_NOT_TAKE_HOLD);

//    aCastEvent(LIST289, CAST_COLLAPSE    ,"Your gate is too unstable, and collapses#*#");
//    aCastEvent(LIST289, CAST_COMPONENTS  ,"You need to play a#*#instrument for this song#*#");
//    aCastEvent(LIST289, CAST_DISTRACTED  ,"You are too distracted to cast a spell now#*#");
//    aCastEvent(LIST289, CAST_IMMUNE      ,"Your target has no mana to affect#*#");
//    aCastEvent(LIST013, CAST_IMMUNE      ,"Your target is immune to changes in its attack speed#*#");
//    aCastEvent(LIST013, CAST_IMMUNE      ,"Your target is immune to changes in its run speed#*#");
//    aCastEvent(UNKNOWN, CAST_IMMUNE      ,"Your target looks unaffected#*#");
//    aCastEvent(UNKNOWN, CAST_INTERRUPTED ,"Your casting has been interrupted#*#");
//    aCastEvent(LIST289, CAST_FIZZLE      ,"You miss a note, bringing your song to a close#*#");
//    aCastEvent(LIST289, CAST_OUTDOORS    ,"This spell does not work here#*#");
//    aCastEvent(LIST289, CAST_OUTDOORS    ,"You can only cast this spell in the outdoors#*#");
//    aCastEvent(LIST289, CAST_OUTDOORS    ,"You can not summon a mount here#*#");
//    aCastEvent(LIST289, CAST_OUTDOORS    ,"You must have both the Horse Models and your current Luclin Character Model enabled to summon a mount#*#");
//
//    aCastEvent(LIST289, CAST_RECOVER     ,"Spell recovery time not yet met#*#");  // Does that still happen?
//    aCastEvent(LIST289, CAST_SUCCESS     ,"You are already on a mount#*#");
  }

//  private final SpellProperty[] gems;
  private final Spell[] gems = new Spell[SPELL_GEMS];
  private final boolean[] gemReadyList = new boolean[SPELL_GEMS];
  private final Set<Integer> extendedTargetIDs = new HashSet<>();

  private final List<Integer> lruSpellSlots = new LinkedList<>();

  private final HistoryValue<Integer> manaHistory = new HistoryValue<>(7500);
  private final HistoryValue<Integer> dmgHistory = new HistoryValue<>(10000);

  private final int maxSpellSlots;

  private String meleeStatus;

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
  private int castingID;

  private long mobLastGateCastMillis;
  private long lastCastMillis;

  public Me(EverquestSession session, int id) {
    super(session, id);

    String mnemRet = session.translate("${Me.AltAbility[Mnemonic Retention]}");

    if(!mnemRet.equals("NULL")) {
      int aaSpent = Integer.parseInt(mnemRet);
      maxSpellSlots = aaSpent == 0 ? 8 :
                      aaSpent == 3 ? 9 :
                      aaSpent == 9 ? 10 :
                      aaSpent == 15 ? 11 : 12;
    }
    else {
      maxSpellSlots = 8;
    }

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

    session.addChatListener(new ChatListener() {
      private final Pattern PATTERN = Pattern.compile(".+");

      @Override
      public Pattern getFilter() {
        return PATTERN;
      }

      @Override
      public void match(Matcher matcher) {
        if(castedSpellName != null) {
          String line = matcher.group(0);
          CastResult castResult = EXACT_MESSAGE_TO_CAST_RESULT.get(line);

          if(castResult == null) {
            for(Map.Entry<String, CastResult> entry : MESSAGE_TO_CAST_RESULT.entrySet()) {
              String message = entry.getKey().replaceAll("\\{SPELL_NAME\\}", castedSpellName);

              if(line.startsWith(message)) {
                castResult = entry.getValue();
              }
            }
          }

          if(castResult != null) {
            lastSeenCastResults.add(castResult);
          }
        }
      }
    });
  }

  private final ConcurrentLinkedQueue<CastResult> lastSeenCastResults = new ConcurrentLinkedQueue<>();
  private volatile String castedSpellName;

  private void resetCastResult(String spellName) {
    lastSeenCastResults.clear();
    castedSpellName = spellName;
  }

  private CastResult getCastResult() {
    retry:
    for(;;) {
      for(CastResult castResult : lastSeenCastResults) {
        if(castResult.getCancelsOut() != null) {
          lastSeenCastResults.remove(castResult.getCancelsOut());
          lastSeenCastResults.remove(castResult);
          continue retry;
        }
      }

      break;
    }

    CastResult finalCastResult = CastResult.SUCCESS;

    for(CastResult castResult : lastSeenCastResults) {
      if(castResult.getRootCauseLevel() > finalCastResult.getRootCauseLevel()) {
        finalCastResult = castResult;
      }
    }

    return finalCastResult;
  }

  public String getMeleeStatus() {
    return meleeStatus;
  }

  /*
   * Actions
   */

  private final Set<Integer> lockedSpellSlots = new HashSet<>();

  public void lockSpellSlot(int slot) {
    lockedSpellSlots.add(slot);
  }

  public void unlockAllSpellSlots() {
    lockedSpellSlots.clear();
  }

  private int getPreferredGem() {
//    List<String> possibleSlots = Arrays.asList(defaultGems.split(" "));

    for(int slot = 1; slot <= maxSpellSlots; slot++) {
      if(getGem(slot) == null) {
        return slot;
      }
    }

    for(int slot : session.getMe().getLRUSlots()) {
      if(!lockedSpellSlots.contains(slot)) {
        session.getMe().updateLRUSlots(slot);
        return slot;
      }
    }
    return 0;
  }

  /**
   * Memorizes a spell into a spell slot.  If slot is 0, the least recently used spell slot will be used.
   */
  public void memorize(int spellId, int spellSlot) {
    session.doCommand("/autoinventory");

    final int slot = spellSlot != 0 && spellSlot <= maxSpellSlots ? spellSlot : getPreferredGem();

    if(slot != 0) {
      final Spell spell = session.getSpell(spellId);

      session.doCommand("/memspell " + slot + " \"" + spell.getName() + "\"");
      session.delay(1000, new Condition() {
        @Override
        public boolean isValid() {
          return gems[slot - 1] == null;
        }
      });
      session.delay(5000, new Condition() {
        @Override
        public boolean isValid() {
          return spell.equals(gems[slot - 1]);
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

  /**
   * Checks whether a spell is ready to cast.  Checks if the spell is memmed and we're not casting.
   */
  public boolean isSpellReady(Spell spell) {
    int gem = getGem(spell);

    if(gem > 0) {
      if(isBard() && getCastedSpell() != null && lastCastMillis < System.currentTimeMillis()) {
        return true;
      }

      if(gemReadyList[gem - 1] && !isCasting()) {
        if(getMana() >= spell.getMana() && manaHistory.getValue(3000) >= spell.getMana()) {
          return true;
        }
      }
    }

    return false;
  }

  public String activateEffect(Effect effect, Spawn target) {
    if(isBard()) {
      session.doCommand("/stopcast");
    }
    else if(isMoving()) {
      session.echo("Halting movement");
      session.doCommand("/nomod /keypress back");
      session.delay(500, new Condition() {
        @Override
        public boolean isValid() {
          return !isMoving();
        }
      });
      session.doCommand("/nomod /keypress left hold");
      session.delay(50);
      session.doCommand("/nomod /keypress left");
      session.delay(200);
    }

    if(effect.getSpell().isTargetted()) {
      session.doCommand("/target id " + target.getId());
      session.delay(1000, "${Target.ID} == " + target.getId());
    }

    session.doCommand("/stand");
    session.doCommand(effect.getCastingLine());

    if(effect.getType() != Type.DISCIPLINE) {
      try {
        int castingID = effect.getSpell().getId();  // ID of spell that was cast, initialized here with the intended ID for instant spells only.

        resetCastResult(effect.getSpell().getName());

        if(effect.getCastTime() > 0) {
          // Wait until we've started the cast
          session.delay(500, new Condition() {
            @Override
            public boolean isValid() {
              return Me.this.getCastingID() != 0;
            }
          });

          castingID = getCastingID();

          // Wait until we've ended the cast
          session.delay(30000, new Condition() {
            @Override
            public boolean isValid() {
              return Me.this.getCastingID() == 0;
            }
          });
        }

        if(effect.getType() == Type.SPELL || effect.getType() == Type.SONG) {
          updateLRUSlots(getGem(effect.getSpell()));
        }

        lastCastMillis = System.currentTimeMillis();

        /*
         * Get the result
         */

        session.delay(300, new Condition() {
          @Override
          public boolean isValid() {
            return getCastResult() != CastResult.SUCCESS;
          }
        });

        if(effect.getSpell().getId() != castingID) {
          session.log("Mismatched cast, expected " + effect.getSpell() + ", but got: " + (castingID == 0 ? "nothing" : session.getSpell(castingID)));

          return "CAST_INTERRUPTED";
        }

        return getCastResult().getCode();
      }
      finally {
        resetCastResult(null);
      }
    }

    return "CAST_SUCCESS";
  }

  @Override
  public boolean isCasting() {
    return System.currentTimeMillis() - lastCastMillis < 200;
  }

  public boolean wasCasting(int lastMillis) {
    return System.currentTimeMillis() - lastCastMillis < lastMillis;
  }

  public boolean isBard() {
    return getClassShortName().equals("BRD");
  }

  public boolean isMounted() {
    for(Spell spell : getSpellEffects()) {
      SpellData spellData = session.getRawSpellData(spell.getId());

      for(int i = 0; i < 12; i++) {
        if(spellData.getAttrib(i) == SpellData.ATTRIB_MOUNT) {
          return true;
        }
      }
    }

    return false;
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
   * Special function which returns the highest health of the 3 lowest health people in the group.
   * @return
   */
  public int getGroupHitPointsPct() {
    List<Integer> healths = new ArrayList<>(6);

    for(Spawn member : session.getGroupMembers()) {
      healths.add(member.getHitPointsPct());
    }

    Collections.sort(healths);

    return healths.get(healths.size() < 3 ? healths.size() - 1 : 2);
  }

  public List<Integer> getAscendingGroupHitPointsPct() {
    List<Integer> healths = new ArrayList<>(6);

    for(Spawn member : session.getGroupMembers()) {
      if(member.isAlive()) {
        healths.add(member.getHitPointsPct());
      }
    }

    Collections.sort(healths);

    return healths;
  }

  public int getAvgGroupHitPointsPct() {
    int totalPct = 0;

    for(Spawn member : session.getGroupMembers()) {
      totalPct += member.getHitPointsPct();
    }

    return totalPct / session.getGroupMembers().size();
  }

  public int getAvgGroupHealerMana() {
    int healerCount = 0;
    int totalMana = 0;

    for(Spawn member : session.getGroupMembers()) {
      if(member.isHealer()) {
        if(member.isAlive()) {
          totalMana += member.getManaPct();
        }
        healerCount++;
      }
    }

    return healerCount == 0 ? 100 : totalMana / healerCount;
  }

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

  public int getExtendedTargetCount() {
    return extendedTargetIDs.size();
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

  private static final Pattern COMMA = Pattern.compile(",");

  //                                                                                  1         2        3         4        5        6         7         8         9         10        11        12       13       14        15        16                17                 18                19                                  21                                    23                24
  //                                                                        Name      HP        Max HP   Mana      Max Mana End      Max End   Weight    XP        AAXP      AAsaved   AA        TargId   Combat   Invis     AutoAtk   Melee.Status      Memmed Spells      Aggro Info        Buffs                               Short Buffs                           Extended Target   Auras            TargetBuffs
  public static final Pattern PATTERN = Pattern.compile("#M [-0-9]+ [-0-9]+ [A-Za-z]+ ([-0-9]+) ([0-9]+) ([-0-9]+) ([0-9]+) ([0-9]+) ([-0-9]+) ([-0-9]+) ([-0-9]+) ([-0-9]+) ([-0-9]+) ([-0-9]+) ([0-9]+) ([0-9]+) ([-0-9]+) ([-0-9]+) ((?:[A-Za-z]+ )+) M\\[([-0-9, ]+)\\] A\\[([-0-9 ]+)\\] B\\[([-0-9 ]+)\\] D\\[([-0-9 ]+)\\] SB\\[([-0-9 ]+)\\] SD\\[([-0-9 ]+)\\] XT\\[([-0-9 ]*)\\] A\\[([^\\]]*)\\].*");
  // (?: TB\\[([0-9: ]+)\\])?  <-- sometimes result is TB]... bugged.

  private int counter = 0;
  protected void updateMe(String info) {
    dmgHistory.add(0);
    Matcher matcher = PATTERN.matcher(info);

    // Changes: Added after 15, stand state
    // Added more spell and buff slots
    if(matcher.matches()) {
      this.maxHitPoints = Integer.parseInt(matcher.group(2));  // Max hitpoints
      this.hitPoints = Integer.parseInt(matcher.group(1));  // Current hitpoints
      updateHealth(maxHitPoints != 0 ? hitPoints * 100 / maxHitPoints : 100, Source.DIRECT);
      this.mana = Integer.parseInt(matcher.group(3));  // Current mana
      this.maxMana = Integer.parseInt(matcher.group(4));  // Max mana
      updateMana(maxMana != 0 ? mana * 100 / maxMana : 100);
      manaHistory.add(this.mana);
      this.maxEndurance = Integer.parseInt(matcher.group(6));  // Max endurance
      this.endurance = Integer.parseInt(matcher.group(5));  // Current endurance
      updateEndurance(maxEndurance != 0 ? endurance * 100 / maxEndurance : 100);
      this.weight = Integer.parseInt(matcher.group(7));
      this.xp = Integer.parseInt(matcher.group(8));
      this.aaxp = Integer.parseInt(matcher.group(9));
      this.aaSaved = Integer.parseInt(matcher.group(10));
      this.aaCount = Integer.parseInt(matcher.group(11));
      updateTarget(Integer.parseInt(matcher.group(12)));  // target id
      this.combatState = Integer.parseInt(matcher.group(13));  // Combat State

      this.invisible = Integer.parseInt(matcher.group(14)) > 0;  // Invis?
      Integer.parseInt(matcher.group(15));  // Auto-Attack?

      this.meleeStatus = matcher.group(16).trim();

      String[] gemStates = matcher.group(17).split(" ");  // Memmed spells

      for(int i = 0; i < gemStates.length; i++) {
        String[] gemState = COMMA.split(gemStates[i]);
        int spellID = Integer.parseInt(gemState[0]);

        this.gems[i] = spellID <= 0 ? null : session.getSpell(spellID);
        this.gemReadyList[i] = gemState[1].equals("1");
      }

      if(counter-- == 0) {
        //session.log(matcher.group(18) + " XT: " + matcher.group(23));
        counter = 20;
      }

      updateBuffs(matcher.group(19).trim() + " " + matcher.group(21).trim(), matcher.group(20).trim() + " " + matcher.group(22).trim());  // Buffs

      extendedTargetIDs.clear();

//      System.out.println(">" + matcher.group(22) + "<");

      if(!matcher.group(23).isEmpty()) {
        for(String id : matcher.group(23).split(" ")) {
          extendedTargetIDs.add(Integer.parseInt(id));
  //        System.out.println(">" + id + "<");
        }
      }

//        session.echo("XT : " + matcher.group(22) + "<");
//      }
      // System.out.println("TB : " + matcher.group(22) + "<");
    }
    else {
      System.err.println("WARNING, INVALID DATA: " + info);
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

  public int getCastingID() {
    return castingID;
  }

  public void updateCastingID(int castingID) {
    this.castingID = castingID;
  }

  public void updateLRUSlots(int slot) {
    lruSpellSlots.remove((Object)slot);
    lruSpellSlots.add(slot);
  }

  public List<Integer> getLRUSlots() {
    return lruSpellSlots;
  }
}
