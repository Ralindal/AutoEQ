package autoeq.eq;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import autoeq.SpellData;
import autoeq.effects.Effect;
import autoeq.effects.SongEffect;
import autoeq.effects.Effect.Type;


public class Me extends Spawn {
  private static final int SPELL_GEMS = 12;

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

  private long lastCastMillis;

  private String castResult;

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
      private final Pattern PATTERN = Pattern.compile(".*");

      @Override
      public Pattern getFilter() {
        return PATTERN;
      }

      @Override
      public void match(Matcher matcher) {
        if("You can only cast this spell in the outdoors.".equals(matcher.group())) {
          castResult = "CAST_OUTDOOR";
        }
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

    int slot = spellSlot != 0 ? spellSlot : getPreferredGem();

    if(slot != 0) {
      session.doCommand("/memorize " + spellId + "|gem" + slot);
      session.delay(1000, new Condition() {
        @Override
        public boolean isValid() {
          return session.translate("${Cast.Status}").contains("M");
        }
      });
      session.delay(5000, new Condition() {
        @Override
        public boolean isValid() {
          return session.translate("${Cast.Status}").equals("I");
        }
      });

      gems[slot - 1] = null;
    }
  }

  public boolean isAlternateAbilityReady(String name) {
    return session.translate("${Cast.Ready[" + name + "|alt]}").equals("TRUE");
  }

  public boolean isItemReady(String name) {
    return session.translate("${Cast.Ready[" + name + "|item]}").equals("TRUE");
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

  @SuppressWarnings("unused")
  private String bardCast(SongEffect effect, Spawn target) {

    if(effect.getSpell().isTargetted()) {
      session.doCommand("/target id " + target.getId());
      session.delay(1000, "${Target.ID} == " + target.getId());
    }

    castResult = "CAST_SUCCESS";

    session.doCommand("/stopcast");
    session.doCommand("/stand");

    int gem = getGem(effect.getSpell());

    session.doCommand("/cast " + gem);

    session.delay(1000, "${Window[CastingWindow]}");
    session.delay(30000, "!${Window[CastingWindow]}");

    /*
     * Casting completed now.  Wait a bit to see the cast result.
     */

    session.delay(100);

    return castResult;
  }

  public String activeEffect(Effect effect, Spawn target) {
    if(isBard()) {
      session.doCommand("/stopcast");
    }

    String casting = effect.getCastingLine();

    long start = System.currentTimeMillis();

    if(effect.getSpell().isTargetted()) {
      if(isBard() || effect.getType() == Type.DISCIPLINE) {
        session.doCommand("/target id " + target.getId());
        session.delay(1000, "${Target.ID} == " + target.getId());
      }
      else {
        casting += (target != null ? " -targetid|" + target.getId() : "");
      }
    }

    session.doCommand("/stand");
    session.doCommand(casting);
//    updateLRUSlots(slot);

    if(effect.getType() != Type.DISCIPLINE) {
      // Wait until we've started the cast
      session.delay(1000, new Condition() {
        @Override
        public boolean isValid() {
          return session.translate("${Cast.Status}").contains("C");
        }
      });

      // Wait until we've ended the cast
      session.delay(30000, new Condition() {
        @Override
        public boolean isValid() {
          return session.translate("${Cast.Status}").equals("I");
        }
      });

      // Short delay so MQ2CAST can update Cast.Result... sigh
      session.delay(50);

      if(effect.getType() == Type.SPELL || effect.getType() == Type.SONG) {
        updateLRUSlots(getGem(effect.getSpell()));
      }

      lastCastMillis = System.currentTimeMillis();

//      if(isBard()) {
//        session.doCommand("/stopcast");
//        session.delay(200);
//      }

      for(int i = 0; i < 20; i++) {
        String[] results = session.translate("${Cast.Stored};${Cast.Result}").split(";");

        if(Spell.baseSpellName(results[0]).equals(Spell.baseSpellName(effect.getSpell().getName()))) {
          if(results[0].equals("CAST_RECOVER")) {
            System.err.println("Casting " + effect.getSpell() + " -> RECOVER : time taken " + (lastCastMillis - start));
          }

          return results[1];
        }

        session.getLogger().warning("Spell Casted '" + effect.getSpell().getName() + "' but result for '" + results[0] + "'!");
        session.delay(100);
      }

      return "";
    }
    else {
      return "CAST_SUCCESS";
    }
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
   * @param gem gem number, 1-10
   * @return Spell memmed in the given gem or null if none
   */
  public Spell getGem(int gem) {
    //return gems[gem - 1].get();
    return gems[gem - 1];
  }

  /**
   * @return 0 if spell not memmed, or 1-10 if memmed
   */
  public int getGem(Spell spell) {
    for(int i = 0; i < SPELL_GEMS; i++) {
      //Spell s = gems[i].get();
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
        totalMana += member.getManaPct();
        healerCount++;
      }
    }

    return healerCount == 0 ? 100 : totalMana / healerCount;
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

  public boolean hasHealOverTime() {
    for(Spell spell : getSpellEffects()) {
      if(spell.isHealOverTime()) {
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

  public boolean hasHarvest() {
    for(String name : getBuffNames()) {
      if(name.startsWith("Patient Harvest") || name.startsWith("Tranquil Harvest") || name.startsWith("Serene Harvest")) {
        return true;
      }
    }

    return false;
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

  //                                                                                  1         2        3         4        5        6         7         8         9         10        11        12       13       14        15        16                17                 18                                  20
  //                                                                        Name      HP        Max HP   Mana      Max Mana End      Max End   Weight    XP        AAXP      AAsaved   AA        TargId   Combat   Invis     AutoAtk   Melee.Status      Memmed Spells      Buffs                               Short Buffs
  public static final Pattern PATTERN = Pattern.compile("#M [-0-9]+ [-0-9]+ [A-Za-z]+ ([-0-9]+) ([0-9]+) ([-0-9]+) ([0-9]+) ([0-9]+) ([-0-9]+) ([-0-9]+) ([-0-9]+) ([-0-9]+) ([-0-9]+) ([-0-9]+) ([0-9]+) ([0-9]+) ([-0-9]+) ([-0-9]+) ((?:[A-Za-z]+ )+) M\\[([-0-9, ]+)\\] B\\[([-0-9 ]+)\\] D\\[([-0-9 ]+)\\] SB\\[([-0-9 ]+)\\] SD\\[([-0-9 ]+)\\] XT\\[([0-9 ]*)\\].*");
  // (?: TB\\[([0-9: ]+)\\])?  <-- sometimes result is TB]... bugged.

  protected void updateMe(String info) {
    dmgHistory.add(0);

    Matcher matcher = PATTERN.matcher(info);

    // Changes: Added after 15, stand state
    // Added more spell and buff slots
    if(matcher.matches()) {
      this.maxHitPoints = Integer.parseInt(matcher.group(2));  // Max hitpoints
      this.hitPoints = Integer.parseInt(matcher.group(1));  // Current hitpoints
      updateHealth(maxHitPoints != 0 ? hitPoints * 100 / maxHitPoints : 100);
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

      updateBuffs(matcher.group(18).trim() + " " + matcher.group(20).trim(), matcher.group(19).trim() + " " + matcher.group(21).trim());  // Buffs

      extendedTargetIDs.clear();

//      System.out.println(">" + matcher.group(22) + "<");

      if(!matcher.group(22).isEmpty()) {
        for(String id : matcher.group(22).split(" ")) {
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

  public void updateLRUSlots(int slot) {
    lruSpellSlots.remove((Object)slot);
    lruSpellSlots.add(slot);
  }

  public List<Integer> getLRUSlots() {
    return lruSpellSlots;
  }
}
