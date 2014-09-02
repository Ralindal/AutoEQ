package autoeq.effects;

import java.util.HashMap;
import java.util.Map;

import autoeq.eq.EverquestSession;
import autoeq.eq.Spell;

public class SpellEffect extends AbstractSpellBasedEffect {
  private static final Map<String, Integer> MAX_GOM_LEVEL = new HashMap<>();

  static {
    MAX_GOM_LEVEL.put("Gift of Mana", 70);
    MAX_GOM_LEVEL.put("Gift of Radiant Mana", 75);
    MAX_GOM_LEVEL.put("Gift of Exquisite Radiant Mana", 80);
    MAX_GOM_LEVEL.put("Gift of Amazing Exquisite Radiant Mana", 85);
    MAX_GOM_LEVEL.put("Gift of Dreamlike Exquisite Radiant Mana", 90);
    MAX_GOM_LEVEL.put("Gift of Ascendant Exquisite Radiant Mana", 95);
    MAX_GOM_LEVEL.put("Gift of Phantasmal Exquisite Radiant Mana", 100);
    MAX_GOM_LEVEL.put("Gracious Mana", 100);
  }

  private final int agro;

  public SpellEffect(EverquestSession session, Spell spell, int agro, long lockOutMillis) {
    super(session, spell, lockOutMillis);

    this.agro = agro;
  }

  @Override
  public Type getType() {
    return Type.SPELL;
  }

  @Override
  public int getAgro() {
    return agro;
  }

  @Override
  public void internalActivate() {
//    getSession().doCommand("/multiline ; /echo ==> \"/cast " + getSession().getMe().getGem(getSpell()) + "\" ${Math.Calc[${MacroQuest.CurrentTimeMillis}-" + System.currentTimeMillis() + "]} ms lag;/cast " + getSession().getMe().getGem(getSpell()));
    getSession().doCommand("/cast " + getSession().getMe().getGem(getSpell()));
  }

  @Override
  protected boolean internalIsReady() {
    if(!getSession().getMe().isSpellReady(getSpell())) {
      return false;
    }

    return super.internalIsReady();
  }

  @Override
  public long getReadyMillis() {
    return getSession().getMe().getGemReadyMillis(getSession().getMe().getGem(getSpell()));
  }

  @Override
  public boolean willUseGOM() {
    for(String name : getSession().getMe().getBuffNames()) {
      Integer maxLevel = MAX_GOM_LEVEL.get(name);

      if(maxLevel != null) {
        return getSpell().getLevel() <= maxLevel;
      }
    }

    return false;
  }

  @Override
  public String toString() {
    return getSpell().getName();
  }

  @Override
  public boolean requiresStanding() {
    return true;
  }

  @Override
  protected boolean isUnaffectedBySilence() {
    return false;
  }

  @Override
  protected boolean usesSpellCasting() {
    return true;
  }
}
