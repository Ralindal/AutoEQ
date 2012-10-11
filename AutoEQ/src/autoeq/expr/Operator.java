package autoeq.expr;

public abstract class Operator {
  public enum Type {UNARY, BINARY}
  
  private final int level;
  private final Type type;
 
  public Operator(int level, Type type) {
    this.level = level;
    this.type = type;
  }
  
  public int getLevel() {
    return level;
  }
  
  public Type getType() {
    return type;
  }

  public Object shortCutEvaluation(Object left) throws SyntaxException {
    return null;
  }
      
  public abstract Object operate(Object left, Object right) throws SyntaxException;
}