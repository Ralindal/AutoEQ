package autoeq;

import static org.junit.Assert.*;

import java.util.Arrays;
import java.util.Collection;

import org.junit.Ignore;
import org.junit.Test;

import autoeq.eq.VariableContext;
import autoeq.expr.Parser;
import autoeq.expr.SyntaxException;

public class ParserTestCase {
  private final Root root = new Root(new Me(), new Spawn());

  @Test
  public void shouldSupportEnum() throws SyntaxException {
    assertEquals(State.ALIVE, Parser.parse(root, "me.state"));
    assertTrue((Boolean)Parser.parse(root, "me.state == \"ALIVE\""));
    assertFalse((Boolean)Parser.parse(root, "me.state == \"DEAD\""));
    assertTrue((Boolean)Parser.parse(root, "me.inState(\"ALIVE\")"));
    assertFalse((Boolean)Parser.parse(root, "me.inState(\"DEAD\")"));
  }

  @Test
  public void shouldSupportFloatingPoint() throws SyntaxException {
    assertTrue((Boolean)Parser.parse(root, "10 * 0.5 > 4"));
    assertFalse((Boolean)Parser.parse(root, "10 * 0.5 < 4"));
    assertTrue((Boolean)Parser.parse(root, "0.2 * 9.9 > 1.9"));
  }

  @Test
  public void shouldSupportLiteralBooleans() throws SyntaxException {
    assertTrue((Boolean)Parser.parse(root, "true"));
    assertFalse((Boolean)Parser.parse(root, "false"));
    assertTrue((Boolean)Parser.parse(root, "2 == 3 || true"));
    assertFalse((Boolean)Parser.parse(root, "2 == 2 && false"));
  }


  @Test
  public void shouldSupportArrayArgument() throws SyntaxException {
    assertTrue((Boolean)Parser.parse(root, "me.isOfClass(\"BRD\", \"ENC\")"));
  }

  @Test
  public void shouldX() {
    System.out.println(">>> " + Integer.class.isAssignableFrom(Long.class));
    System.out.println(">>> " + Integer.class.isAssignableFrom(long.class));
    System.out.println(">>> " + Integer.class.isAssignableFrom(int.class));
    System.out.println(">>> " + Long.class.isAssignableFrom(Integer.class));
    System.out.println(">>> " + Long.class.isAssignableFrom(int.class));
    System.out.println(">>> " + Long.class.isAssignableFrom(long.class));
    System.out.println(">>> " + int.class.isAssignableFrom(Long.class));
    System.out.println(">>> " + int.class.isAssignableFrom(long.class));
    System.out.println(">>> " + int.class.isAssignableFrom(Integer.class));
    System.out.println(">>> " + long.class.isAssignableFrom(Integer.class));
    System.out.println(">>> " + long.class.isAssignableFrom(int.class));
    System.out.println(">>> " + long.class.isAssignableFrom(Long.class));
  }

  @Test
  public void shouldFindMethod() throws SyntaxException {
    Parser.parse(root, "me.context.setExpiringVariable(\"bla\", 1000)");
  }

  @Test
  public void shouldSupportConditionalOperator() throws SyntaxException {
    assertEquals(5, Parser.parse(null, "true ? 5 : 6"));
    assertEquals(6, Parser.parse(null, "false ? 5 : 6"));
    assertEquals(7, Parser.parse(null, "true ? 5 + 2 : 6 / 2"));
    assertEquals(3, Parser.parse(null, "false ? 5 + 2 : 6 / 2"));
    assertEquals(8, Parser.parse(null, "false ? 5 + 2 : true ? 5 + 3 : 6 / 2"));
    assertEquals(3, Parser.parse(null, "(2 == 1 + 1) ? 3 : 2"));
    assertEquals(3, Parser.parse(null, "(2 == 1 + 1) ? 3 : (2 == 1 + 2) ? 4 : 5"));
    assertEquals(4, Parser.parse(null, "(2 == 1 + 2) ? 3 : (2 == 1 + 1) ? 4 : 5"));
    assertEquals(5, Parser.parse(null, "(2 == 1 + 3) ? 3 : (2 == 1 + 2) ? 4 : 5"));
    assertEquals(3, Parser.parse(null, "(2 == 1 + 1) ? false ? 5 + 2 : 6 / 2 : 6 / 3"));
    assertEquals(5, Parser.parse(null, "true ? 5 : 6"));
    assertEquals(true, Parser.parse(null, "true ? true : false"));
    assertEquals(false, Parser.parse(null, "true ? false : true"));
    assertEquals(true, Parser.parse(null, "true ? 90 <= 85 || 60 <= 75 : false"));
    assertEquals(true, Parser.parse(null, "true ? (20 <= 85) || 60 <= 75 : false"));
    assertEquals(true, Parser.parse(null, "true ? (20 <= 85 && (true || false)) || 60 <= 75 : false"));
    assertEquals(14, Parser.parse(null, "true ? (5+2) * 2 : 6"));
    assertEquals(true, Parser.parse(null, "true ? (20 <= 85) || 60 <= 75 : false"));

    try {
      Parser.parse(null, "true ?");
      fail();
    }
    catch(SyntaxException e) {
      assertTrue(e.getMessage().contains("Expression expected"));
      assertEquals(5, e.getToken().getPosition());
    }

    try {
      Parser.parse(null, "true ? 2");
      fail();
    }
    catch(SyntaxException e) {
      assertTrue(e.getMessage().contains("':' expected"));
      assertEquals(5, e.getToken().getPosition());
    }

    try {
      Parser.parse(null, "true ? :");
      fail();
    }
    catch(SyntaxException e) {
      assertTrue(e.getMessage().contains("Expression expected"));
      assertEquals(7, e.getToken().getPosition());
    }

    try {
      Parser.parse(null, "true ? : 2");
      fail();
    }
    catch(SyntaxException e) {
      assertTrue("" + e, e.getMessage().contains("Expression expected"));
      assertEquals(7, e.getToken().getPosition());
    }

    try {
      Parser.parse(null, "true ? 2 :");
      fail();
    }
    catch(SyntaxException e) {
      assertTrue(e.getMessage().contains("Expression expected"));
      assertEquals(5, e.getToken().getPosition());
    }
  }

  @Test
  public void shouldNotAllowColonOutsideConditionalOperator() {
    try {
      Parser.parse(null, ":");
      fail();
    }
    catch(SyntaxException e) {
      assertTrue(e.getMessage().contains("Expression expected"));
      assertEquals(0, e.getToken().getPosition());
    }

    try {
      Parser.parse(null, "true : false");
      fail();
    }
    catch(SyntaxException e) {
      assertTrue(e.getMessage().contains("Operator expected"));
      assertEquals(5, e.getToken().getPosition());
    }

    try {
      Parser.parse(null, "2 / : 3");
      fail();
    }
    catch(SyntaxException e) {
      assertTrue(e.getMessage().contains("Expression expected"));
      assertEquals(4, e.getToken().getPosition());
    }

    try {
      assertEquals(true, Parser.parse(null, "2 + 3 : 2"));
      fail();
    }
    catch(SyntaxException e) {
      assertTrue(e.getMessage().contains("Operator expected"));
      assertEquals(6, e.getToken().getPosition());
    }
  }

  @Test(expected = SyntaxException.class)
  public void shouldNotAllowOperatorAfterCommaOperator() throws SyntaxException {
    try {
      Parser.parse(root, "1, / 2");
    }
    catch(SyntaxException e) {
      assertTrue(e.getMessage().contains("Variable or literal expected"));
      throw e;
    }
  }

  @Test
  public void shouldDoShortcutEvaluation() throws SyntaxException {
    assertFalse((Boolean)Parser.parse(root, "target.mana > 100"));
    assertFalse((Boolean)Parser.parse(root, "target.mana > 100 && target.mana < 200"));
    assertFalse((Boolean)Parser.parse(root, "target != null && (target.mana > 100 && target.mana < 200)"));
    assertFalse((Boolean)Parser.parse(root, "mainAssistTarget != null && mainAssistTarget.mana > 100"));
    assertFalse((Boolean)Parser.parse(root, "mainAssistTarget != null && mainAssistTarget.mana > 100 && mainAssistTarget.mana < 200"));
    assertFalse((Boolean)Parser.parse(root, "mainAssistTarget != null && (mainAssistTarget.mana > 100 && mainAssistTarget.mana < 200)"));
    assertFalse((Boolean)Parser.parse(root, "mainAssistTarget != null && mainAssistTarget.mana() > 100 ? 2 + \"a\" : false"));
  }

  @Test
  public void shouldSupportElvisOperator() throws SyntaxException {
    assertEquals(2, Parser.parse(null, "null ?: 2"));
    assertEquals(root.target, Parser.parse(root, "target ?: 2"));
    assertEquals(2, Parser.parse(root, "mainAssistTarget ?: 2"));
    assertEquals(2, Parser.parse(root, "mainAssistTarget.mana ?: 2"));
    assertEquals(2, Parser.parse(root, "mainAssistTarget.pet.mana ?: 2"));
    assertEquals(2, Parser.parse(root, "mainAssistTarget.pet.multiply(2 + 2, 4) ?: 2"));
    assertEquals(true, Parser.parse(root, "(mainAssistTarget.pet.multiply(2 + 2, 4) ?: 2) < 5"));
    assertEquals(false, Parser.parse(root, "(target.mana ?: 2) < 5"));

    try {
      Parser.parse(null, "null ?:");
      fail();
    }
    catch(SyntaxException e) {
      assertTrue(e.getMessage().contains("Expression expected"));
      assertEquals(5, e.getToken().getPosition());
    }
  }

  @Test
  @Ignore("not needed to support this, as currently member selection already safely returns null")
  public void shouldSupportNullSafeMemberSelectionOperator() throws SyntaxException {
    assertEquals(100, Parser.parse(root, "target?.mana"));
    assertEquals(null, Parser.parse(root, "mainAssistTarget?.mana"));

    try {
      Parser.parse(null, "null?.");
      fail();
    }
    catch(SyntaxException e) {
      assertTrue(e.getMessage().contains("Expression expected"));
      assertEquals(5, e.getToken().getPosition());
    }
  }

  @Test
  public void shouldDoSameLevelOperatorsInOrder() throws SyntaxException {
    assertEquals(20, Parser.parse(null, "4 / 2 * 10"));
    assertEquals(2, Parser.parse(null, "4 * 1000 / 2000"));
  }

  @Test
  public void testParse() throws SyntaxException {
    assertTrue((Boolean)Parser.parse(root, "0 == 1 || 1 == 1"));
    assertTrue((Boolean)Parser.parse(root, "target.mana < 95 || target.mana > 99"));
    assertTrue((Boolean)Parser.parse(root, "target.mana > 95 || target.mana < 90"));
    assertTrue((Boolean)Parser.parse(root, "target.mana > 95 && target.mana < 101"));
    assertTrue((Boolean)Parser.parse(root, "target.mana > 95 && (target.mana < 101 && target.mana < 200)"));
    assertFalse((Boolean)Parser.parse(root, "target.mana < 95 || target.mana > 101"));
    assertFalse((Boolean)Parser.parse(root, "target.mana > 95 && target.mana < 99"));
    assertFalse((Boolean)Parser.parse(root, "target.mana > 101 && target.mana < 99"));
    assertFalse((Boolean)Parser.parse(root, "target.mana > 101 && target.mana < 110"));
    assertTrue((Boolean)Parser.parse(root, "(0 == 1 || 1 == 1)"));
    assertFalse((Boolean)Parser.parse(root, "!(0 == 1 || 1 == 1)"));
    assertFalse((Boolean)Parser.parse(root, "0 == 1 && !(1 == 1)"));

    assertTrue((Boolean)Parser.parse(root, "target.inCombat"));
    assertFalse((Boolean)Parser.parse(root, "!target.inCombat"));
    assertFalse((Boolean)Parser.parse(root, "target.mana < 20 && !target.inCombat"));
    assertFalse((Boolean)Parser.parse(root, "target.mana < 20 && !target.inCombat && target.notInCombat"));
    assertFalse((Boolean)Parser.parse(root, "target.mana < 20 && !target.inCombat && !(target.names contains \"Testchar Deluxe\")"));
    assertFalse((Boolean)Parser.parse(root, "target.mana > 20 && !target.inCombat && !(target.names contains \"Testchar Deluxe\")"));
    assertFalse((Boolean)Parser.parse(root, "target.mana > 20 && !target.notInCombat && !(target.names contains \"Testchar Deluxe\")"));
    assertTrue((Boolean)Parser.parse(root, "target.mana > 20 && !target.notInCombat && !(target.names contains \"Testchar\")"));

    assertEquals(45, Parser.parse(root, "2 + 6 / 2 + 8 * (3 + 2)"));

    assertTrue((Boolean)Parser.parse(root, "me.names contains \"Testchar Deluxe\""));
    assertFalse((Boolean)Parser.parse(root, "me.names contains \"Testchar\""));
    assertTrue((Boolean)Parser.parse(root, "me.names contains \"Testchar\" + \" \" + \"Deluxe\""));

    try {
      Parser.parse(root, "110 + ");
      fail();
    }
    catch(SyntaxException e) {
      assertTrue(e.getMessage().contains("Expression expected"));
    }

    try {
      Parser.parse(root, "10 !(1 == 1)");
      fail();
    }
    catch(SyntaxException e) {
      assertTrue(e.getMessage().contains("Binary operator expected"));
    }

    try {
      Parser.parse(root, "null !(1 == 1)");
      fail();
    }
    catch(SyntaxException e) {
      assertTrue(e.getMessage().contains("Binary operator expected"));
    }

    try {
      Parser.parse(root, "/ 2");
      fail();
    }
    catch(SyntaxException e) {
      assertTrue(e.getMessage().contains("Variable or literal expected"));
    }

    try {
      Parser.parse(root, "10 11");
      fail();
    }
    catch(SyntaxException e) {
      assertTrue(e.getMessage().contains("Operator expected"));
    }

    try {
      Parser.parse(root, "10 (11 + 2)");
      fail();
    }
    catch(SyntaxException e) {
      assertTrue(e.getMessage().contains("Binary operator expected"));
    }

    assertEquals(5, Parser.parse(root, "abs(-5)"));
    assertEquals(15, Parser.parse(root, "multiply(2, 6) + 3"));
    assertEquals(8, Parser.parse(root, "multiply(2, divide(12, 3))"));
    assertEquals(36, Parser.parse(root, "multiply(2, 6) * 3"));
    assertEquals(36, Parser.parse(root, "target.multiply(2, 6) * 3"));
    assertEquals(42, Parser.parse(root, "(2 + multiply(2, 6)) * 3"));

    assertTrue((Boolean)Parser.parse(root, "target.mana == 2 * 50"));
    assertTrue((Boolean)Parser.parse(root, "target.mana == multiply(2, 30) + 40"));

    // Parameter test
    assertFalse((Boolean)Parser.parse(root, "me.isSameSpawn(target)"));
    assertTrue((Boolean)Parser.parse(root, "me.isSameSpawn(me)"));
    assertFalse((Boolean)Parser.parse(root, "target.isSameSpawn(me)"));
    assertEquals(0.0, Parser.parse(root, "target.distance"));
    assertEquals(10.0, Parser.parse(root, "target.distance(me)"));
    assertEquals(10.0, Parser.parse(root, "me.distance(target)"));
  }

  public enum State {
    ALIVE, DEAD
  }

  public static class Root {
    private final Spawn target;
    private final Me me;

    public Root(Me me, Spawn target) {
      this.me = me;
      this.target = target;
    }

    public Me me() {
      return me;
    }

    public Spawn target() {
      return target;
    }

    public int mana() {
      return 50;
    }

    public int multiply(int a, int b) {
      return a * b;
    }

    public int divide(int a, int b) {
      return a / b;
    }

    public int abs(int a) {
      return Math.abs(a);
    }

    public boolean self() {
      return true;
    }

    public Spawn mainAssistTarget() {
      return null;
    }
  }

  public static class Me extends Spawn {
    public State getState() {
      return State.ALIVE;
    }

    public boolean inState(State state) {
      return state == State.ALIVE ? true : false;
    }
  }

  public static class Spawn {
    private final VariableContext variableContext = new VariableContext();

    public VariableContext getContext() {
      return variableContext;
    }

    public boolean isMe() {
      return true;
    }

    public Spawn pet() {
      return null;
    }

    public boolean isSameSpawn(Spawn target) {
      return target.equals(this);
    }

    public boolean isSitting() {
      return false;
    }

    public boolean inCombat() {
      return true;
    }

    public boolean notInCombat() {
      return false;
    }

    public double getDistance() {
      return 0;
    }

    /**
     * @param spawn
     */
    public double getDistance(Spawn spawn) {
      return 10;
    }

    public int mana() {
      return 100;
    }

    public String name() {
      return "Firiona";
    }

    public boolean isOfClass(String... classes) {
      return Arrays.asList(classes).contains("ENC");
    }

    public Collection<String> names() {
      return Arrays.asList(new String[] {"Firiona", "Testchar Deluxe", "Dada Mala"});
    }

    public int multiply(int a, int b) {
      return a * b;
    }
  }
}
