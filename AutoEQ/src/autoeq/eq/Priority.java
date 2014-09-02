package autoeq.eq;

import autoeq.effects.Effect;
import autoeq.expr.Parser;
import autoeq.expr.SyntaxException;

public class Priority {
  public static double decodePriority(EverquestSession session, Spawn target, Effect effect, String priorityExpr, int basePriority) {
    if(priorityExpr == null || priorityExpr.isEmpty()) {
      return basePriority;
    }

    try {
      return ((Number)Parser.parse(new ExpressionRoot(session, target, null, null, effect), priorityExpr)).doubleValue();
    }
    catch(SyntaxException e) {
      session.logErr("Error parsing priority '" + priorityExpr + "', using basePriority (" + basePriority + ") for " + effect + ": " + e);
      return basePriority;
    }
  }
}
