package autoeq.commandline;

import junit.framework.Assert;

import org.junit.Before;
import org.junit.Test;

public class CommandLineParserTest {
  private TestConf conf;

  @Before
  public void before() {
    conf = new TestConf();
  }

  @Test
  public void shouldHaveCorrectHelpString() {
    Assert.assertEquals("[range <number>] [rangefactor <fraction>] [profile <text>] [off|nearest|smart] [target off|nearest|smart]", CommandLineParser.getHelpString(TestConf.class));
  }

  @Test
  public void shouldParseString() {
    CommandLineParser.parse(conf, "profile burn");

    Assert.assertEquals("burn", conf.profile);
  }

  @Test
  public void shouldParseQuotedString() {
    CommandLineParser.parse(conf, "profile \"burn 2\"");

    Assert.assertEquals("burn 2", conf.profile);
  }

  @Test
  public void shouldParseEscapedString() {
    CommandLineParser.parse(conf, "profile burn\\ 2");

    Assert.assertEquals("burn 2", conf.profile);
  }

  @Test
  public void shouldParseSingleQuotedString() {
    CommandLineParser.parse(conf, "profile 'burn 2'");

    Assert.assertEquals("burn 2", conf.profile);
  }

  @Test
  public void shouldParseStringWithEscapedQuotes() {
    CommandLineParser.parse(conf, "profile 'burn \\\"2\\\"'");

    Assert.assertEquals("burn \"2\"", conf.profile);
  }

  @Test
  public void shouldParseStringMultipleEscapes() {
    CommandLineParser.parse(conf, "profile burn\\ \\\"2\\\"");

    Assert.assertEquals("burn \"2\"", conf.profile);
  }

  @Test
  public void shouldParseEnum() {
    CommandLineParser.parse(conf, "target smart");

    Assert.assertEquals(TargetType.SMART, conf.target);
  }

  @Test
  public void shouldParseDefaultParameter() {
    CommandLineParser.parse(conf, "nearest");

    Assert.assertEquals(TargetType.NEAREST, conf.defaultTarget);
  }


//  @Test
//  public void shouldParsePrimitiveBoolean() {
//    TestConf conf = new TestConf();
//
//    CommandLineParser.parse(conf, "");
//
//    Assert.assertEquals(200, conf.range);
//  }

  @Test
  public void shouldParsePrimitiveInteger() {
    CommandLineParser.parse(conf, "range 200");

    Assert.assertEquals(200, conf.range);
  }

  @Test
  public void shouldParsePrimitiveDouble() {
    CommandLineParser.parse(conf, "rangefactor 0.8");

    Assert.assertEquals(0.8, conf.rangefactor, 0.00001);
  }

  @Test
  public void shouldParseCommandLine1() {
    CommandLineParser.parse(conf, "range 200 nearest");

    Assert.assertEquals(200, conf.range);
    Assert.assertEquals(TargetType.NEAREST, conf.defaultTarget);
  }

  public static class TestConf {
    @Parameter
    private int range;

    @Parameter
    private double rangefactor;

    @Parameter
    private String profile;

    @Parameter(defaultParameter = true)
    private TargetType defaultTarget;

    @Parameter
    private TargetType target;
  }

  public enum TargetType {
    OFF, NEAREST, SMART
  }
}
