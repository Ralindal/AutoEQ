package autoeq.eq;

import autoeq.effects.Effect;
import autoeq.expr.Parser;
import autoeq.expr.SyntaxException;

public class Priority {
  public static double decodePriority(EverquestSession session, Effect effect, String priority, int basePriority) {
    if(priority == null) {
      return basePriority;
    }

    try {
      return ((Number)Parser.parse(new ExpressionRoot(session, null, null, null, effect), priority)).doubleValue();
    }
    catch(SyntaxException e) {
      System.err.println("Error parsing priority '" + priority + "', using basePriority (" + basePriority + "): " + e);
      return basePriority;
    }
  }
}
