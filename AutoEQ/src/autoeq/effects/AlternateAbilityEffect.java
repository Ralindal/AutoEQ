package autoeq.effects;

import autoeq.eq.EverquestSession;
import autoeq.eq.ExpressionListener;
import autoeq.eq.Spell;

public class AlternateAbilityEffect extends AbstractSpellBasedEffect {
  private final String name;
  private final int agro;

  private long readySince;
  private boolean ready;
  private boolean usable;
  private long readyMillis;

  public AlternateAbilityEffect(EverquestSession session, String name, Spell spell, int agro, long lockOutMillis) {
    super(session, spell, lockOutMillis);

    this.name = name;
    this.agro = agro;

    int id = session.getCharacterDAO().getAltAbilityID(name);

    if(id >= 0) {
      session.registerTimer("A " + id, new ExpressionListener() {
        @Override
        public void stateUpdated(String result) {
          long millis = Long.parseLong(result) * 1000;

          if(millis < 0) {
            millis = 60 * 1000;
          }

          boolean nowReady = result.equals("0");

          if(!ready && nowReady) {
            readySince = System.currentTimeMillis();
          }

          usable = !result.equals("-1");
          ready = nowReady;
          readyMillis = millis;
        }
      });
    }
  }

  public String getName() {
    return name;
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
  public void internalActivate() {
    getSession().doCommand("/alt activate ${Me.AltAbility[" + name + "].ID}");
  }

  @Override
  public long getReadyMillis() {
    return readyMillis;
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
  public boolean internalIsUsable() {
    return usable;
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
