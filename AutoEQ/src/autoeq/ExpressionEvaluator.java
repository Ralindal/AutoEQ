package autoeq;

import java.util.List;

import autoeq.expr.Parser;
import autoeq.expr.SyntaxException;


public class ExpressionEvaluator {
  
  public static boolean evaluate(List<String> conditions, Object root, Object errorObject) {
    boolean valid = true;
    
    if(conditions != null) {
      for(String condition : conditions) {
        try {
          if(!valid) {
            break;
          }
          
          valid = valid && (Boolean)Parser.parse(root, condition);
        }
        catch(SyntaxException e) {
          System.err.println("Syntax error in condition of " + errorObject.toString() + ": " + condition);
          System.err.println(e.getMessage());
          break;
        }
      }
    }
    
    return valid;
  }
}
