package autoeq.eq;

import java.util.regex.Matcher;

public interface UserCommand {
  public void onCommand(Matcher matcher);
}
