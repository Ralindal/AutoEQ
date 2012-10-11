package autoeq.eq;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public interface ChatListener {
  public Pattern getFilter();
  public void match(Matcher matcher);
}
