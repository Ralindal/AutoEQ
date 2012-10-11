package autoeq;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import junit.framework.TestCase;

public class TokenizerTestCase extends TestCase {
  private static final Pattern PATTERN =
    Pattern.compile("([A-Za-z]+|-?[0-9]+(\\.[0-9]+)?|\"(\\\\.|[^\\\\\"])*\"|\\(|\\)|[^A-Za-z0-9() ]+)");

  // "(\\.|[^\\"])*"

  public void test() {
    assertEquals(Arrays.asList("1", "+", "1"), tokenize("1 + 1"));
    assertEquals(Arrays.asList("(", "1", "+", "1", ")"), tokenize(" ( 1 + 1 ) "));
    assertEquals(Arrays.asList("(", "1", "+", "1", ")"), tokenize("(1+1)"));
    assertEquals(Arrays.asList("\"(1+1)\""), tokenize("\"(1+1)\""));
    assertEquals(Arrays.asList("\"(1\\\"+1)\""), tokenize("\"(1\\\"+1)\""));
    assertEquals(Arrays.asList("\"apple\"", "+", "\"sauce\""), tokenize("\"apple\" + \"sauce\""));
  }

  private List<String> tokenize(String s) {
    Matcher matcher = PATTERN.matcher(s);
    List<String> tokens = new ArrayList<String>();

    while(matcher.find()) {
      tokens.add(matcher.group());
    }

    return tokens;
  }
}
