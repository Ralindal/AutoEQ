package autoeq.eq;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import autoeq.Attribute;
import autoeq.effects.Effect;
import autoeq.modules.scan.ScanModule;

public class CastResultMonitor {
  private static final Map<MessageMatcher, ResultFactory> MESSAGE_MATCHERS = new HashMap<>();

  static class Event {
    final MessageMatcher messageMatcher;
    final String line;
    final long time;

    Event(MessageMatcher messageMatcher, String line) {
      this.messageMatcher = messageMatcher;
      this.line = line;
      this.time = System.currentTimeMillis();
    }

    public CastResult determineCastResult(SpellCast spellCast) {
      if(messageMatcher.matches(spellCast, line, time)) {
        return MESSAGE_MATCHERS.get(messageMatcher).determineResult(spellCast);
      }

      return null;
    }

    @Override
    public String toString() {
      return "[t=" + (time % 100000L) + ";\"" + line + "\"]";
    }
  }

  private final ConcurrentLinkedDeque<Event> events = new ConcurrentLinkedDeque<>();
  private final ConcurrentLinkedDeque<SpellCast> spellCasts = new ConcurrentLinkedDeque<>();

  public enum ResponseTime {
    BEFORE_CAST_START(Long.MAX_VALUE, 0, 0),   // creation - startcast
    INSTANT(250, 0, 0),                        // creation - 250
    WITHIN_RECAST(1500, 0, 0),                 // creation - 1500
    DURING_CASTING(0, Long.MAX_VALUE, 0),      //            startcast - endcast
    AFTER_CAST_START(0, Long.MAX_VALUE, 500),  //            startcast - endcast - 500
    AFTER_CAST(0, 0, 500),                     //                        endcast - 500 (with 100ms of leeway for endcast)
    ANY(Long.MAX_VALUE, Long.MAX_VALUE, 500);  // creation - startcast - endcast - 500

    private final long millisAfterCreation;
    private final long millisAfterStart;
    private final long millisAfterFinished;

    ResponseTime(long millisAfterCreation, long millisAfterStart, long millisAfterFinished) {
      this.millisAfterCreation = millisAfterCreation;
      this.millisAfterStart = millisAfterStart;
      this.millisAfterFinished = millisAfterFinished;
    }

    public boolean matches(SpellCast spellCast, long time) {
      if(millisAfterCreation != 0 && time - spellCast.creationTime <= millisAfterCreation && (spellCast.finishedCastingTime == null || time < spellCast.finishedCastingTime)) {
        return true;
      }
      if(millisAfterStart != 0 && spellCast.startCastTime != null) {
        long timeAfterStartTime = time - spellCast.startCastTime;

        if(timeAfterStartTime >= 0 && timeAfterStartTime <= millisAfterStart) {
          return true;
        }
      }
      if(millisAfterFinished != 0 && spellCast.finishedCastingTime != null) {
        long timeAfterCastingTime = time - spellCast.finishedCastingTime;

        if(timeAfterCastingTime > -500 && (spellCast.startCastTime == null || time >= spellCast.startCastTime) && timeAfterCastingTime <= millisAfterFinished) {
          return true;
        }
      }

      return false;
    }
  }

  interface MessageMatcher {
    boolean quickMatch(String line);
    boolean matches(SpellCast spellCast, String line, long time);
    ResponseTime getResponseTime();
  }

  static class ExactMessageMatcher implements MessageMatcher {
    private final String exactMessage;
    private final ResponseTime responseTime;

    ExactMessageMatcher(String exactMessage, ResponseTime responseTime) {
      this.exactMessage = exactMessage;
      this.responseTime = responseTime;
    }

    @Override
    public boolean quickMatch(String line) {
      return line.equals(exactMessage);
    }

    @Override
    public boolean matches(SpellCast spellCast, String line, long time) {
      if(responseTime.matches(spellCast, time)) {
        if(line.equals(exactMessage)) {
          return true;
        }
      }

      return false;
    }

    @Override
    public ResponseTime getResponseTime() {
      return responseTime;
    }
  }

  static class StartMessageMatcher implements MessageMatcher {
    private final String startOfMessage;
    private final ResponseTime responseTime;

    StartMessageMatcher(String startOfMessage, ResponseTime responseTime) {
      this.startOfMessage = startOfMessage;
      this.responseTime = responseTime;
    }

    @Override
    public boolean quickMatch(String line) {
      return line.startsWith(startOfMessage);
    }

    @Override
    public boolean matches(SpellCast spellCast, String line, long time) {
      if(responseTime.matches(spellCast, time)) {
        if(line.startsWith(startOfMessage)) {
          return true;
        }
      }

      return false;
    }

    @Override
    public ResponseTime getResponseTime() {
      return responseTime;
    }
  }

  static class SpellMessageMatcher implements MessageMatcher {
    private final String exactMessage;
    private final String startOfMessage;
    private final ResponseTime responseTime;

    SpellMessageMatcher(String exactMessage, ResponseTime responseTime) {
      this.exactMessage = exactMessage;
      this.responseTime = responseTime;

      this.startOfMessage = exactMessage.substring(0, exactMessage.indexOf("{"));
    }

    @Override
    public boolean quickMatch(String line) {
      return line.startsWith(startOfMessage);
    }

    @Override
    public boolean matches(SpellCast spellCast, String line, long time) {
      if(responseTime.matches(spellCast, time)) {
        if(line.equals(exactMessage.replaceAll("\\{SPELL_NAME\\}", spellCast.effect.getSpell().getName()))) {
          return true;
        }
      }

      return false;
    }

    @Override
    public ResponseTime getResponseTime() {
      return responseTime;
    }
  }

  interface ResultFactory {
    CastResult determineResult(SpellCast spellCast);
  }

  static class SimpleResultFactory implements ResultFactory {
    private final CastResult castResult;
    private final CastResult castResultDetrimental;

    SimpleResultFactory(CastResult castResult, CastResult castResultDetrimental) {
      this.castResult = castResult;
      this.castResultDetrimental = castResultDetrimental;
    }

    SimpleResultFactory(CastResult castResult) {
      this(castResult, null);
    }

    @Override
    public CastResult determineResult(SpellCast spellCast) {
      return castResultDetrimental != null && spellCast.effect.getSpell().isDetrimental() ? castResultDetrimental : castResult;
    }
  }

  public enum CastResult {
    FINISH_MARKER("CAST_FINISHED", -1),
    SUCCESS("CAST_SUCCESS", 0),
    RECOVERING("CAST_RECOVER", 1),
    INTERRUPTED("CAST_INTERRUPTED", 2),
    CANNOT_SEE_TARGET("CAST_CANNOTSEE", 2),
    DISTRACTED("CAST_DISTRACTED", 3),
    TRY_LATER("CAST_TRY_LATER", 4),
    FIZZLED("CAST_FIZZLE", 5),
    MISSING_COMPONENTS("CAST_COMPONENTS", 5),
    MISSING_TARGET("CAST_NOTARGET", 5),
    NOT_READY("CAST_NOTREADY", 5),
    INSUFFICIENT_MANA("CAST_OUTOFMANA", 5),
    OUT_OF_RANGE("CAST_OUTOFRANGE", 5),
    RESISTED("CAST_RESIST", 5),
    DID_NOT_TAKE_HOLD("CAST_TAKEHOLD", 5),
    SITTING("CAST_STANDING", 5),
    STUNNED("CAST_STUNNED", 5),
    IMMUNE_INSTANT("CAST_IMMUNE", 6),
    IMMUNE("CAST_IMMUNE", 6),
    NOT_ATTUNED("CAST_NOT_ATTUNED", 6),

    GUARANTEED_SUCCESS("CAST_SUCCESS", 9),

    SELF_RESIST(RESISTED);  // Cancels out a resist

    private final int rootCauseLevel;
    private final String code;
    private final CastResult cancelsOut;

    CastResult(String code, int rootCauseLevel) {
      this.code = code;
      this.rootCauseLevel = rootCauseLevel;
      this.cancelsOut = null;
    }

    CastResult(CastResult cancelsOut) {
      this.cancelsOut = cancelsOut;
      this.code = "CAST_SUCCESS";
      this.rootCauseLevel = 0;
    }

    public String getCode() {
      return code;
    }

    public int getRootCauseLevel() {
      return rootCauseLevel;
    }

    public CastResult getCancelsOut() {
      return cancelsOut;
    }
  }

  // TODO a spell can have multiple finish markers...
  static {
    MESSAGE_MATCHERS.put(new MessageMatcher() {
      @Override
      public boolean quickMatch(String line) {
        return line.startsWith("Your ") && line.endsWith(" flickers with a pale light.");
      }

      @Override
      public boolean matches(SpellCast spellCast, String line, long time) {
        if(spellCast.startCastTime != null && spellCast.startCastTime <= time && time > spellCast.startCastTime + spellCast.effect.getCastTime() * 3 / 4) {
          return quickMatch(line);
        }

        return false;
      }

      @Override
      public ResponseTime getResponseTime() {
        return ResponseTime.AFTER_CAST;
      }
    }, new SimpleResultFactory(CastResult.FINISH_MARKER));

    MESSAGE_MATCHERS.put(new ExactMessageMatcher("You must be standing to cast a spell.", ResponseTime.INSTANT), new SimpleResultFactory(CastResult.SITTING));
    MESSAGE_MATCHERS.put(new ExactMessageMatcher("You must first select a target for this spell!", ResponseTime.INSTANT), new SimpleResultFactory(CastResult.MISSING_TARGET));
    MESSAGE_MATCHERS.put(new ExactMessageMatcher("You haven't recovered yet...", ResponseTime.INSTANT), new SimpleResultFactory(CastResult.RECOVERING));
    MESSAGE_MATCHERS.put(new ExactMessageMatcher("You cannot see your target.", ResponseTime.INSTANT), new SimpleResultFactory(CastResult.CANNOT_SEE_TARGET));
    MESSAGE_MATCHERS.put(new ExactMessageMatcher("This spell only works on the cursed.", ResponseTime.INSTANT), new SimpleResultFactory(CastResult.IMMUNE));
    MESSAGE_MATCHERS.put(new ExactMessageMatcher("This spell only works on the undead.", ResponseTime.INSTANT), new SimpleResultFactory(CastResult.IMMUNE));
    MESSAGE_MATCHERS.put(new ExactMessageMatcher("This spell only works on summoned beings.", ResponseTime.INSTANT), new SimpleResultFactory(CastResult.IMMUNE));
    MESSAGE_MATCHERS.put(new ExactMessageMatcher("You can't cast spells while invulnerable!", ResponseTime.INSTANT), new SimpleResultFactory(CastResult.DISTRACTED));
    MESSAGE_MATCHERS.put(new ExactMessageMatcher("Your target has no mana to affect", ResponseTime.INSTANT), new SimpleResultFactory(CastResult.IMMUNE));
    MESSAGE_MATCHERS.put(new ExactMessageMatcher("You can't cast spells while stunned!", ResponseTime.INSTANT), new SimpleResultFactory(CastResult.STUNNED));
    MESSAGE_MATCHERS.put(new ExactMessageMatcher("That spell cannot affect this target PC.", ResponseTime.INSTANT), new SimpleResultFactory(CastResult.IMMUNE));  // Cast TwinCastNuke on yourself
    MESSAGE_MATCHERS.put(new ExactMessageMatcher("You do not have sufficient focus to maintain that ability.", ResponseTime.BEFORE_CAST_START), new SimpleResultFactory(CastResult.IMMUNE));  // IMMUNE is wrong, but it tries again in 5 mins
    MESSAGE_MATCHERS.put(new ExactMessageMatcher("You are not sufficient level to use this item.", ResponseTime.BEFORE_CAST_START), new SimpleResultFactory(CastResult.DID_NOT_TAKE_HOLD));
    MESSAGE_MATCHERS.put(new ExactMessageMatcher("You cannot use this item unless it is attuned.", ResponseTime.INSTANT), new SimpleResultFactory(CastResult.NOT_ATTUNED));

    MESSAGE_MATCHERS.put(new StartMessageMatcher("You must first target a group member", ResponseTime.INSTANT), new SimpleResultFactory(CastResult.MISSING_TARGET));

    MESSAGE_MATCHERS.put(new ExactMessageMatcher("Your spell fizzles!", ResponseTime.WITHIN_RECAST), new SimpleResultFactory(CastResult.FIZZLED));

    // TODO needs testing for correct ResponseTime
    MESSAGE_MATCHERS.put(new ExactMessageMatcher("Your target does not meet the spell requirements.", ResponseTime.ANY), new SimpleResultFactory(CastResult.INTERRUPTED, CastResult.IMMUNE));  // For example, 11th hour -- but also undead slow on non-undead (instant then)

    MESSAGE_MATCHERS.put(new ExactMessageMatcher("You were unable to restore the corpse to life, but you may have success with a later attempt.", ResponseTime.AFTER_CAST), new SimpleResultFactory(CastResult.TRY_LATER));
    MESSAGE_MATCHERS.put(new ExactMessageMatcher("You are missing some required components.", ResponseTime.AFTER_CAST), new SimpleResultFactory(CastResult.MISSING_COMPONENTS));
    MESSAGE_MATCHERS.put(new ExactMessageMatcher("Your spell is interrupted.", ResponseTime.AFTER_CAST_START), new SimpleResultFactory(CastResult.INTERRUPTED));

    MESSAGE_MATCHERS.put(new ExactMessageMatcher("Your spell was partially successful.", ResponseTime.AFTER_CAST), new SimpleResultFactory(CastResult.GUARANTEED_SUCCESS));
    MESSAGE_MATCHERS.put(new ExactMessageMatcher("Your target is out of range, get closer!", ResponseTime.ANY), new SimpleResultFactory(CastResult.OUT_OF_RANGE));

    MESSAGE_MATCHERS.put(new ExactMessageMatcher("Your spell is too powerful for your intended target.", ResponseTime.BEFORE_CAST_START), new SimpleResultFactory(CastResult.DID_NOT_TAKE_HOLD));  // When trying to buff someone of too low level
    MESSAGE_MATCHERS.put(new ExactMessageMatcher("Your target is immune to changes in its run speed.", ResponseTime.AFTER_CAST), new SimpleResultFactory(CastResult.IMMUNE));
    MESSAGE_MATCHERS.put(new ExactMessageMatcher("This NPC cannot be charmed.", ResponseTime.AFTER_CAST), new SimpleResultFactory(CastResult.IMMUNE));

    MESSAGE_MATCHERS.put(new ExactMessageMatcher("Your spell did not take hold.", ResponseTime.BEFORE_CAST_START), new SimpleResultFactory(CastResult.DID_NOT_TAKE_HOLD));  // When casting on unattackable NPC
    MESSAGE_MATCHERS.put(new ExactMessageMatcher("Your spell did not take hold.", ResponseTime.AFTER_CAST), new SimpleResultFactory(CastResult.DID_NOT_TAKE_HOLD));
    MESSAGE_MATCHERS.put(new ExactMessageMatcher("Your spell would not have taken hold on your target.", ResponseTime.AFTER_CAST), new SimpleResultFactory(CastResult.DID_NOT_TAKE_HOLD));  // When trying to cast lower buff when higher buff is present

    MESSAGE_MATCHERS.put(new ExactMessageMatcher("Your target cannot be mesmerized.", ResponseTime.AFTER_CAST), new SimpleResultFactory(CastResult.IMMUNE));  // Full Mez immunity
    MESSAGE_MATCHERS.put(new ExactMessageMatcher("Your target cannot be mesmerized (with this spell).", ResponseTime.AFTER_CAST), new SimpleResultFactory(CastResult.IMMUNE));  // Immune to this spell (level based)

    MESSAGE_MATCHERS.put(new SpellMessageMatcher("You resist the {SPELL_NAME} spell!", ResponseTime.AFTER_CAST), new SimpleResultFactory(CastResult.SELF_RESIST));   //"You resist the {1} spell!"
    MESSAGE_MATCHERS.put(new SpellMessageMatcher("Your target resisted the {SPELL_NAME} spell.", ResponseTime.AFTER_CAST), new SimpleResultFactory(CastResult.RESISTED));

    MESSAGE_MATCHERS.put(new ExactMessageMatcher("You *CANNOT* cast spells, you have been silenced!", ResponseTime.INSTANT), new SimpleResultFactory(CastResult.DISTRACTED)); // TODO is this the correct action?

    // Unverified:
    MESSAGE_MATCHERS.put(new StartMessageMatcher("Spell recast time not yet met", ResponseTime.ANY), new SimpleResultFactory(CastResult.NOT_READY));
    MESSAGE_MATCHERS.put(new StartMessageMatcher("Insufficient Mana to cast this spell", ResponseTime.ANY), new SimpleResultFactory(CastResult.INSUFFICIENT_MANA));
    MESSAGE_MATCHERS.put(new StartMessageMatcher("You need to be in a more open area to summon a mount", ResponseTime.ANY), new SimpleResultFactory(CastResult.DID_NOT_TAKE_HOLD));
    MESSAGE_MATCHERS.put(new StartMessageMatcher("You can only summon a mount on dry land", ResponseTime.ANY), new SimpleResultFactory(CastResult.DID_NOT_TAKE_HOLD));


    // Before actual spell starts casting (before "You begin casting" message):
    // Instant:
//    EXACT_MESSAGE_TO_CAST_RESULT.put("You must be standing to cast a spell.", CastResult.SITTING);
//    EXACT_MESSAGE_TO_CAST_RESULT.put("You must first select a target for this spell!", CastResult.MISSING_TARGET);
//    EXACT_MESSAGE_TO_CAST_RESULT.put("You haven't recovered yet...", CastResult.RECOVERING);
//    EXACT_MESSAGE_TO_CAST_RESULT.put("You cannot see your target.", CastResult.CANNOT_SEE_TARGET);
//    EXACT_MESSAGE_TO_CAST_RESULT.put("That spell can not affect this target PC.", CastResult.IMMUNE);
//    EXACT_MESSAGE_TO_CAST_RESULT.put("Your target has no mana to affect", CastResult.IMMUNE_INSTANT);
//    EXACT_MESSAGE_TO_CAST_RESULT.put("You can't cast spells while invulnerable!", CastResult.DISTRACTED);
//    EXACT_MESSAGE_TO_CAST_RESULT.put("This spell only works on the undead.", CastResult.IMMUNE);
//    EXACT_MESSAGE_TO_CAST_RESULT.put("This spell only works on summoned beings.", CastResult.IMMUNE);
//    MESSAGE_TO_CAST_RESULT.put("You must first target a group member", CastResult.MISSING_TARGET);
//
//    // Roundtrip:
//    EXACT_MESSAGE_TO_CAST_RESULT.put("Your target does not meet the spell requirements.", CastResult.INTERRUPTED);  // For example, 11th hour -- but also undead slow on non-undead (instant then)
//    EXACT_MESSAGE_TO_CAST_RESULT.put("Your spell fizzles!", CastResult.FIZZLED);
//
//    // Results after a full cast:
//    // --------------------------
//    EXACT_MESSAGE_TO_CAST_RESULT.put("You were unable to restore the corpse to life, but you may have success with a later attempt.", CastResult.TRY_LATER);
//    EXACT_MESSAGE_TO_CAST_RESULT.put("You are missing some required components.", CastResult.MISSING_COMPONENTS);
//    EXACT_MESSAGE_TO_CAST_RESULT.put("Your spell is interrupted.", CastResult.INTERRUPTED);
//    EXACT_MESSAGE_TO_CAST_RESULT.put("Your spell was partially successful.", CastResult.SUCCESS);
//
//    // Dunno
//    EXACT_MESSAGE_TO_CAST_RESULT.put("Your spell is too powerful for your intended target.", CastResult.DID_NOT_TAKE_HOLD);
//    EXACT_MESSAGE_TO_CAST_RESULT.put("Your target is immune to changes in its run speed.", CastResult.IMMUNE);
//    EXACT_MESSAGE_TO_CAST_RESULT.put("This NPC cannot be charmed.", CastResult.IMMUNE);
//
//    MESSAGE_TO_CAST_RESULT.put("You resist the {SPELL_NAME} spell!", CastResult.SELF_RESIST);   //"You resist the {1} spell!"
//    MESSAGE_TO_CAST_RESULT.put("Your target resisted the {SPELL_NAME} spell.", CastResult.RESISTED);
//
//    MESSAGE_TO_CAST_RESULT.put("You *CANNOT* cast spells, you have been silenced", CastResult.DISTRACTED);
//    MESSAGE_TO_CAST_RESULT.put("Your target cannot be mesmerized", CastResult.IMMUNE);
//    MESSAGE_TO_CAST_RESULT.put("This spell only works on ", CastResult.IMMUNE);
//    MESSAGE_TO_CAST_RESULT.put("Spell recast time not yet met", CastResult.NOT_READY);
//    MESSAGE_TO_CAST_RESULT.put("Insufficient Mana to cast this spell", CastResult.INSUFFICIENT_MANA);
//    MESSAGE_TO_CAST_RESULT.put("Your target is out of range, get closer!", CastResult.OUT_OF_RANGE);
//    MESSAGE_TO_CAST_RESULT.put("You can't cast spells while stunned", CastResult.STUNNED);
//    EXACT_MESSAGE_TO_CAST_RESULT.put("Your spell did not take hold.", CastResult.DID_NOT_TAKE_HOLD);
//    MESSAGE_TO_CAST_RESULT.put("Your spell would not have taken hold", CastResult.DID_NOT_TAKE_HOLD);
//    MESSAGE_TO_CAST_RESULT.put("You need to be in a more open area to summon a mount", CastResult.DID_NOT_TAKE_HOLD);
//    MESSAGE_TO_CAST_RESULT.put("You can only summon a mount on dry land", CastResult.DID_NOT_TAKE_HOLD);

//    aCastEvent(LIST289, CAST_COLLAPSE    ,"Your gate is too unstable, and collapses#*#");
//    aCastEvent(LIST289, CAST_COMPONENTS  ,"You need to play a#*#instrument for this song#*#");
//    aCastEvent(LIST289, CAST_DISTRACTED  ,"You are too distracted to cast a spell now#*#");
//    aCastEvent(LIST013, CAST_IMMUNE      ,"Your target is immune to changes in its attack speed#*#");
//    aCastEvent(LIST013, CAST_IMMUNE      ,"Your target is immune to changes in its run speed#*#");
//    aCastEvent(UNKNOWN, CAST_IMMUNE      ,"Your target looks unaffected#*#");
//    aCastEvent(UNKNOWN, CAST_INTERRUPTED ,"Your casting has been interrupted#*#");
//    aCastEvent(LIST289, CAST_FIZZLE      ,"You miss a note, bringing your song to a close#*#");
//    aCastEvent(LIST289, CAST_OUTDOORS    ,"This spell does not work here#*#");
//    aCastEvent(LIST289, CAST_OUTDOORS    ,"You can only cast this spell in the outdoors#*#");
//    aCastEvent(LIST289, CAST_OUTDOORS    ,"You can not summon a mount here#*#");
//    aCastEvent(LIST289, CAST_OUTDOORS    ,"You must have both the Horse Models and your current Luclin Character Model enabled to summon a mount#*#");
//
//    aCastEvent(LIST289, CAST_RECOVER     ,"Spell recovery time not yet met#*#");  // Does that still happen?
//    aCastEvent(LIST289, CAST_SUCCESS     ,"You are already on a mount#*#");
  }

  private final EverquestSession session;

  private SpellCast currentSpellCast;

  public CastResultMonitor(final EverquestSession session) {
    this.session = session;
    session.addChatListener(new ChatListener() {
      private final Pattern PATTERN = Pattern.compile(".+");

      @Override
      public Pattern getFilter() {
        return PATTERN;
      }

      @Override
      public void match(Matcher matcher) {
        String line = matcher.group(0);
        long currentTime = System.currentTimeMillis();

        for(MessageMatcher messageMatcher : MESSAGE_MATCHERS.keySet()) {
          if(messageMatcher.quickMatch(line)) {
            events.add(new Event(messageMatcher, line));

            /*
             * If the event is something that can only happen after a finished cast, then mark
             * the corresponding SpellCast as finished:
             */

            if(messageMatcher.getResponseTime() == ResponseTime.AFTER_CAST) {
              for(SpellCast spellCast : spellCasts) {
                if(messageMatcher.matches(spellCast, line, currentTime) && spellCast.finishedCastingTime == null) {
                  session.log("CRM: Marked " + spellCast.effect + " as finished."); // TODO rarely occurs, as the Cast Window detection is quicker (after cast messages require round trip)
                  spellCast.finishedCastingTime = currentTime;
                  break;
                }
              }
            }

            break;
          }
        }

        if(line.startsWith("You begin casting ")) {
          String spellName = line.substring(18, line.length() - 1);

          session.getMe().setLastCastStartMillis();

          for(SpellCast spellCast : spellCasts) {
            if(spellCast.effect.getSpell().getName().equals(spellName) && spellCast.creationTime < currentTime) {
              spellCast.startCastTime = currentTime;
              spellCast.finishedCastingTime = null;  // It happens that the system thinks the spell finished already (because it is slow to start the cast >400ms), in that case if we do detect a start cast, finishedCastingTime is cleared again
              return;
            }
          }

          session.log("CRM: Detected manual cast of: " + spellName);
        }
      }
    });
  }

  public void pulse() {
    long currentTime = System.currentTimeMillis();

    if(!session.getMe().isCasting()) {
      finishedCastingLastSpell();
    }

    /*
     * Walk through all known spell casts and see if any cast results can be determined.  It's
     * guaranteed that lines with spell results never occur before the spell was cast, but they
     * can occur after the next spell was cast.
     */

    for(Iterator<SpellCast> iterator = spellCasts.iterator(); iterator.hasNext();) {
      SpellCast spellCast = iterator.next();
      List<CastResult> matchingCastResults = new ArrayList<>();
      List<Event> matchingEvents = new ArrayList<>();

      for(Iterator<Event> eventIterator = events.iterator(); eventIterator.hasNext();) {
        Event event = eventIterator.next();
        CastResult castResult = event.determineCastResult(spellCast);

        if(castResult != null) {
          matchingCastResults.add(castResult);
          eventIterator.remove();
          matchingEvents.add(event);
        }
      }

      /*
       * Remove results that cancel each outer out:
       */

      retry:
      for(;;) {
        for(CastResult castResult : matchingCastResults) {
          if(castResult.getCancelsOut() != null) {
            matchingCastResults.remove(castResult.getCancelsOut());
            matchingCastResults.remove(castResult);
            continue retry;
          }
        }

        break;
      }

      /*
       * Remove immune cast results when it is a PBAE spell.  This addresses the problem with PBAE Stuns where
       * all mobs in the area of effect get marked as immune (for 5 minutes) when only one of them is immune.
       *
       * Also removes "did not take hold" results for similar reasons.
       */

      Spell spell = spellCast.effect.getSpell();

      if(spell != null && spell.isDetrimental() && (spell.getTargetType() == TargetType.PBAE || spell.getTargetType() == TargetType.BEAM)) {
        while(matchingCastResults.remove(CastResult.IMMUNE)) {
        }
        while(matchingCastResults.remove(CastResult.DID_NOT_TAKE_HOLD)) {
        }
      }

      /*
       * Determine final result:
       */

      CastResult finalCastResult = CastResult.SUCCESS;

      for(CastResult castResult : matchingCastResults) {
        if(castResult.getRootCauseLevel() > finalCastResult.getRootCauseLevel()) {
          finalCastResult = castResult;
        }
      }

      /*
       * If the result was not the default SUCCESS then process the result.  The default SUCCESS is processed
       * if after a certain time no result to the contrary was found.
       */

      if(finalCastResult != CastResult.SUCCESS || (spellCast.finishedCastingTime != null && currentTime > spellCast.finishedCastingTime + 500)) {
        processResult(spellCast, finalCastResult);
        iterator.remove();
      }
      else {
        events.addAll(matchingEvents);
      }
    }

    /*
     * Clean up events deque:
     */

    SpellCast spellCast = spellCasts.peekFirst();

    if(spellCast != null) {
      for(Iterator<Event> iterator = events.iterator(); iterator.hasNext();) {
        Event event = iterator.next();

        if(event.time < spellCast.creationTime) {
          iterator.remove();
        }
      }
    }
    else {
      if(!events.isEmpty()) {
        session.log(">>> Could not resolve events: " + events);
      }
      events.clear();
    }

//    SpellCast spellCast = spellCasts.peekFirst();
//
//    if(spellCast != null && spellCast.finishedCastingTime != null) {
//      CastResult castResult = getCastResult();
//
//      if(castResult != CastResult.SUCCESS
//          || spellCast.finishedCastingTime + 300 + (spellCast.effect.getCastTime() < 500 ? 500 - spellCast.effect.getCastTime() : 0) < currentTime
//          || (session.getMe().isBard() && !spellCast.effect.getSpell().isDetrimental())) {
//        spellCasts.removeFirst();
//        lastSeenCastResults.clear();
//
//        processResult(spellCast, castResult);
//      }
//    }
  }

  public void addCurrentCastingSpell(SpellLine spellLine, Effect effect, List<Spawn> targets) {
    finishedCastingLastSpell();

    currentSpellCast = new SpellCast(spellLine, effect, targets);
    spellCasts.add(currentSpellCast);

    updateSpellEffectManager(currentSpellCast, "CAST_ASSUMED_SUCCESS");
  }

  public void finishedCastingLastSpell() {
    if(currentSpellCast != null && currentSpellCast.finishedCastingTime == null) {
      currentSpellCast.finishedCastingTime = System.currentTimeMillis();
    }
  }

  private void processResult(SpellCast spellCast, CastResult result) {
    session.log(String.format("CRM: %s %s --> %s [%s]",
      spellCast.effect,
      spellCast.finishedCastingTime != null ? "in " + (spellCast.finishedCastingTime - spellCast.creationTime) + " ms" : "was interrupted",
      result,
      "tC=" + (spellCast.creationTime % 100000L) + (spellCast.startCastTime == null ? "" : ",tS=" + (spellCast.startCastTime % 100000L)) + (spellCast.finishedCastingTime == null ? "" : ",tF=" + (spellCast.finishedCastingTime % 100000L))
    ));

    String castResult = result.getCode();
    Effect effect = spellCast.effect;
    List<Spawn> targets = spellCast.targets;

    if(effect.getSpell() != null) {

      /*
       * Handles shrink
       */

      if(effect.getSpell().getRawSpellData().hasAttribute(Attribute.SHRINK)) {
        for(Spawn target : targets) {
          target.getSpellEffectManager(effect.getSpell()).addCastResult(spellCast, "CAST_SHRINK", 1.0);
        }
      }
      else {
        /*
         * Outputs informative message
         */

        if(effect.getSpell().getDuration() > 25) {
          String targetDescription = "";
          int maxTargets = 5;

          for(Spawn target : targets) {
            if(maxTargets-- == 0) {
              targetDescription += " and " + (targets.size() - 5) + " other targets";
              break;
            }

            if(!targetDescription.isEmpty()) {
              targetDescription += ", ";
            }
            targetDescription += target.getName();
          }

          session.echo("CRM: " + castResult + ": " + effect.getSpell() + " for " + effect.getSpell().getDuration() + "s to " + targetDescription);
        }

        if(effect.getSpell().isDetrimental() && effect.getSpell().getTargetType().isAreaOfEffect()) {
          ScanModule scanModule = (ScanModule)session.getModule("ScanModule");

          if(scanModule != null) {
            scanModule.reset();
          }
        }

        updateSpellEffectManager(spellCast, castResult);

        if(castResult.equals("CAST_SUCCESS") || castResult.equals("CAST_RESIST")) {
          if(effect.getSpell().getDuration() == 0 && !effect.getSpell().isDetrimental()) {
            // For instant spells which are succesful (usually heals or nukes) a complete spell casting lockout
            // is desired because there is some lag before the next health update.  0.25 seconds should be sufficient
            // without really disrupting casting (mainly healing).  The lock-out only affects the speed at which
            // AA/Clickeys + normal spells can be casted back to back.  Since spells have a 2.25 second lockout anyway
            // these won't be affected.
            session.setCastLockOut(250);
          }
        }
      }
    }
  }

  private static void updateSpellEffectManager(SpellCast spellCast, String castResult) {
    Effect effect = spellCast.effect;

    if(effect.getSpell() != null) {
      double durationExtensionFactor = spellCast.spellLine != null ? spellCast.spellLine.getDurationExtensionFactor() : 1.0;

      /*
       * The following raw spell data check handles the cast of spells being automatically cast, specifically
       * for Unity of the Spirits.
       */

      for(Spell autoCastedSpell : effect.getSpell().getAutoCastedSpells()) {
        for(Spawn target : spellCast.targets) {
          target.getSpellEffectManager(autoCastedSpell).addCastResult(spellCast, castResult, durationExtensionFactor);
        }
      }

  //        if(effect.getSpell().getTargetType() == TargetType.BEAM) {
  //          targets.get(0).getSpellEffectManager(effect.getSpell()).addCastResult(castResult);
  //        }
  //        else {
      for(Spawn target : spellCast.targets) {
        target.getSpellEffectManager(effect.getSpell()).addCastResult(spellCast, castResult, durationExtensionFactor);
      }
    }
  }

  static class SpellCast {
    final SpellLine spellLine;
    final Effect effect;
    final List<Spawn> targets;
    final long creationTime;

    Long startCastTime;
    Long finishedCastingTime;

    SpellCast(SpellLine spellLine, Effect effect, List<Spawn> targets) {
      this.spellLine = spellLine;
      this.effect = effect;
      this.targets = targets;
      this.creationTime = System.currentTimeMillis();
    }
  }
}
