package autoeq.modules.rezwait;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import autoeq.ThreadScoped;
import autoeq.eq.ChatListener;
import autoeq.eq.Command;
import autoeq.eq.EverquestSession;
import autoeq.eq.Me;
import autoeq.eq.Module;

import com.google.inject.Inject;


@ThreadScoped
public class RezWaitModule implements Module {
  private static final Pattern REZ_TEXT = Pattern.compile("([A-Za-z]+) wants to cast ([A-Za-z ]+) \\(([0-9]+).+");
  private static final Pattern CALL_TEXT = Pattern.compile("([A-Za-z]+) is attempting to return you to your.+");

  private final EverquestSession session;

  private long deathMillis;
//  private float lastExperience;
  private boolean death;

  @Inject
  public RezWaitModule(final EverquestSession session) {
    this.session = session;

    session.addChatListener(new ChatListener() {

      @Override
      public Pattern getFilter() {
        return Pattern.compile("(You regain some experience from resurrection\\.|You gained party experience!)");
      }

      @Override
      public void match(Matcher matcher) {
        session.echo("REZWAIT: Rezzed or gained experience, cancelling automatic camp-out.");
        death = false;
        deathMillis = 0;
      }
    });
  }

  @Override
  public int getBurstCount() {
    return 8;
  }

  @Override
  public List<Command> pulse() {
    Me me = session.getMe();

    if(!me.isAlive() && !death) {
      death = true;
      deathMillis = System.currentTimeMillis();
      session.echo("REZWAIT: We died.");
    }

    if(deathMillis != 0) {
      long waitedMillis = System.currentTimeMillis() - deathMillis;

      session.delay(500);

      // If dead more than an hour then camp out
      if(waitedMillis > 60 * 60 * 1000) {
        session.echo("REZWAIT: Waited for an hour, camping out.");
        session.doCommand("/camp desktop");
        session.delay(60 * 1000);
      }
      else if((waitedMillis / 1000) % 60 == 0) {
        session.echo("REZWAIT: " + (60 - (waitedMillis / 1000 / 60)) + " minutes left before camping out");
        session.delay(1000);
      }

      /*
       * Auto accept rez
       */

      if(session.evaluate("${Window[ConfirmationDialogBox].Open}")) {
        String rezText = session.translate("${Window[ConfirmationDialogBox].Child[CD_TextOutput].Text}");

        Matcher matcher = REZ_TEXT.matcher(rezText);
        Matcher callMatcher = CALL_TEXT.matcher(rezText);

        if((matcher.matches() && (session.getGroupMemberNames().contains(matcher.group(1)) || session.getBotNames().contains(matcher.group(1))) && Integer.parseInt(matcher.group(3)) >= 90) ||
            (callMatcher.matches() && (session.getGroupMemberNames().contains(callMatcher.group(1)) || session.getBotNames().contains(callMatcher.group(1))))) {
          session.doCommand("/nomodkey /notify ConfirmationDialogBox Yes_Button leftmouseup");
          session.delay(500);

          if(session.delay(5000, "${Window[RespawnWnd].Open}")) {
            String secondOptionName = session.translate("${Window[RespawnWnd].Child[RW_OptionsList].List[2,2]}");  // Resurrect
            int rezOption = secondOptionName.equalsIgnoreCase("Resurrect") ? 2 : 1;
              //session.getZoneId() == 213 ? 1 : 2;   // Exception for Plane of War

            session.doCommand("/nomodkey /notify RespawnWnd RW_OptionsList listselect " + rezOption);
            session.delay(500);
            session.doCommand("/nomodkey /notify RespawnWnd RW_SelectButton leftmouseup");
            session.delay(500);

            session.echo("REZWAIT: Accepted Rez, cancelling automatic camp-out.");
            death = false;
            deathMillis = 0;
          }
        }
        else {
          session.log("Ignoring rez: " + rezText);
          session.echo("Ignoring rez: " + rezText);
        }
      }

      // TODO Useless now because no corpses need looting, but it blocks other commands from processing.
      return new ArrayList<>(Arrays.asList(new Command[] {new LootCorpseCommand()}));
    }

    return null;
  }

//  public List<Command> pulse() {
//    Me me = session.getMe();
//    float currentExperience = me.getExperience() + me.getLevel();
//
//    if(lastExperience == 0) {
//      lastExperience = currentExperience;
//    }
//
//    float expDelta = currentExperience - lastExperience;
//    lastExperience = currentExperience;
//
//    if(expDelta < 0) {
//      if(deathMillis == 0) {
//        deathMillis = System.currentTimeMillis();
//      }
//    }
//    else if(expDelta > 0) {
//      if(deathMillis != 0) {
//        session.echo("REZWAIT: Rezzed or gained experience, cancelling automatic camp-out.");
//      }
//      deathMillis = 0;
//    }
//
//    if(deathMillis != 0) {
//      long waitedMillis = System.currentTimeMillis() - deathMillis;
//
//      // If dead more than an hour then camp out
//      if(waitedMillis > 60 * 60 * 1000) {
//        session.echo("REZWAIT: Waited for an hour, camping out.");
//        session.doCommand("/camp desktop");
//        session.delay(60 * 1000);
//      }
//      else if((waitedMillis / 1000) % 60 == 0) {
//        session.echo("REZWAIT: " + (60 - (waitedMillis / 1000 / 60)) + " minutes left before camping out");
//        session.delay(1000);
//      }
//
//      // TODO Use now because no corpses need looting, but it blocks other commands from processing.
//      return new ArrayList<Command>(Arrays.asList(new Command[] {new LootCorpseCommand()}));
//    }
//
//    return null;
//  }
}
