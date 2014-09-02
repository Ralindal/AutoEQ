package autoeq.expr;

import java.lang.reflect.Array;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Parser {
  private static final Pattern PATTERN =
    Pattern.compile("([_A-Za-z][_A-Za-z0-9]*|-?[0-9]+(\\.[0-9]+)?|\"(\\\\.|[^\\\\\"])*\"|\\(|\\)|[^A-Za-z0-9() ]+)");
//    Pattern.compile("([A-Za-z]+|-?[0-9]+(\\.[0-9]+)?|\\(|\\)|[^A-Za-z0-9() ]+)");

  private static final Object UNDEFINED = new Object();

  private static final Map<String, Operator> OPERATORS = new HashMap<>();

  static abstract class UnaryOperator extends Operator {
    public UnaryOperator(String description, int level) {
      super(description, level);
    }

    protected abstract TypedValue operate(Object value) throws SyntaxException;

    @Override
    public final TypedValue operate(TypedValue left, TypedValue root, List<Token> tokens, boolean parseOnly) throws SyntaxException {
      if(left != null) {
        throw new SyntaxException("Binary operator expected");
      }

      if(parseOnly) {
        Parser.parse(root, tokens, getLevel(), parseOnly);
        return new TypedValue(Boolean.class, null);
      }
      else {
        return operate(Parser.parse(root, tokens, getLevel(), parseOnly).value);
      }
    }
  }

  static abstract class CustomUnaryOperator extends Operator {
    public CustomUnaryOperator(String description, int level) {
      super(description, level);
    }

    protected abstract TypedValue customOperate(TypedValue root, List<Token> tokens, boolean parseOnly) throws SyntaxException;

    @Override
    public final TypedValue operate(TypedValue left, TypedValue root, List<Token> tokens, boolean parseOnly) throws SyntaxException {
      if(left != null) {
        throw new SyntaxException("Binary operator expected");
      }
      return customOperate(root, tokens, parseOnly);
    }
  }

  static abstract class BinaryOperator extends Operator {
    public BinaryOperator(String description, int level) {
      super(description, level);
    }

    protected abstract Object operate(Object left, Object right) throws SyntaxException;

    @Override
    public final TypedValue operate(TypedValue left, TypedValue root, List<Token> tokens, boolean parseOnly) throws SyntaxException {
      if(left == null) {
        throw new SyntaxException("Variable or literal expected");
      }

      if(parseOnly) {
        return Parser.parse(root, tokens, getLevel(), parseOnly);
      }
      else {
        return new TypedValue(operate(left.value, Parser.parse(root, tokens, getLevel(), parseOnly).value));
      }
    }
  }

  static abstract class CustomMultiOperator extends Operator {
    public CustomMultiOperator(String description, int level) {
      super(description, level);
    }

    protected abstract TypedValue customOperate(TypedValue left, TypedValue root, List<Token> tokens, boolean parseOnly) throws SyntaxException;

    @Override
    public final TypedValue operate(TypedValue left, TypedValue root, List<Token> tokens, boolean parseOnly) throws SyntaxException {
      if(left == null) {
        throw new SyntaxException("Variable or literal expected");
      }
      return customOperate(left, root, tokens, parseOnly);
    }
  }

  static {
    OPERATORS.put("(", new CustomUnaryOperator("()", 1) {
      @Override
      public TypedValue customOperate(TypedValue root, List<Token> tokens, boolean parseOnly) throws SyntaxException {
        int index = findMatchingParenthesis(tokens, 1);

        TypedValue result = parse(root, tokens.subList(0, index), 1000, parseOnly);
        tokens.remove(0);  // remove closing parenthesis

        return result;
      }
    });

    OPERATORS.put(".", new CustomMultiOperator(".", 2) {
      @Override
      protected TypedValue customOperate(TypedValue left, TypedValue root, List<Token> tokens, boolean parseOnly) throws SyntaxException {
        Token token = tokens.remove(0);

        return callMethod(root, left, tokens, parseOnly, token);
      }
    });

    OPERATORS.put("!", new UnaryOperator("!", 3) {
      @Override
      public TypedValue operate(Object value) throws SyntaxException {
        return new TypedValue(!(Boolean)value);
      }
    });

    // TODO currently not needed because null is returned when a call cannot be made
//    OPERATORS.put("?.", new CustomMultiOperator(2) {
//      @Override
//      protected Object customOperate(Object left, Object root, List<Token> tokens, boolean parseOnly) throws SyntaxException {
//        if(left == null) {
//          return null;
//        }
//        return callMethod(root, left, tokens, parseOnly, tokens.remove(0));
//      }
//    });

    OPERATORS.put("/", new BinaryOperator("/", 5) {
      @Override
      public Object operate(Object left, Object right) throws SyntaxException {
        if(left instanceof Double || right instanceof Double) {
          return toDouble(left) / toDouble(right);
        }
        else {
          return (Integer)left / (Integer)right;
        }
      }
    });

    OPERATORS.put("*", new BinaryOperator("*", 5) {
      @Override
      public Object operate(Object left, Object right) throws SyntaxException {
        if(left instanceof Double || right instanceof Double) {
          return toDouble(left) * toDouble(right);
        }
        else {
          return (Integer)left * (Integer)right;
        }
      }
    });

    OPERATORS.put("+", new BinaryOperator("+", 6) {
      @Override
      public Object operate(Object left, Object right) throws SyntaxException {
        if(left instanceof String) {
          return left.toString() + right.toString();
        }
        else if(left instanceof Double || right instanceof Double) {
          return toDouble(left) + toDouble(right);
        }
        else {
          return (Integer)left + (Integer)right;
        }
      }
    });

    OPERATORS.put("-", new BinaryOperator("-", 6) {
      @Override
      public Object operate(Object left, Object right) throws SyntaxException {
        if(left instanceof Double || right instanceof Double) {
          return toDouble(left) - toDouble(right);
        }
        else {
          return (Integer)left - (Integer)right;
        }
      }
    });

    OPERATORS.put(">", new BinaryOperator(">", 8) {
      @Override
      public Object operate(Object left, Object right) throws SyntaxException {
        return toDouble(left) > toDouble(right);
      }
    });

    OPERATORS.put("<", new BinaryOperator("<", 8) {
      @Override
      public Object operate(Object left, Object right) throws SyntaxException {
        return toDouble(left) < toDouble(right);
      }
    });

    OPERATORS.put(">=", new BinaryOperator(">=", 8) {
      @Override
      public Object operate(Object left, Object right) throws SyntaxException {
        return toDouble(left) >= toDouble(right);
      }
    });

    OPERATORS.put("<=", new BinaryOperator("<=", 8) {
      @Override
      public Object operate(Object left, Object right) throws SyntaxException {
        return toDouble(left) <= toDouble(right);
      }
    });

    OPERATORS.put("==", new BinaryOperator("==", 9) {
      @Override
      public Object operate(Object left, Object right) throws SyntaxException {
        return isEqual(left, right);
      }
    });

    OPERATORS.put("!=", new BinaryOperator("!=", 9) {
      @Override
      public Object operate(Object left, Object right) throws SyntaxException {
        return !isEqual(left, right);
      }
    });

    OPERATORS.put("&&", new CustomMultiOperator("&&", 13) {
      @Override
      public TypedValue customOperate(TypedValue left, TypedValue root, List<Token> tokens, boolean parseOnly) throws SyntaxException {
        if(parseOnly || !((Boolean)left.value).booleanValue()) {
          Parser.parse(root, tokens, getLevel(), true);  // disregard right hand side
          return new TypedValue(Boolean.FALSE);
        }
        else {
          return Parser.parse(root, tokens, getLevel(), parseOnly);
        }
      }
    });

    OPERATORS.put("||", new CustomMultiOperator("||", 14) {
      @Override
      public TypedValue customOperate(TypedValue left, TypedValue root, List<Token> tokens, boolean parseOnly) throws SyntaxException {
        if(parseOnly || ((Boolean)left.value).booleanValue()) {
          Parser.parse(root, tokens, getLevel(), true);  // disregard right hand side
          return new TypedValue(Boolean.TRUE);
        }
        else {
          return Parser.parse(root, tokens, getLevel(), parseOnly);
        }
      }
    });

    OPERATORS.put("?", new CustomMultiOperator("?", 15) {
      @Override
      public TypedValue customOperate(TypedValue left, TypedValue root, List<Token> tokens, boolean parseOnly) throws SyntaxException {
        TypedValue result;

        if(parseOnly || (Boolean)left.value) {
          result = Parser.parse(root, tokens, getLevel() + 1, parseOnly);

          if(tokens.isEmpty() || !tokens.remove(0).getText().equals(":")) {
            throw new SyntaxException("':' expected");
          }

          Parser.parse(root, tokens, getLevel() + 1, true);  // disregard right hand side
        }
        else {
          Parser.parse(root, tokens, getLevel() + 1, true);  // disregard left hand side

          if(tokens.isEmpty() || !tokens.remove(0).getText().equals(":")) {
            throw new SyntaxException("':' expected");
          }

          result = Parser.parse(root, tokens, getLevel() + 1, parseOnly);
        }

        return result;
      }
    });

    OPERATORS.put("?:", new CustomMultiOperator("?:", 15) {
      @Override
      public TypedValue customOperate(TypedValue left, TypedValue root, List<Token> tokens, boolean parseOnly) throws SyntaxException {
        if(left.value != null) {
          Parser.parse(root, tokens, getLevel(), true);  // disregard right hand side
          return left;
        }
        else {
          return Parser.parse(root, tokens, getLevel(), parseOnly);
        }
      }
    });

    OPERATORS.put("contains", new BinaryOperator("contains", 7) {
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
    else if(o instanceof Long) {
      return ((Long)o).doubleValue();
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

  public static class Script {
    private final String expr;
    private final List<Token> tokens;

    public Script(String expr, List<Token> tokens) {
      this.expr = expr;
      this.tokens = tokens;
    }

    public List<Token> getTokens() {
      return tokens;
    }

    public String getExpr() {
      return expr;
    }
  }

  public static Script compile(Class<?> rootClass, String expr) {
    List<Token> tokens = new ArrayList<>();
    Matcher matcher = PATTERN.matcher(expr);
    int skippedStart = 0;

    while(matcher.find()) {
      tokens.add(new Token(matcher.group(0), expr.substring(skippedStart, matcher.start()), matcher.start()));
      skippedStart = matcher.end();
    }

    return new Script(expr, tokens);
  }

  public static Object run(Object root, Script script) throws SyntaxException {
    try {
      List<TypedValue> result = parseList(root == null ? null : new TypedValue(root), new ArrayList<>(script.getTokens()), false);
//      System.out.println(expr + " = " + result);

      return result.get(result.size() - 1).value;
    }
    catch(SyntaxException e) {
      char[] spaces = new char[e.getToken().getPosition()];
      Arrays.fill(spaces, ' ');

      throw new SyntaxException("Syntax error near " + e.getToken() + ": " + e.getMessage() + "\n" + script.getExpr() + "\n" + String.valueOf(spaces) + "^\n", e.getToken(), e);
    }
    catch(Exception e) {
      throw new RuntimeException("Exception while parsing: " + script.getExpr(), e);
    }
  }

  public static Object parseUncached(Object root, String expr) throws SyntaxException {
    Script script = compile(root == null ? null : root.getClass(), expr);

    return run(root, script);
  }

  private static final Map<String, Script> CACHE = new HashMap<>();

  public static Object parse(Object root, String expr) throws SyntaxException {
    Script script = CACHE.get(expr);

    if(script == null) {
      System.out.println("PARSER: Compiling: " + expr);
      script = compile(root == null ? null : root.getClass(), expr);
      CACHE.put(expr, script);
    }

    long startMillis = System.currentTimeMillis();
    Object result = run(root, script);
    long runDuration = System.currentTimeMillis() - startMillis;

    if(runDuration > 3) {
      System.out.println("PARSER: " + runDuration + " ms for: " + expr);
    }

    return result;
  }

  private static Pattern IDENTIFIER = Pattern.compile("[_A-Za-z][_A-Za-z0-9]*");
  private static Pattern BOOLEAN = Pattern.compile("(true|false)");
  private static Pattern INTEGER = Pattern.compile("-?[0-9]+");
  private static Pattern FLOAT = Pattern.compile("-?[0-9]+(\\.[0-9]+)?");

  private static List<TypedValue> parseList(TypedValue root, List<Token> tokens, boolean parseOnly) throws SyntaxException {
    final List<TypedValue> results = new ArrayList<>();

    while(!tokens.isEmpty()) {
      results.add(parse(root, tokens, 1000, parseOnly));

      if(!tokens.isEmpty()) {
        Token token = tokens.remove(0);

        if(!token.getText().equals(",")) {
          throw new SyntaxException("Operator expected", token);
        }
      }
    }

    return results;
  }

  static final class TypedValue {
    final Class<?> type;
    final Object value;

    TypedValue(Class<?> type, Object value) {
      this.type = type;
      this.value = value;

      if(value instanceof TypedValue) {
        throw new RuntimeException("cannot nest TypedValue objects");
      }
    }

    TypedValue(Object value) {
      if(value == null) {
        throw new RuntimeException("cannot determine type of null");
      }
      if(value instanceof TypedValue) {
        throw new RuntimeException("cannot nest TypedValue objects");
      }

      this.type = value.getClass();
      this.value = value;
    }

    @Override
    public String toString() {
      return "TypedValue(" + type + "=" + value + ")";
    }
  }

  static TypedValue parse(TypedValue root, List<Token> tokens, int level, boolean parseOnly) throws SyntaxException {
    if(tokens.isEmpty()) {
      throw new SyntaxException("Expression expected");
    }

    TypedValue result = null;
    boolean operatorRequired = false;

    while(!tokens.isEmpty()) {
      Token token = tokens.get(0);
      String text = token.getText();
      Operator operator = OPERATORS.get(text);
//      System.out.println("Getting operator for " + token + " -> " + operator + "; parseOnly=" + parseOnly + "; operatorRequired=" + operatorRequired + ";parentOperator.level=" + level + " : result = " + result);

      if((operator == null && operatorRequired) || (operator != null && level <= operator.getLevel())) {
        break;
      }

      tokens.remove(0);

      if(operator != null) {
        try {
          result = operator.operate(result, root, tokens, parseOnly);
//          System.out.println("Result of operate " + operator + " was: " + result);
          operatorRequired = true;
        }
        catch(SyntaxException e) {
          if(e.getToken() == null) {
            e.setToken(token);
          }
          throw e;
        }
      }
      else {
        if(token.isLiteralResolved()) {
          result = token.getLiteral();
        }
        else if(token.getResolvedMethod() != null) {
          result = callMethod(root, root, tokens, false, token);
        }
        else {
          if(text.equals("null")) {
            result = new TypedValue(Object.class, null);
            token.setLiteral(result);
          }
          else if(token.matches(BOOLEAN)) {
            result = new TypedValue(Boolean.parseBoolean(text));
            token.setLiteral(result);
          }
          else if(token.matches(INTEGER)) {
            result = new TypedValue(Integer.parseInt(text));
            token.setLiteral(result);
          }
          else if(token.matches(FLOAT)) {
            result = new TypedValue(Double.parseDouble(text));
            token.setLiteral(result);
          }
          else if(token.matches(IDENTIFIER) && !text.equals("contains")) {
            result = callMethod(root, root, tokens, false, token);
          }
          else if(text.startsWith("\"")) {
            if(!text.endsWith("\"")) {
              throw new SyntaxException("Unclosed string", token);
            }

            result = new TypedValue(text.substring(1, text.length() - 1).replaceAll("\\\\\"", "\""));
            token.setLiteral(result);
          }
          else {
            throw new SyntaxException("Expression expected", token);
          }
        }

        operatorRequired = true;
      }
    }

//    System.out.println("Leaving level " + level + " with " + result);

    return result;
  }

  private static TypedValue callMethod(TypedValue root, TypedValue parent, List<Token> tokens, boolean parseOnly, Token token) throws SyntaxException {
    List<TypedValue> parameters = null;

    try {
      if(tokens.size() > 0 && tokens.get(0).getText().equals("(")) {
        parameters = parseList(root, tokens.subList(1, findMatchingParenthesis(tokens, 0)), parseOnly);
        tokens.remove(0);
        tokens.remove(0);
      }
      else {
        parameters = Collections.emptyList();
      }

      Method method = token.getResolvedMethod();

      if(method == null) {
        method = findMethod(parent.type, token.getText(), parameters);
        token.setResolvedMethod(method);
      }

      if(parseOnly) {
        return new TypedValue(method.getReturnType(), null);
      }

      if(parent.value == null) {
        return new TypedValue(parent.type, null);
      }

      Object[] parameterValues = new Object[parameters.size()];

      for(int i = 0; i < parameters.size(); i++) {
        TypedValue typedValue = parameters.get(i);

        parameterValues[i] = typedValue.value;
      }

      MethodCall methodCall = createMethodCall(method, parameterValues);

      try {
        // TODO This calls the method, even in parseOnly mode as we need somekind of "result" to return that can be identified by its Class (so null wouldn't do) -- this is actually not a good situation as it means that later method selection can change at runtime depending on the return value type of this call...
        return new TypedValue(method.getReturnType(), methodCall.invoke(parent.value));
      }
      catch(IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
        throw new RuntimeException("Could not call " + methodCall.method + " with instance [" + parent + "] and parameters " + Arrays.toString(methodCall.parms), e);
      }
    }
    catch(NoSuchMethodException e) {
      throw new SyntaxException("Unknown member of " + parent + ": " + token.getText() + "(" + parameters + ")", token, e);
    }
  }

  private static int findMatchingParenthesis(List<Token> tokens, int openCount) throws SyntaxException {
    // find matching bracket
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

  static class MethodCall {
    private final Method method;
    private final Object[] parms;

    MethodCall(Method method, Object... parms) {
      this.method = method;
      this.parms = parms;
    }

    Object invoke(Object obj) throws IllegalAccessException, IllegalArgumentException, InvocationTargetException {
      return method.invoke(obj, parms);
    }
  }

  private static Method findMethod(Class<?> rootClass, String propertyName, List<TypedValue> parameters) throws NoSuchMethodException {
    try {
      Method method;

      Class<?>[] parameterClasses = new Class<?>[parameters.size()];

      for(int i = 0; i < parameters.size(); i++) {
        TypedValue typedValue = parameters.get(i);

        parameterClasses[i] = typedValue.type;
      }

      method = searchForMethod(rootClass, propertyName, parameterClasses);

      if(method == null) {
        method = searchForMethod(rootClass, "get" + propertyName.substring(0, 1).toUpperCase() + propertyName.substring(1), parameterClasses);

        if(method == null) {
          method = searchForMethod(rootClass, "is" + propertyName.substring(0, 1).toUpperCase() + propertyName.substring(1), parameterClasses);

          if(method != null && method.getReturnType() != Boolean.class && method.getReturnType() != boolean.class) {
            method = null;
          }
        }
      }

      if(method == null) {
        throw new NoSuchMethodException(propertyName);
      }

      return method;
    }
    catch(SecurityException e) {
      throw new RuntimeException(e);
    }
  }

  private static MethodCall createMethodCall(Method method, Object... parameters) {
    Class<?>[] types = method.getParameterTypes();

    if(method.isVarArgs()) {
      parameters = transformParametersForVarArgs(types.length, types[types.length - 1].getComponentType(), parameters);
    }

    return new MethodCall(method, InspectionUtils.convertSourcesToBeCompatible(types, parameters));
  }

  /**
   * Returns all super types in most specific to least specific order.
   */
  private static List<Class<?>> getAllSuperTypes(Class<?> type) {
    List<Class<?>> superTypes = new ArrayList<>();
    List<Class<?>> todoList = new ArrayList<>();

    todoList.add(type);

    while(!todoList.isEmpty()) {
      Class<?> currentType = todoList.remove(0);

      if(currentType.getSuperclass() != null) {
        todoList.add(currentType.getSuperclass());
      }

      for(Class<?> iface : currentType.getInterfaces()) {
        todoList.add(iface);
      }

      superTypes.add(currentType);
    }

    return superTypes;
  }

  private static Method searchForMethod(Class<?> baseType, String name, Class<?>... parameterClasses) {
    List<Class<?>> allSuperTypes = getAllSuperTypes(baseType);
    Collections.reverse(allSuperTypes);

    for(Class<?> type : allSuperTypes) {
      Method[] methods = type.getDeclaredMethods();

      for(int i = 0; i < methods.length; i++) {
        // Has to be named the same of course.
        if(!methods[i].getName().equals(name)) {
          continue;
        }

        Class<?>[] types = methods[i].getParameterTypes();
        Class<?>[] newParameterClasses = parameterClasses;

        if(methods[i].isVarArgs() && willParametersMatchVarArgs(types.length, types[types.length - 1].getComponentType(), newParameterClasses)) {
          newParameterClasses = Arrays.copyOf(newParameterClasses, types.length);
          newParameterClasses[types.length - 1] = Array.newInstance(types[types.length - 1].getComponentType(), 0).getClass();
        }

        // Does it have the same number of arguments that we're looking for.
        if(types.length != newParameterClasses.length) {
          continue;
        }

        // Check for type compatibility
        if(InspectionUtils.areTypesAlmostCompatible(types, newParameterClasses)) {
          return methods[i];
  //        return new MethodCall(methods[i], InspectionUtils.convertSourcesToBeCompatible(types, parms));
        }
      }
    }

    return null;
  }

  private static Object[] transformParametersForVarArgs(int totalParameters, Class<?> varArgType, Object... parms) {
    if(totalParameters > parms.length + 1) {
      // Will never match, just return original
      return parms;
    }

    Object[] newParms = Arrays.copyOf(parms, totalParameters);
    Object[] varArgsArray = (Object[])Array.newInstance(varArgType, parms.length + 1 - totalParameters);

    newParms[totalParameters - 1] = varArgsArray;

    // Check if all parameters that must be part of the varargs array are compatible:
    for(int i = totalParameters - 1; i < parms.length; i++) {
      try {
        varArgsArray[i - totalParameters + 1] = InspectionUtils.convertToType(varArgType, parms[i]);
      }
      catch(IllegalArgumentException e) {
        // Unable to make all types match, return original array
        return parms;
      }
    }

    return newParms;
  }

  private static boolean willParametersMatchVarArgs(int totalParameters, Class<?> varArgType, Class<?>... parameterClasses) {
    if(totalParameters > parameterClasses.length + 1) {
      // Will never match, just return original
      return false;
    }

    // Check if all parameters that must be part of the varargs array are compatible:
    for(int i = totalParameters - 1; i < parameterClasses.length; i++) {
      if(!InspectionUtils.isTypeAlmostCompatible(varArgType, parameterClasses[i])) {
        return false;
      }
    }

    return true;
  }
}
