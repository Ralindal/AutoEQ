package autoeq.modules.invite;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import autoeq.ThreadScoped;
import autoeq.eq.ChatListener;
import autoeq.eq.Command;
import autoeq.eq.EverquestSession;
import autoeq.eq.Module;
import autoeq.ini.Section;

import com.google.inject.Inject;


@ThreadScoped
public class InviteModule implements Module, ChatListener {
  private static final Pattern INVITE_PATTERN = Pattern.compile("([A-Z][a-z]{3,}) tells you, '(.*)'");
  
  private final EverquestSession session;
  private List<String> allowedCharacters;

  @Inject
  public InviteModule(EverquestSession session) {
    this.session = session;
    
    session.addChatListener(this);
    
    Section section = session.getIni().getSection("Invite");
    
    if(section != null) {
      allowedCharacters = section.getAll("Name");
    }
  }
  
  public int getPriority() {
    return 9;
  }
  
  @Override
  public List<Command> pulse() {
    return null;
  }

  @Override
  public Pattern getFilter() {
    return INVITE_PATTERN;
  }

  @Override
  public void match(Matcher matcher) {
    String person = matcher.group(1);
    String text = matcher.group(2);
        
    if(text.toLowerCase().contains("invite")) {
      if(allowedCharacters != null && allowedCharacters.contains(person)) {
        session.echo("INVITE: Inviting " + person);
        session.doCommand("/invite " + person);
      }
    }
  }
  
  @Override
  public boolean isLowLatency() {
    return false;
  }
}
