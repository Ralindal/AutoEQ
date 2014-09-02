package autoeq.eq;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.when;

import java.io.FileReader;
import java.io.IOException;
import java.io.LineNumberReader;
import java.util.HashMap;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import autoeq.SpellData;
import autoeq.eq.Spawn.Source;

public class SpawnTest {
  private static Map<Integer, SpellData> rawSpellData = new HashMap<>();

  static {
    try(LineNumberReader reader = new LineNumberReader(new FileReader("spells_us.txt"))) {
      String line;

      while((line = reader.readLine()) != null) {
        String[] fields = line.split("\\^");
        SpellData sd = new SpellData(fields);
        rawSpellData.put(sd.getId(), sd);
      }
    }
    catch(IOException e) {
      throw new RuntimeException(e);
    }
  }

  @Mock
  public EverquestSession session;

  @Before
  public void before() {
    MockitoAnnotations.initMocks(this);
    final Map<Integer, Spell> spells = new HashMap<>();

    when(session.getRawSpellData(anyInt())).then(new Answer<SpellData>() {
      @Override
      public SpellData answer(InvocationOnMock invocation) throws Throwable {
        return rawSpellData.get((int)(invocation.getArguments()[0]));
      }
    });

    when(session.getSpell(anyInt())).then(new Answer<Spell>() {
      @Override
      public Spell answer(InvocationOnMock invocation) throws Throwable {
        int spellId = (int)(invocation.getArguments()[0]);
        Spell spell = spells.get(spellId);

        if(spell == null) {
          spell = new Spell(session, spellId);
          spells.put(spellId, spell);
        }

        return spell;
      }
    });

    when(session.translate(anyString())).thenReturn("99;4000;group v2;beneficial;4.0");
  }

  @Test
  public void willStackShouldAllowUnifiedCertitudeToStackWithSymbolOfGezat() {
    Spawn spawn = new Spawn(session, 1);

    assertTrue(spawn.willStack(session.getSpell(getSpellData("Unified Certitude").getId())));

    spawn.updateBuffsAndDurations("" + getSpellData("Symbol of Gezat Rk. II").getId() + ":60", true, Source.DIRECT);

    assertTrue(spawn.willStack(session.getSpell(getSpellData("Unified Certitude").getId())));

    spawn.updateBuffsAndDurations("" + getSpellData("Symbol of Gezat Rk. II").getId() + ":60 " + getSpellData("Ward of the Reverent Rk. II").getId() + ":60", true, Source.DIRECT);

    assertTrue(spawn.willStack(session.getSpell(getSpellData("Unified Certitude").getId())));
  }

  @Test
  public void willStackShouldTakeAutoCastedSpellsIntoAccountOfDifferentRanks() {
    Spawn spawn = new Spawn(session, 1);

    assertTrue(spawn.willStack(session.getSpell(getSpellData("Unity of Gezat").getId())));

    spawn.updateBuffsAndDurations("" + getSpellData("Symbol of Gezat").getId() + ":60", true, Source.DIRECT);

    assertTrue(spawn.willStack(session.getSpell(getSpellData("Unity of Gezat").getId())));

    spawn.updateBuffsAndDurations("" + getSpellData("Symbol of Gezat").getId() + ":60 " + getSpellData("Blessing of Fervor").getId() + ":60 " + getSpellData("Ward of the Reverent").getId() + ":60", true, Source.DIRECT);

    assertFalse(spawn.willStack(session.getSpell(getSpellData("Unity of Gezat").getId())));

    spawn.updateBuffsAndDurations("" + getSpellData("Symbol of Gezat").getId() + ":60 " + getSpellData("Blessing of Fervor").getId() + ":60 " + getSpellData("Ward of the Reverent Rk. III").getId() + ":60", true, Source.DIRECT);

    assertFalse(spawn.willStack(session.getSpell(getSpellData("Unity of Gezat").getId())));

    spawn.updateBuffsAndDurations("" + getSpellData("Symbol of Gezat").getId() + ":60 " + getSpellData("Blessing of Fervor").getId() + ":60 " + getSpellData("Blessing of Fervor Rk. II").getId() + " " + getSpellData("Ward of the Reverent Rk. III").getId() + ":60", true, Source.DIRECT);

    assertFalse(spawn.willStack(session.getSpell(getSpellData("Unity of Gezat").getId())));
  }

  @Test
  public void willStackShouldAllowConfoundToStackWithPerilousDisorientation() {
    Spawn spawn = new Spawn(session, 1);

    assertTrue(spawn.willStack(session.getSpell(getSpellData("Confound").getId())));

    spawn.updateBuffsAndDurations("" + getSpellData("Perilous Disorientation").getId() + ":60", true, Source.DIRECT);

    assertTrue(spawn.willStack(session.getSpell(getSpellData("Confound").getId())));
  }

  @Test
  public void willStackShouldAllowUnityOfSpiritsToStackEvenWithDifferentLevelBuffsOfItsAutocastedSpellsIfAtleastOneMissing() {
    Spawn spawn = new Spawn(session, 1);

    assertTrue(spawn.willStack(session.getSpell(getSpellData("Unity of the Spirits").getId())));

    spawn.updateBuffsAndDurations("" + getSpellData("Mammoth's Strength").getId() + ":60", true, Source.DIRECT);

    assertTrue(spawn.willStack(session.getSpell(getSpellData("Unity of the Spirits").getId())));

    spawn.updateBuffsAndDurations("" + getSpellData("Mammoth's Strength").getId() + ":60 " + getSpellData("Preternatural Foresight").getId() + ":60", true, Source.DIRECT);

    assertTrue(spawn.willStack(session.getSpell(getSpellData("Unity of the Spirits").getId())));

    spawn.updateBuffsAndDurations("" + getSpellData("Mammoth's Strength").getId() + ":60 " + getSpellData("Preeminent Foresight").getId() + ":60", true, Source.DIRECT);

    assertTrue(spawn.willStack(session.getSpell(getSpellData("Unity of the Spirits").getId())));
  }

  private static SpellData getSpellData(String name) {
    SpellData bestSD = null;

    for(SpellData sd : rawSpellData.values()) {
      if(sd.getName().equals(name)) {
        if(bestSD != null) {
          System.out.println("WARNING: Conflict between " + bestSD.getId() + " and " + sd.getId() + "; same name: " + sd.getName());
        }
        bestSD = sd;
      }
    }

    return bestSD;
  }
}