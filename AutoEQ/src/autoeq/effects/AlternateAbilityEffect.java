package autoeq.effects;

import autoeq.eq.EverquestSession;
import autoeq.eq.ExpressionListener;
import autoeq.eq.Spell;

public class AlternateAbilityEffect implements Effect {
  private final String name;
  private final Spell spell;
  private final int agro;

  private boolean ready;
  
  public AlternateAbilityEffect(EverquestSession session, String name, Spell spell, int agro) {
    this.name = name;
    this.spell = spell;
    this.agro = agro;
    
    session.registerExpression("${Cast.Ready[" + name + "|alt]}", new ExpressionListener() {
      public void stateUpdated(String result) {
        ready = result.equals("TRUE");
      }
    });
  }

  public Spell getSpell() {
    return spell;
  }

  public Type getType() {
    return Type.ABILITY;
  }
  
  public int getAgro() {
    return agro;
  }
  
  public String getCastingLine() {
    return "/casting \"" + name + "\" alt";
  }

  public boolean isReady() {
    return ready;
  }
  
  @Override
  public String toString() {
    return name;
  }

  public boolean willUseGOM() {
    return false;
  }
}
