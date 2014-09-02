package autoeq.eq;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import autoeq.SpellData;
import autoeq.effects.Effect;
import autoeq.expr.Parser;
import autoeq.expr.SyntaxException;
import autoeq.ini.Section;
import autoeq.modules.buff.EffectSet;

public class SpellParser {

  public static List<ParsedEffectLine> parseSpells(final EverquestSession session, Section section, int defaultAgro) {
    List<EffectSet> effectSets = new ArrayList<>();

    List<String> spellNamePairs = section.getAll("Spell");
    final String order = section.get("SpellOrder");
    String filter = section.get("SpellFilter");
    int agro = Integer.parseInt(section.getDefault("Agro", "" + defaultAgro));

    for(String s : spellNamePairs) {
      Effect groupEffect = null;
      Effect singleEffect = null;

      for(String name : s.split("\\|")) {
        Effect effect = session.getEffect(name, agro);

        if(effect != null) {
          if(effect.getSpell() == null || !effect.getSpell().getTargetType().isAreaOfEffect()) {
            singleEffect = effect;
          }
          else {
            groupEffect = effect;
          }
        }
      }

      if(singleEffect != null || groupEffect != null) {
        effectSets.add(new EffectSet(singleEffect, groupEffect));
      }
    }

    if(filter != null) {
      try {
        for(Iterator<EffectSet> iterator = effectSets.iterator(); iterator.hasNext();) {
          EffectSet effectSet = iterator.next();

          ExpressionRoot root = new ExpressionRoot(session, null, null, null, effectSet.getSingleOrGroup());

          if((Boolean)Parser.parse(root, filter)) {
            iterator.remove();
          }
        }
      }
      catch(SyntaxException e) {
        System.err.println("Problem while filtering spells for Section: " + section.getName());
        e.printStackTrace();
      }
    }

    if(order != null) {
      System.err.println(">>> Sorting with: " + order);
      try {
        Collections.sort(effectSets, new Comparator<EffectSet>() {
          @Override
          public int compare(EffectSet o1, EffectSet o2) {
            ExpressionRoot root1 = new ExpressionRoot(session, null, null, null, o1.getSingleOrGroup());
            ExpressionRoot root2 = new ExpressionRoot(session, null, null, null, o2.getSingleOrGroup());

            try {
              Number number1 = (Number)Parser.parse(root1, order);
              Number number2 = (Number)Parser.parse(root2, order);

              return Double.compare(number1.doubleValue(), number2.doubleValue());
            }
            catch(SyntaxException e) {
              throw new RuntimeException(e);
            }
          }
        });
      }
      catch(RuntimeException e) {
        System.err.println("Problem while sorting spells for Section: " + section.getName());
        e.printStackTrace();
      }
      System.err.println(">>> Result: " + effectSets);
    }

    /*
     * Remove spells with same timerId's that have a recast time greater than their duration
     */

    Set<Integer> seenTimerIds = new HashSet<>();

    for(Iterator<EffectSet> iterator = effectSets.iterator(); iterator.hasNext();) {
      EffectSet effectSet = iterator.next();

      Spell spell = effectSet.getSingleOrGroup().getSpell();

      if(spell != null) {
        SpellData sd = spell.getRawSpellData();
        int timerId = sd.getTimerId();

        if(timerId > 0 && sd.getRecastMillis() > spell.getDuration() * 1000 && seenTimerIds.contains(timerId)) {
          System.err.println(">>> Filtered by timerId: " + effectSet);
          iterator.remove();
        }

        seenTimerIds.add(timerId);
      }
    }

    /*
     * Remove spells that are skipped
     */

    for(int i = 0; i < Integer.parseInt(section.getDefault("Skip", "0")); i++) {
      if(effectSets.size() > 0) {
        effectSets.remove(0);
      }
    }

    if(!effectSets.isEmpty()) {
      Effect effect = effectSets.get(0).getGroupOrSingle();
      int minimumTargets = effect.getSpell() != null && effect.getSpell().getTargetType().isAreaOfEffect() ? 2 : 1;

      String minimumTargetsConf = section.get("MinimumTargets");

      if(minimumTargetsConf != null) {
        minimumTargets = Integer.parseInt(minimumTargetsConf);
      }

      List<String> conditions = section.getAll("Condition");
      List<String> priorityAdjusts = section.getAll("PriorityAdjust");

      for(String key : section.getAllKeys()) {
        if(key.startsWith("${") && key.endsWith("}")) {
          for(int i = 0; i < conditions.size(); i++) {
            String condition = conditions.get(i);

            conditions.set(i, condition.replaceAll(Pattern.quote(key), section.get(key)));
          }

          for(int i = 0; i < priorityAdjusts.size(); i++) {
            String priorityAdjust = priorityAdjusts.get(i);

            priorityAdjusts.set(i, priorityAdjust.replaceAll(Pattern.quote(key), section.get(key)));
          }
        }
      }

      List<String> gems = section.getAll("Gem");

      if(gems.isEmpty()) {
        gems.add("0");
      }

      List<ParsedEffectLine> parsedEffectLines = new ArrayList<>();
      int counter = 1;

      ParsedEffectGroup group = new ParsedEffectGroup(
        gems,
        section.getDefault("ValidTargets", "war pal shd mnk rog ber rng bst brd clr shm dru enc mag nec wiz"),
        section.getAll("Profile"),
        conditions,
        priorityAdjusts,
        section.get("Priority"),
        TargetCategory.valueOf(section.getDefault("TargetType", section.getName().startsWith("Debuff.") ? "MAIN" : "ALL").toUpperCase()),
        minimumTargets,
        section.getAll("PostAction"),
        section.getDefault("Announce", "false").equals("true"),
        section.getDefault("AnnounceChannelPrefix", "/gsay"),
        section.get("RangeExtensionFactor") != null ? Double.parseDouble(section.get("RangeExtensionFactor")) : 1.0,
        section.get("DurationExtensionFactor") != null ? Double.parseDouble(section.get("DurationExtensionFactor")) : 1.0,
        section.get("AdditionalDurationExtension") != null ? Long.parseLong(section.get("AdditionalDurationExtension")) * 1000 : 0,
        section.get("GemSum")
      );

      if(gems.size() > 1) {
        for(;;) {
          parsedEffectLines.add(new ParsedEffectLine(
            group,
            section.getName() + (counter++ > 1 ? "#" + counter++ + "#" + effectSets.get(0).getSingleOrGroup() : ""),
            Integer.parseInt(gems.get(0)),
            effectSets
          ));

          effectSets.remove(0);

          if(effectSets.isEmpty()) {
            break;
          }
        }
      }
      else {
        parsedEffectLines.add(new ParsedEffectLine(
          group,
          section.getName(),
          Integer.parseInt(gems.get(0)),
          effectSets
        ));
      }

      return parsedEffectLines;
    }

    return Collections.emptyList();
  }

}

