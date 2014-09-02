package autoeq.expr;

import java.util.List;

import autoeq.expr.Parser.TypedValue;

public abstract class Operator {
  private final String description;
  private final int level;

  public Operator(String description, int level) {
    this.description = description;
    this.level = level;
  }

  public int getLevel() {
    return level;
  }

  public abstract TypedValue operate(TypedValue left, TypedValue root, List<Token> tokens, boolean parseOnly) throws SyntaxException;

  @Override
  public String toString() {
    return "Operator[" + description + "@" + level +"]";
  }
}