package autoeq.effects;

import autoeq.eq.EverquestSession;
import autoeq.eq.ExpressionListener;
import autoeq.eq.Spell;

public class ItemEffect implements Effect {
  private final String name;
  private final Spell spell;
  private final int agro;

  private boolean ready;
  
  public ItemEffect(EverquestSession session, String name, Spell spell, int agro) {
    this.name = name;
    this.spell = spell;
    this.agro = agro;
    
    session.registerExpression("${Cast.Ready[" + name + "|item]}", new ExpressionListener() {
      @Override
      public void stateUpdated(String result) {
        ready = result.equals("TRUE");
      }
    });
  }

  @Override
  public Spell getSpell() {
    return spell;
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
    return "/nomod /casting \"" + name + "\" item";
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
