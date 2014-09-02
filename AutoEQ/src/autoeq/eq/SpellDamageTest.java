package autoeq.eq;

import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.when;

import java.io.FileReader;
import java.io.IOException;
import java.io.LineNumberReader;
import java.util.HashMap;
import java.util.Map;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import autoeq.SpellData;

public class SpellDamageTest {
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

    when(session.translate(anyString())).then(new Answer<String>() {
      @Override
      public String answer(InvocationOnMock invocation) throws Throwable {
        String translationString = (String)(invocation.getArguments()[0]);

        if("${Me.AltAbility[Mnemonic Retention]}".equals(translationString)) {
          return "3";
        }

        return "99;0;group v2;detrimental;4.0";
      }
    });

    Me me = new Me(session, 1);

    when(session.getMe()).thenReturn(me);
  }

  @Test
  public void shouldHandleNormalDamage() {
    Spell spell = session.getSpell(getSpellData("Ethereal Iceblight").getId());

    Assert.assertEquals(9593, spell.getDamage());
  }

  @Test
  public void shouldHandleNormalAndRaidDamage() {
    Spell spell = session.getSpell(getSpellData("Ethereal Incandescence").getId());

    Assert.assertEquals(25747, spell.getDamage());
  }

  @Test
  public void shouldHandleAutoCastDamage() {
    Spell spell = session.getSpell(getSpellData("Wildmagic Blast").getId());

    Assert.assertEquals(2993, spell.getDamage());
  }

  @Test
  public void shouldHandleComplexAutoCastDamage() {
    Spell spell = session.getSpell(getSpellData("Ethereal Weave").getId());

    Assert.assertEquals(33785, spell.getDamage());
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
