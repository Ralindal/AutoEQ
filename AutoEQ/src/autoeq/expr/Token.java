package autoeq.expr;

import java.util.regex.Pattern;

public class Token {
  private final String text;
  private final String separator;
  private final int position;

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

  public boolean matches(Pattern pattern) {
    return pattern.matcher(text).matches();
  }

  @Override
  public String toString() {
    return "'" + text + "' at " + position;
  }
}