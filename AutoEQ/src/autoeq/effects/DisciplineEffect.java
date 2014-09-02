package autoeq.effects;

import autoeq.eq.EverquestSession;
import autoeq.eq.ExpressionListener;
import autoeq.eq.Spell;

public class DisciplineEffect extends AbstractSpellBasedEffect {
  private final String name;
  private final int agro;

  private long readyMillis;

  public DisciplineEffect(EverquestSession session, String name, Spell spell, int agro, long lockOutMillis) {
    super(session, spell, lockOutMillis);

    this.name = name;
    this.agro = agro;

    String result = session.translate("${Me.CombatAbility[" + spell.getName() + "]}");

    if(result.matches("[0-9]+")) {
      session.registerTimer("C " + result, new ExpressionListener() {
        @Override
        public void stateUpdated(String result) {
          long millis = Long.parseLong(result) * 1000;

          if(millis < 0) {
            millis = 60 * 1000;
          }

          readyMillis = millis;
        }
      });
    }
  }

//  @Override
//  public int getCastTime() {
//    return 0;
//  }

  @Override
  public Type getType() {
    return Type.DISCIPLINE;
  }

  @Override
  public int getAgro() {
    return agro;
  }

  @Override
  public void internalActivate() {
    getSession().doCommand("/nomod /doability \"" + getSpell().getName() + "\"");
  }

  @Override
  public boolean internalIsReady() {
    return readyMillis == 0;
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
    return readyMillis;
  }

  @Override
  protected boolean isUnaffectedBySilence() {
    return true;
  }

  @Override
  protected boolean usesSpellCasting() {
    return false;
  }
}
