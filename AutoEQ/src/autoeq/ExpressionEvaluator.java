package autoeq;

import java.util.List;

import autoeq.expr.Parser;
import autoeq.expr.SyntaxException;


public class ExpressionEvaluator {

  public static boolean evaluate(List<String> conditions, Object root, Object errorObject) {
    return evaluateAndReturnFailingCondition(conditions, root, errorObject) == null;
  }

  public static String evaluateAndReturnFailingCondition(List<String> conditions, Object root, Object errorObject) {
    if(conditions != null) {
      for(String condition : conditions) {
        try {
          if(!(Boolean)Parser.parse(root, condition)) {
            return "EvaluateFalse(" + condition + ")";
          }
        }
        catch(SyntaxException e) {
          System.err.println("Syntax error in condition of " + errorObject.toString() + ": " + condition);
          System.err.println(e.getMessage());
          return "EvaluateSyntaxError(" + condition + ")";
        }
      }
    }

    return null;
  }

  public static double sum(List<String> expressions, Object root, Object errorObject) {
    double sum = 0.0;

    if(expressions != null) {
      for(String expression : expressions) {
        try {
          sum += ((Number)Parser.parse(root, expression)).doubleValue();
        }
        catch(SyntaxException e) {
          System.err.println("Syntax error in condition of " + errorObject.toString() + ": " + expression);
          System.err.println(e.getMessage());
          break;
        }
      }
    }

    return sum;
  }
}
