package autoeq.eq;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import autoeq.effects.Effect;
import autoeq.effects.Effect.Type;
import autoeq.expr.Parser;
import autoeq.expr.SyntaxException;

public class GemAssigner {
  private final EverquestSession session;
  private final Map<ParsedEffectGroup, GroupGemStatus> gemStates = new HashMap<>();
  private final Set<Integer> usedGems = new HashSet<>();
  private final Set<Integer> spellIdsMemmed = new HashSet<>();

  public GemAssigner(EverquestSession session) {
    this.session = session;
  }

  /**
   * Returns a Gem if for this group a new spell needs to be memorized.  Returns
   * null if enough spells were memorized, there are no more gems or if it was
   * already memorized.
   *
   * @param effectLine
   * @return a Gem or null
   */
  public Gem getGem(ParsedEffectLine effectLine) {
    ParsedEffectGroup group = effectLine.getGroup();
    Effect effect = effectLine.getEffect();

    if(effect == null) {
      return null;
    }

    GroupGemStatus gemStatus = gemStates.get(group);

    if(gemStatus == null) {
      gemStatus = new GroupGemStatus(group);
      gemStates.put(group, gemStatus);
    }

    if(effect.getType() != Effect.Type.SPELL && effect.getType() != Type.SONG) {
      gemStatus.slotsLeft--;  // If first matching effect is not a spell or song, reduce number of effects needing to be memmed and exit.
      return null;
    }

    Spell spell = effectLine.getEffect().getSpell();

    if(gemStatus.gemSum >= 1.0 || gemStatus.slotsLeft == 0 || gemStatus.gemsAvailable.isEmpty() || spellIdsMemmed.contains(spell.getId())) {
      return null;
    }

    /*
     * Sum Expression can limit number of spells in a group:
     */

    String gemSumExpression = group.getGemSumExpression();

    if(gemSumExpression != null) {
      ExpressionRoot root = new ExpressionRoot(session, null, null, null, effectLine.getEffect());

      try {
        Number number = (Number)Parser.parse(root, gemSumExpression);

        gemStatus.gemSum += number.doubleValue();
      }
      catch(SyntaxException e) {
        throw new RuntimeException(e);
      }
    }

    /*
     * Each spell takes up a slot:
     */

    gemStatus.slotsLeft--;

    /*
     * Lock the current gem if already memmed:
     */

    int currentGem = session.getMe().getGem(spell);

    if(currentGem != 0) {
      session.getMe().lockSpellSlot(currentGem, 5);
      usedGems.add(currentGem);
      spellIdsMemmed.add(spell.getId());
      return null;
    }

    /*
     * Attempt to give the spell a slot:
     */

    tryAgain:
    for(;;) {
      if(gemStatus.gemsAvailable.isEmpty()) {
        return null;
      }

      int gem = Integer.parseInt(gemStatus.gemsAvailable.remove(0));

      if(usedGems.contains(gem)) {
        continue tryAgain;
      }

      if(session.getMe().isSafeToMemorizeSpell()) {
        if(gem != 0) {
          usedGems.add(gem);
        }

        spellIdsMemmed.add(spell.getId());

        return new Gem(spell, gem, 5);
      }
    }
  }

  private static class GroupGemStatus {
    private final List<String> gemsAvailable;

    private int slotsLeft;
    private double gemSum;

    public GroupGemStatus(ParsedEffectGroup group) {
      gemsAvailable = group.getGems();

      slotsLeft = gemsAvailable.size();
    }
  }
}
