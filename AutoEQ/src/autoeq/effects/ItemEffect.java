package autoeq.effects;

import autoeq.eq.ClickableItem;
import autoeq.eq.EffectType;
import autoeq.eq.EverquestSession;
import autoeq.eq.Spell;
import autoeq.eq.TargetType;

public class ItemEffect extends AbstractEffect {
  private final String name;
  private final int agro;

//  private final int castTime;

//  private long readyMillis;

  public ItemEffect(EverquestSession session, String name, int agro, long lockOutMillis) {
    super(session, lockOutMillis);

    this.name = name;
    this.agro = agro;

//    this.castTime = (int)(Double.parseDouble(session.translate("${FindItem[=" + name + "].CastTime}")) * 1000);
//
//    session.registerTimer("I \"" + name + "\"", new ExpressionListener() {
//      @Override
//      public void stateUpdated(String result) {
//        long millis = Long.parseLong(result) * 1000;
//
//        if(millis < 0) {
//          millis = 60 * 1000;
//        }
//
//        readyMillis = millis;
//      }
//    });
  }

  @Override
  public int getCastTime() {
    ClickableItem item = getSession().getClickableItem(name);

    return item == null ? 0 : item.getCastTime();
  }

  public String getName() {
    return name;
  }

  @Override
  public Type getType() {
    return Type.ITEM;
  }

  @Override
  public int getAgro() {
    return agro;
  }

  @Override
  public boolean internalIsUsable() {
    return getSession().getClickableItem(name) != null;
  }

  @Override
  public void internalActivate() {
    getSession().doCommand("/nomodkey /itemnotify ${FindItem[=" + name + "].InvSlot} rightmouseup");
  }

  @Override
  protected boolean internalIsReady() {
    Spell spell = getSpell();

    if(spell == null || !isUsable()) {
      return false;
    }
    if(!isUnaffectedBySilence() && getSession().getMe().getActiveEffect(EffectType.SILENCE) != null) {
      return false;
    }
    if(usesSpellCasting() && getSession().getMe().isCasting()) {
      return false;
    }
    if(getSession().getMe().getMana() < spell.getMana() || getSession().getMe().getManaHistory().getValue(3000) < spell.getMana()) {
      return false;
    }

    return getReadyMillis() == 0;
  }

  @Override
  public String toString() {
    return name;
  }

  @Override
  public boolean willUseGOM() {
    return false;
  }

  @Override
  public boolean requiresStanding() {
    return true;
  }

  @Override
  public long getReadyMillis() {
    ClickableItem item = getSession().getClickableItem(name);

    return item == null ? 60 * 1000 : item.getTimer() * 1000 * 6;
  }

  protected boolean isUnaffectedBySilence() {
    return false;
  }

  protected boolean usesSpellCasting() {
    return true;
  }

  @Override
  public String getId() {
    return getClass().getName() + ":" + name;
  }

  @Override
  public Spell getSpell() {
    ClickableItem clickableItem = getSession().getClickableItem(name);

    return clickableItem == null ? null : getSession().getSpell(clickableItem.getSpellId());
  }

  @Override
  public boolean isDetrimental() {
    Spell spell = getSpell();

    return spell == null ? false : spell.isDetrimental();
  }

  @Override
  public TargetType getTargetType() {
    Spell spell = getSpell();

    return spell == null ? TargetType.SELF : spell.getTargetType();
  }

  @Override
  public double getRange() {
    Spell spell = getSpell();

    return spell == null ? 0 : spell.getRange();
  }
}
