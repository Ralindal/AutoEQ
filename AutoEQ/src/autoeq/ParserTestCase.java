package autoeq;

import java.util.Arrays;
import java.util.Collection;

import autoeq.expr.Parser;
import autoeq.expr.SyntaxException;

import junit.framework.TestCase;

public class ParserTestCase extends TestCase {
  private final Root root = new Root(new Spawn(), new Spawn());

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

    assertFalse((Boolean)Parser.parse(root, "target.mana < 20 && !target.inCombat && target.notInCombat"));
    assertFalse((Boolean)Parser.parse(root, "target.mana < 20 && !target.inCombat && !(target.names contains \"Testchar Deluxe\")"));
    assertFalse((Boolean)Parser.parse(root, "target.mana > 20 && !target.inCombat && !(target.names contains \"Testchar Deluxe\")"));
    assertFalse((Boolean)Parser.parse(root, "target.mana > 20 && !target.notInCombat && !(target.names contains \"Testchar Deluxe\")"));
    assertTrue((Boolean)Parser.parse(root, "target.mana > 20 && !target.notInCombat && !(target.names contains \"Testchar\")"));

    assertEquals(45.0, (double)(Double)Parser.parse(root, "2 + 6 / 2 + 8 * (3 + 2)"));

    assertTrue((Boolean)Parser.parse(root, "me.names contains \"Testchar Deluxe\""));
    assertFalse((Boolean)Parser.parse(root, "me.names contains \"Testchar\""));
    assertTrue((Boolean)Parser.parse(root, "me.names contains \"Testchar\" + \" \" + \"Deluxe\""));

    try {
      Parser.parse(root, "110 + ");
      fail();
    }
    catch(SyntaxException e) {
      assertTrue(e.getMessage().contains("Token expected"));
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
      Parser.parse(root, "1, / 2");
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
      assertTrue(e.getMessage().contains("Operator expected"));
    }

    assertEquals(5, Parser.parse(root, "abs(-5)"));
    assertEquals(15.0, Parser.parse(root, "multiply(2, 6) + 3"));
    assertEquals(8, Parser.parse(root, "multiply(2, divide(12, 3))"));
    assertEquals(36.0, Parser.parse(root, "multiply(2, 6) * 3"));
    assertEquals(36.0, Parser.parse(root, "target.multiply(2, 6) * 3"));
    assertEquals(42.0, Parser.parse(root, "(2 + multiply(2, 6)) * 3"));

    assertTrue((Boolean)Parser.parse(root, "target.mana == 2 * 50"));
    assertTrue((Boolean)Parser.parse(root, "target.mana == multiply(2, 30) + 40"));

    // Shortcut Evaluation tests:
    assertFalse((Boolean)Parser.parse(root, "target != null && (target.mana > 100 && target.mana < 200)"));
    assertFalse((Boolean)Parser.parse(root, "mainAssistTarget != null && mainAssistTarget.mana > 100"));
    assertFalse((Boolean)Parser.parse(root, "mainAssistTarget != null && mainAssistTarget.mana > 100 && mainAssistTarget.mana < 200"));
    assertFalse((Boolean)Parser.parse(root, "mainAssistTarget != null && (mainAssistTarget.mana > 100 && mainAssistTarget.mana < 200)"));

    // Parameter test
    assertFalse((Boolean)Parser.parse(root, "me.isSameSpawn(target)"));
    assertTrue((Boolean)Parser.parse(root, "me.isSameSpawn(me)"));
  }

  public static class Root {
    private final Spawn target;
    private final Spawn me;

    public Root(Spawn me, Spawn target) {
      this.me = me;
      this.target = target;
    }

    public Spawn me() {
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

  public static class Spawn {
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

    public int mana() {
      return 100;
    }

    public String name() {
      return "Firiona";
    }

    public Collection<String> names() {
      return Arrays.asList(new String[] {"Firiona", "Testchar Deluxe", "Dada Mala"});
    }

    public int multiply(int a, int b) {
      return a * b;
    }
  }
}
