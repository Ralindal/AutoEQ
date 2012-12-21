package autoeq.effects;

import autoeq.eq.EverquestSession;
import autoeq.eq.ExpressionListener;
import autoeq.eq.Spell;

public class ItemEffect implements Effect {
  private final String name;
  private final Spell spell;
  private final int agro;
  private final int castTime;

  private boolean ready;

  public ItemEffect(EverquestSession session, String name, Spell spell, int agro) {
    this.name = name;
    this.spell = spell;
    this.agro = agro;

    this.castTime = (int)(Double.parseDouble(session.translate("${FindItem[=" + name + "].CastTime}")) * 1000);

    session.registerExpression("${Cast.Ready[" + name + "|item]}", new ExpressionListener() {
      @Override
      public void stateUpdated(String result) {
        ready = result.equals("TRUE");
      }
    });
  }

  @Override
  public int getCastTime() {
    return castTime;
  }

  @Override
  public Spell getSpell() {
    return spell;
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
  public String getCastingLine() {
    return "/nomodkey /itemnotify ${FindItem[=" + name + "].InvSlot} rightmouseup";
  }

  @Override
  public boolean isReady() {
    return ready;
  }

  @Override
  public String toString() {
    return name;
  }

  @Override
  public boolean willUseGOM() {
    return false;
  }
}
