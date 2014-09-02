package autoeq.expr;

import java.lang.reflect.Method;
import java.util.regex.Pattern;

import autoeq.expr.Parser.TypedValue;

public class Token {
  private final String text;
  private final String separator;
  private final int position;

  private Method resolvedMethod;
  private TypedValue literal = null;

  public Token(String text, String separator, int position) {
    this.text = text;
    this.separator = separator;
    this.position = position;
  }

  public String getText() {
    return text;
  }

  public String getSeparator() {
    return separator;
  }

  public int getPosition() {
    return position;
  }

  public Method getResolvedMethod() {
    return resolvedMethod;
  }

  public void setResolvedMethod(Method method) {
    this.resolvedMethod = method;
  }

  public boolean isLiteralResolved() {
    return literal != null;
  }

  public TypedValue getLiteral() {
    return literal;
  }

  public void setLiteral(TypedValue literal) {
    this.literal = literal;
  }

  public boolean matches(Pattern pattern) {
    return pattern.matcher(text).matches();
  }

  @Override
  public String toString() {
    return "'" + text + "' at " + position;
  }
}