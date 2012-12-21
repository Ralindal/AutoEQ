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

    session.registerExpression("${Me.AltAbilityReady[" + name + "]}", new ExpressionListener() {
      @Override
      public void stateUpdated(String result) {
        ready = result.equals("TRUE");
      }
    });
  }

  public String getName() {
    return name;
  }

  @Override
  public Spell getSpell() {
    return spell;
  }

  @Override
  public int getCastTime() {
    return spell.getCastTime();
  }

  @Override
  public Type getType() {
    return Type.ABILITY;
  }

  @Override
  public int getAgro() {
    return agro;
  }

  @Override
  public String getCastingLine() {
    return "/alt activate ${Me.AltAbility[" + name + "].ID}";
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
