package autoeq.expr;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import autoeq.expr.Operator.Type;


public class Parser {
  private static final Pattern PATTERN =
    Pattern.compile("([A-Za-z]+|-?[0-9]+(\\.[0-9]+)?|\"(\\\\.|[^\\\\\"])*\"|\\(|\\)|[^A-Za-z0-9() ]+)");
//    Pattern.compile("([A-Za-z]+|-?[0-9]+(\\.[0-9]+)?|\\(|\\)|[^A-Za-z0-9() ]+)");

  private static final Object UNDEFINED = new Object();

  private static final Map<String, Operator> OPERATORS = new HashMap<>();

  static {
    OPERATORS.put("!", new Operator(3, Type.UNARY) {
      @Override
      public Object operate(Object left, Object right) throws SyntaxException {
        return !(Boolean)right;
      }
    });

    OPERATORS.put("/", new Operator(5, Type.BINARY) {
      @Override
      public Object operate(Object left, Object right) throws SyntaxException {
        return toDouble(left) / toDouble(right);
      }
    });

    OPERATORS.put("*", new Operator(5, Type.BINARY) {
      @Override
      public Object operate(Object left, Object right) throws SyntaxException {
        return toDouble(left) * toDouble(right);
      }
    });

    OPERATORS.put("+", new Operator(6, Type.BINARY) {
      @Override
      public Object operate(Object left, Object right) throws SyntaxException {
        if(left instanceof String) {
          return left.toString() + right.toString();
        }
        else {
          return toDouble(left) + toDouble(right);
        }
      }
    });

    OPERATORS.put("-", new Operator(6, Type.BINARY) {
      @Override
      public Object operate(Object left, Object right) throws SyntaxException {
        return toDouble(left) - toDouble(right);
      }
    });

    OPERATORS.put(">", new Operator(8, Type.BINARY) {
      @Override
      public Object operate(Object left, Object right) throws SyntaxException {
        return toDouble(left) > toDouble(right);
      }
    });

    OPERATORS.put("<", new Operator(8, Type.BINARY) {
      @Override
      public Object operate(Object left, Object right) throws SyntaxException {
        return toDouble(left) < toDouble(right);
      }
    });

    OPERATORS.put(">=", new Operator(8, Type.BINARY) {
      @Override
      public Object operate(Object left, Object right) throws SyntaxException {
        return toDouble(left) >= toDouble(right);
      }
    });

    OPERATORS.put("<=", new Operator(8, Type.BINARY) {
      @Override
      public Object operate(Object left, Object right) throws SyntaxException {
        return toDouble(left) <= toDouble(right);
      }
    });

    OPERATORS.put("==", new Operator(9, Type.BINARY) {
      @Override
      public Object operate(Object left, Object right) throws SyntaxException {
        return isEqual(left, right);
      }
    });

    OPERATORS.put("!=", new Operator(9, Type.BINARY) {
      @Override
      public Object operate(Object left, Object right) throws SyntaxException {
        return !isEqual(left, right);
      }
    });

    OPERATORS.put("&&", new Operator(13, Type.BINARY) {
      @Override
      public Object shortCutEvaluation(Object left) throws SyntaxException {
        if(!((Boolean)left).booleanValue()) {
          return Boolean.FALSE;
        }
        return null;
      }

      @Override
      public Object operate(Object left, Object right) throws SyntaxException {
        return (Boolean)left && (Boolean)right;
      }
    });

    OPERATORS.put("||", new Operator(14, Type.BINARY) {
      @Override
      public Object shortCutEvaluation(Object left) throws SyntaxException {
        if(((Boolean)left).booleanValue()) {
          return Boolean.TRUE;
        }
        return null;
      }

      @Override
      public Object operate(Object left, Object right) throws SyntaxException {
        return (Boolean)left || (Boolean)right;
      }
    });

    OPERATORS.put("contains", new Operator(7, Type.BINARY) {
      @Override
      public Object operate(Object left, Object right) throws SyntaxException {
        return ((Collection<?>)left).contains(right);
      }
    });
  }

  public static double toDouble(Object o) throws SyntaxException {
    if(o instanceof Integer) {
      return ((Integer)o).doubleValue();
    }
    else if(o instanceof Double) {
      return (Double)o;
    }

    throw new SyntaxException("Not numeric: " + (o == null ? "null" : o.getClass()));
  }

  private static boolean isEqual(Object left, Object right) throws SyntaxException {
    if(left == null || right == null) {
      if(left != null && (left instanceof Number || left instanceof Boolean)) {
        throw new SyntaxException("Comparison with null not allowed for " + left.getClass());
      }
      if(right != null && (right instanceof Number || right instanceof Boolean)) {
        throw new SyntaxException("Comparison with null not allowed for " + right.getClass());
      }

      return left == right;
    }
    else if(left instanceof String) {
      return left.toString().equals(right.toString());
    }
    else if(left instanceof Number) {
      return toDouble(left) == toDouble(right);
    }
    else if(left instanceof Enum && !(right instanceof Enum)) {
      return ((Enum<?>)left).name().equals(right.toString());
    }
    else if(right instanceof Enum && !(left instanceof Enum)) {
      return ((Enum<?>)right).name().equals(left.toString());
    }
    else {
//      System.err.println("l/r = " + left + " / " + right + "; left.getClass = " + left.getClass().isEnum() + "; " + left.getClass().getSuperclass());
      return left.equals(right);
    }
  }

  public static Object parse(Object root, String expr) throws SyntaxException {
    List<Token> tokens = new ArrayList<>();
    Matcher matcher = PATTERN.matcher(expr);
    int skippedStart = 0;

    while(matcher.find()) {
      tokens.add(new Token(matcher.group(0), expr.substring(skippedStart, matcher.start()), matcher.start()));
      skippedStart = matcher.end();
    }

    try {
      List<Object> result = parse(root, tokens, 99, false);
//      System.out.println(expr + " = " + result);

      return result.get(result.size() - 1);
    }
    catch(SyntaxException e) {
      char[] spaces = new char[e.getToken().getPosition()];
      Arrays.fill(spaces, ' ');

      throw new SyntaxException("Syntax error near " + e.getToken() + ": " + e.getMessage() + "\n" + expr + "\n" + String.valueOf(spaces) + "^\n", e);
    }
    catch(Exception e) {
      throw new RuntimeException("Exception while parsing: " + expr, e);
    }
  }

  private static Object parseSingleResult(Object root, List<Token> tokens, int level, boolean parseOnly) throws SyntaxException {
    List<Object> results = parse(root, tokens, level, parseOnly);

    return results.get(results.size() - 1);
  }

  private static Pattern IDENTIFIER = Pattern.compile("[_A-Za-z][_A-Za-z0-9]*");
  private static Pattern INTEGER = Pattern.compile("-?[0-9]+");
  private static Pattern FLOAT = Pattern.compile("-?[0-9]+(\\.[0-9]+)?");

  private static List<Object> parse(Object root, List<Token> tokens, int level, boolean parseOnly) throws SyntaxException {
    if(tokens.size() == 0) {
      throw new SyntaxException("Token expected");
    }

    final List<Object> results = new ArrayList<>();
    Object result = UNDEFINED;
    boolean operatorExpected = false;

    while(tokens.size() > 0) {
      Token token = tokens.remove(0);

      Operator operator = OPERATORS.get(token.getText());
//      System.err.println("Getting operator for '" + token.getText() + "' -> " + operator + " : result = " + result);

      if(operator != null) {
        if(operator.getType() == Type.UNARY && result != UNDEFINED) {
          throw new SyntaxException("Binary operator expected", token);
        }
        if(operator.getType() == Type.BINARY && result == UNDEFINED) {
          throw new SyntaxException("Variable or literal expected", token);
        }

        if(level < operator.getLevel()) {
          // Operator found has lower precedence than current operator.  Return the
          // result and let the caller handle this operator instead.
          tokens.add(0, token);
          results.add(result);
          return results;
        }

        if(!parseOnly) {
          try {
            Object left = result;

            result = operator.shortCutEvaluation(left);
            if(result == null) {
              result = operator.operate(left, parseSingleResult(root, tokens, operator.getLevel(), parseOnly));
            }
            else {
              parseSingleResult(root, tokens, operator.getLevel(), true);  // result discarded as known already, just parse rest of tree
            }
          }
          catch(SyntaxException e) {
            if(e.getToken() == null) {
              e.setToken(token);
            }
            throw e;
          }
        }
        else {
          result = parseSingleResult(root, tokens, operator.getLevel(), true);  // result discarded later, but intermediate results needed for correct parsing
        }
      }
      else if(operatorExpected) {
        if(token.getText().equals(",")) {
          results.add(result);
          result = UNDEFINED;
          operatorExpected = false;
        }
        else if(token.getText().equals(".")) {
          result = callMethod(root, result, tokens, parseOnly, tokens.remove(0));

//          try {
//            result = readProperty(result, tokens.remove(0).getText());
//          }
//          catch(NoSuchMethodException e) {
//            throw new SyntaxException("Unknown member of " + result + ": " + token.getText(), token);
//          }
        }
        else {
          throw new SyntaxException("Operator expected", token);
        }
      }
      else {
        if(token.getText().equals("null")) {
          result = null;
        }
        else if(token.matches(IDENTIFIER) && !token.getText().equals("contains")) {
          result = callMethod(root, root, tokens, parseOnly, token);
        }
        else if(token.matches(INTEGER)) {
          result = Integer.parseInt(token.getText());
        }
        else if(token.matches(FLOAT)) {
          result = Double.parseDouble(token.getText());
        }
        else if(token.getText().equals("(")) {
          tokens.add(0, token);
          int index = findMatchingParenthesis(tokens);

          result = parseSingleResult(root, tokens.subList(1, index), 99, parseOnly);
          tokens.remove(0);  // remove opening parenthesis
          tokens.remove(0);  // remove closing parenthesis
        }
        else if(token.getText().startsWith("\"")) {
          String t = token.getText();

          if(!t.endsWith("\"")) {
            throw new SyntaxException("Unclosed string", token);
          }
          result = t.substring(1, t.length() - 1);
        }
        else {
          throw new SyntaxException("Variable or literal expected", token);
        }

        operatorExpected = true;
      }
    }

    results.add(result);

    return results;
  }

  private static Object callMethod(Object root, Object parent, List<Token> tokens, boolean parseOnly, Token token) throws SyntaxException {
    try {
      if(tokens.size() > 0 && tokens.get(0).getText().equals("(")) {
        List<Object> parameters = parse(root, tokens.subList(1, findMatchingParenthesis(tokens)), 99, parseOnly);
        tokens.remove(0);
        tokens.remove(0);

        return callMethod(parent, token.getText(), parameters);
      }
      else {
        return readProperty(parent, token.getText());
      }
    }
    catch(NoSuchMethodException e) {
      throw new SyntaxException("Unknown member of " + parent + ": " + token.getText(), token, e);
    }
  }

  private static int findMatchingParenthesis(List<Token> tokens) throws SyntaxException {
    // find matching bracket
    int openCount = 0;

    for(int i = 0; i < tokens.size(); i++) {
      if(tokens.get(i).getText().equals("(")) {
        openCount++;
      }
      else if(tokens.get(i).getText().equals(")")) {
        if(--openCount == 0) {
          return i;
        }
      }
    }

    throw new SyntaxException("Unmatched open parenthesis", tokens.get(0));
  }

//  private static Object parse(Object root, List<Token> tokens, int level) throws SyntaxException {
//    if(tokens.size() == 0) {
//      throw new SyntaxException("Token expected");
//    }
//
//    Object result = null;
//    boolean literalExpected = true;
//
//    nextToken:
//    while(tokens.size() > 0) {
//      Token token = tokens.remove(0);
//
//      if(token.getText().equals("null") && literalExpected) {
//        result = null;
//        literalExpected = false;
//      }
//      else if(token.matches("[A-Za-z]+") && !token.getText().equals("contains") && literalExpected) {
//        List<Object> parameters = new ArrayList<Object>();
//
//        if(tokens.get(0).getText().equals("(")) {
//          tokens.remove(0);
//
//          int openCount = 1;
//          int tokenIndex = 0;
//
//          while(tokenIndex < tokens.size()) {
//            String text = tokens.get(tokenIndex).getText();
//            System.err.println(" > (" + tokenIndex + ") " + text);
//
//            if(text.equals("(")) {
//              openCount++;
//            }
//            else if(text.equals(")")) {
//              if(--openCount == 0) {
//                parameters.add(parse(root, tokens.subList(0, tokenIndex), 99));
//                tokens.remove(0);
//                tokenIndex = 0;
//                break;
//              }
//            }
//            else if(openCount == 1 && text.equals(",")) {
//              parameters.add(parse(root, tokens.subList(0, tokenIndex), 99));
//              tokens.remove(0);
//              tokenIndex = 0;
//            }
//            tokenIndex++;
//          }
//
//          if(openCount != 0) {
//            throw new SyntaxException("Unmatched open parenthesis", token);
//          }
//
//          System.err.println("Result of bracket evaluation: " + parameters);
//        }
//
//        try {
//          result = readProperty(root, token.getText());
//          literalExpected = false;
//        }
//        catch(NoSuchMethodException e) {
//          throw new SyntaxException("Unknown member of " + root + ": " + token.getText(), token);
//        }
//      }
//      else if(token.matches("-?[0-9]+") && literalExpected) {
//        result = Integer.parseInt(token.getText());
//        literalExpected = false;
//      }
//      else if(token.getText().equals(".")) {
//        try {
//          result = readProperty(result, tokens.remove(0).getText());
//        }
//        catch(NoSuchMethodException e) {
//          throw new SyntaxException("Unknown member of " + result + ": " + token.getText(), token);
//        }
//      }
//      else if(token.getText().equals("(") && literalExpected) {
//        // find matching bracket
//        int openCount = 1;
//        for(int i = 0; i < tokens.size(); i++) {
//          if(tokens.get(i).getText().equals("(")) {
//            openCount++;
//          }
//          else if(tokens.get(i).getText().equals(")")) {
//            if(--openCount == 0) {
//              result = parse(root, tokens.subList(0, i), 99);
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
//      else if(token.getText().startsWith("\"")) {
//        String t = token.getText();
//
//        if(!t.endsWith("\"")) {
//          throw new SyntaxException("Unclosed string", token);
//        }
//        result = t.substring(1, t.length() - 1);
////        String s = "";
////
////        for(;;) {
////          if(tokens.size() == 0) {
////            throw new SyntaxException("Unclosed string", token);
////          }
////          token = tokens.remove(0);
////          String t = token.getText();
////          s += token.getSeparator();
////          if(t.equals("\"")) {
////            break;
////          }
////          s += t;
////        }
////
////        result = s;
//      }
//      else {
//        for(Operator operator : OPERATORS) {
//          if(operator.getToken().equals(token.getText())) {
//            if(level < operator.getLevel()) {
//              tokens.add(0, token);
//              return result;
//            }
//
//            try {
//              result = operator.operate(result, parse(root, tokens, operator.getLevel()));
//              continue nextToken;
//            }
//            catch(SyntaxException e) {
//              if(e.getToken() == null) {
//                e.setToken(token);
//              }
//              throw e;
//            }
//          }
//        }
//
//        throw new SyntaxException("Operator expected", token);
//      }
//    }
//
//    return result;
//  }


  private static Object readProperty(Object root, String propertyName) throws NoSuchMethodException {
    if(root == null) {
      return null;
    }

    try {
      Method method;

      try {
        method = root.getClass().getMethod(propertyName, (Class[])null);
      }
      catch(NoSuchMethodException e) {
        method = root.getClass().getMethod("get" + propertyName.substring(0, 1).toUpperCase() + propertyName.substring(1), (Class[])null);
      }

      return method.invoke(root, (Object[])null);
    }
    catch(SecurityException e) {
      throw new RuntimeException(e);
    }
    catch(IllegalAccessException e) {
      throw new RuntimeException(e);
    }
    catch(InvocationTargetException e) {
      throw new RuntimeException(e);
    }
  }

  private static Object callMethod(Object root, String propertyName, List<Object> parameters) throws NoSuchMethodException {
    try {
      Method method;

      method = searchForMethod(root.getClass(), propertyName, parameters.toArray());

      if(method == null) {
        method = searchForMethod(root.getClass(), "get" + propertyName.substring(0, 1).toUpperCase() + propertyName.substring(1), parameters.toArray());
      }

      if(method == null) {
        throw new NoSuchMethodException(propertyName);
      }

      return method.invoke(root, InspectionUtils.convertSourcesToBeCompatible(method.getParameterTypes(), parameters.toArray()));
    }
    catch(SecurityException e) {
      throw new RuntimeException(e);
    }
    catch(IllegalAccessException e) {
      throw new RuntimeException(e);
    }
    catch(InvocationTargetException e) {
      throw new RuntimeException(e);
    }
  }

  private static Method searchForMethod(Class<?> type, String name, Object... parms) {
    Method[] methods = type.getMethods();

    for(int i = 0; i < methods.length; i++) {
      // Has to be named the same of course.
      if(!methods[i].getName().equals(name)) {
        continue;
      }

      Class<?>[] types = methods[i].getParameterTypes();

      // Does it have the same number of arguments that we're looking for.
      if(types.length != parms.length) {
        continue;
      }

      // Check for type compatibility
      if(InspectionUtils.areTypesAlmostCompatible(types, parms)) {
        return methods[i];
      }
    }
    return null;
  }
}
