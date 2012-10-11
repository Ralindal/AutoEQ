package autoeq;

import java.util.Arrays;
import java.util.Collection;

import autoeq.expr.Parser;
import autoeq.expr.SyntaxException;


public class ExpressionTest {

  public static void main(String[] args) throws SyntaxException {
    Root root = new Root(new Spawn2(), new Spawn2());

    String expr = "mana + 10 > 60";
    System.out.println(Parser.parse(root, expr));
    String expr2 = "((3 + 4) * 2) / (2 - 1)";
    System.out.println(Parser.parse(root, expr2));
    String expr3 = "!self";
    System.out.println(Parser.parse(root, expr3));
    String expr4 = "10 - -10";
    System.out.println(Parser.parse(root, expr4));
    String expr5 = "!(self)";
    System.out.println(Parser.parse(root, expr5));
    String expr6 = "target.isMe && !me.isSitting && me.inCombat";
    System.out.println(Parser.parse(root, expr6));
    System.out.println(Parser.parse(root, "target.mana == 1024"));
    System.out.println(Parser.parse(root, "target.name == \"Testchar\""));
    System.out.println(Parser.parse(root, "target.names contains \"Testchar Cleric2\" || target.names contains \"Testchar Testchar2\""));
    System.out.println(Parser.parse(root, "target.pet == null"));
    System.out.println(Parser.parse(root, "target.pet != null"));
    System.out.println(Parser.parse(root, "0 == 1 || 1 == 1"));
    System.out.println(Parser.parse(root, "target.mana < 95 || target.mana > 99"));
    System.out.println(Parser.parse(root, "target.mana > 95 || target.mana < 90"));
    System.out.println(Parser.parse(root, "(target.mana > 95 || target.mana < 99)"));
    System.out.println(Parser.parse(root, "(target.mana > 95 || target.names contains \"Testchar Cleric2\")"));

//    Parser.parse(root, "10 10");
    System.out.println(Parser.parse(root, "me.isExtendedTarget(target) || (target == me && target.mana < 99)"));
  }

//  private static void parse(Object root, String expr) {
//    List<Token> tokens = new ArrayList<Token>();
//    Matcher matcher = PATTERN.matcher(expr);
//
//    while(matcher.find()) {
//      tokens.add(new Token(matcher.group(0), matcher.start()));
//    }
//
//    try {
//      System.out.println(expr + " = " + parse(root, tokens, 99));
//    }
//    catch(SyntaxException e) {
//      e.getToken();
//      System.err.println("Syntax Error near " + e.getToken() + ": " + e.getMessage());
//      System.err.println(expr);
//      char[] spaces = new char[e.getToken().getPosition()];
//      Arrays.fill(spaces, ' ');
//      System.err.println(String.valueOf(spaces) + "^");
//    }
//  }
//
//  private static Object parse(Object root, List<Token> tokens, int level) throws SyntaxException {
//    Object result = null;
//
//    while(tokens.size() > 0) {
//      System.out.println("Parsing " + tokens.get(0));
//      Token token = tokens.remove(0);
//
//      if(token.matches("[a-z]+")) {
//        result = readProperty(root, token.getText());
//      }
//      else if(token.matches("-?[0-9]+")) {
//        result = Integer.parseInt(token.getText());
//      }
//      else if(token.getText().equals(".")) {
//        result = readProperty(result, tokens.remove(0).getText());
//      }
//      else if(token.getText().equals("(")) {
//        // find matching bracket
//        int openCount = 1;
//        for(int i = 0; i < tokens.size(); i++) {
//          if(tokens.get(i).getText().equals("(")) {
//            openCount++;
//          }
//          else if(tokens.get(i).getText().equals(")")) {
//            if(--openCount == 0) {
//              result = parse(root, tokens.subList(0, i), 99);
//              //System.out.println("result from parenthesis = " + result);
//              tokens.remove(0);
//              break;
//            }
//          }
//        }
//
//        if(openCount != 0) {
//          throw new SyntaxException("Unmatched open parenthesis", token);
//        }
//      }
//      else {
//        for(Operator operator : OPERATORS) {
//          if(operator.token.equals(token.getText())) {
//            if(level < operator.level) {
//              tokens.add(0, token);
//              return result;
//            }
//
//            try {
//              result = operator.operate(result, parse(root, tokens, operator.level));
//            }
//            catch(SyntaxException e) {
//              e.setToken(token);
//              throw e;
//            }
//          }
//        }
//      }
//    }
//
//    return result;
//  }
//
//  private static Object readProperty(Object root, String propertyName) {
//    try {
//      Method method = root.getClass().getMethod(propertyName, (Class[])null);
//
//      return method.invoke(root, (Object[])null);
//    }
//    catch(SecurityException e) {
//      throw new RuntimeException(e);
//    }
//    catch(NoSuchMethodException e) {
//      throw new RuntimeException(e);
//    }
//    catch(IllegalAccessException e) {
//      throw new RuntimeException(e);
//    }
//    catch(InvocationTargetException e) {
//      throw new RuntimeException(e);
//    }
//  }

//  public abstract static class Operator {
//    private final int level;
//    private final String token;
//
//    public Operator(String token, int level) {
//      this.token = token;
//      this.level = level;
//    }
//
//    public abstract Object operate(Object left, Object right) throws SyntaxException;
//  }

  public static double toDouble(Object o) throws SyntaxException {
    if(o instanceof Integer) {
      return ((Integer)o).doubleValue();
    }
    else if(o instanceof Double) {
      return (Double)o;
    }

    throw new SyntaxException("Not numeric: " + o.getClass());
  }

  public static class Root {
    private final Spawn2 target;
    private final Spawn2 me;

    public Root(Spawn2 me, Spawn2 target) {
      this.me = me;
      this.target = target;
    }

    public Spawn2 me() {
      return me;
    }

    public Spawn2 target() {
      return target;
    }
    public int mana() {
      return 50;
    }
    public boolean self() {
      return true;
    }


  }

  public static class Spawn2 {
    public boolean isMe() {
      return true;
    }

    public Spawn2 pet() {
      return null;
    }

    public boolean isSitting() {
      return false;
    }

    public boolean isExtendedTarget(Spawn2 spawn) {
      System.out.println(">>> called with: "+spawn);
      return false;
    }

    public boolean inCombat() {
      return true;
    }

    public int mana() {
      return 1024;
    }

    public String name() {
      return "Testchar";
    }

    public Collection<String> names() {
      return Arrays.asList(new String[] {"Testchar", "Testchar Testchar", "Testchar Cleric"});
    }
  }
}

