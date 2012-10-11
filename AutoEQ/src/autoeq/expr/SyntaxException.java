package autoeq.expr;

public class SyntaxException extends Exception {
  private Token token;

  public SyntaxException(String text) {
    super(text);
  }
  
  public SyntaxException(String text, Token token) {
    super(text);
    this.token = token;
  }

  public SyntaxException(String text, Throwable cause) {
    super(text, cause);
  }
  
  public SyntaxException(String text, Token token, Throwable cause) {
    super(text, cause);
    this.token = token;
  }
  
  public void setToken(Token token) {
    this.token = token;
  }
  
  public Token getToken() {
    return token;
  }
}
