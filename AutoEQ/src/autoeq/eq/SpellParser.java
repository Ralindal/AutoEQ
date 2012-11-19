package autoeq.eq;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;

import autoeq.effects.Effect;
import autoeq.expr.Parser;
import autoeq.expr.SyntaxException;
import autoeq.ini.Section;
import autoeq.modules.buff.EffectSet;

public class SpellParser {

  public static List<EffectSet> parseSpells(final EverquestSession session, Section section, int defaultAgro) {
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
          if(effect.getSpell().getTargetType() == TargetType.SINGLE) {
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
            System.err.println(">>> Filtered: " + effectSet);
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
      System.err.println(">>> Sorting: " + effectSets);
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

    for(int i = 0; i < Integer.parseInt(section.getDefault("Skip", "0")); i++) {
      if(effectSets.size() > 0) {
        effectSets.remove(0);
      }
    }

    return effectSets;
  }

}
