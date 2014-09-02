package autoeq.eq;

public class CharacterDAO {
  private final EverquestSession session;

  public CharacterDAO(EverquestSession session) {
    this.session = session;
  }

  public Spell getSpellFromBook(String name) {
    String result = session.translate("${Me.Book[" + name + "]} ${Me.Book[" + name + " Rk. II]} ${Me.Book[" + name + " Rk.II]} ${Me.Book[" + name + " Rk. III]} ${Me.Book[" + name + " Rk.III]}");

    for(String s : result.split(" ")) {
      if(!s.equals("NULL")) {
        return session.getSpell(Integer.parseInt(session.translate("${Me.Book[" + s + "].ID}")));
      }
    }

    return null;
  }

  public Spell getCombatAbilityFromList(String name) {
    String result = session.translate("${Me.CombatAbility[${Me.CombatAbility[" + name + " Rk. III]}].ID} ${Me.CombatAbility[${Me.CombatAbility[" + name + " Rk.III]}].ID} ${Me.CombatAbility[${Me.CombatAbility[" + name + " Rk. II]}].ID} ${Me.CombatAbility[${Me.CombatAbility[" + name + " Rk.II]}].ID} ${Me.CombatAbility[${Me.CombatAbility[" + name + "]}].ID}");

    for(String s : result.split(" ")) {
      if(!s.equals("NULL")) {
        return session.getSpell(Integer.parseInt(session.translate("${Spell[" + s + "].ID}")));
      }
    }

    return null;
  }

  public Spell getAltAbility(String name) {
    String result = session.translate("${Me.AltAbility[" + name + "].Spell.ID}");

    if(result.matches("[0-9]+")) {
      return session.getSpell(Integer.parseInt(result));
    }

    return null;
  }

  public int getAltAbilityID(String name) {
    String result = session.translate("${Me.AltAbility[" + name + "].ID}");

    if(result.matches("[0-9]+")) {
      return Integer.parseInt(result);
    }

    return -1;
  }

  public int getSpellSlots() {
    String mnemRet = session.translate("${Me.AltAbility[Mnemonic Retention]}");

    if(!mnemRet.equals("NULL")) {
      int aaSpent = Integer.parseInt(mnemRet);
      return aaSpent == 0 ? 8 :
             aaSpent == 3 ? 9 :
             aaSpent == 9 ? 10 :
             aaSpent == 15 ? 11 : 12;
    }
    else {
      return 8;
    }
  }
}
