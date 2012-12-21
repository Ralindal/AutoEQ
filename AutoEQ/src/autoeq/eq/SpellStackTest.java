package autoeq.eq;

import java.io.FileReader;
import java.io.IOException;
import java.io.LineNumberReader;
import java.util.HashMap;
import java.util.Map;

import junit.framework.Assert;

import org.junit.Test;

import autoeq.SpellData;

public class SpellStackTest {
  private static Map<Integer, SpellData> rawSpellData = new HashMap<>();

  private final SpellData symbol = getSpellData("Symbol of Ealdun");
  private final SpellData skin = getSpellData("Timbercore Skin");
  private final SpellData credence = getSpellData("Credence");
  private final SpellData unifiedCredence = getSpellData("Unified Credence");
  private final SpellData spellHaste = getSpellData("Blessing of Assurance");
  private final SpellData olderSpellHaste = getSpellData("Blessing of Resolve");
  private final SpellData focus = getSpellData("Exigent Focusing");
  private final SpellData shield = getSpellData("Shield of Dreams Rk. II");

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

  @Test
  public void symbolShouldStackWithSkin() {
    Assert.assertTrue(symbol.stacksWith(skin));
    Assert.assertTrue(skin.stacksWith(symbol));
  }

  @Test
  public void credenceShouldOverwriteSymbolButNotInReverse() {
    Assert.assertFalse(symbol.blocks(credence));
    Assert.assertTrue(credence.blocks(symbol));
  }

  @Test
  public void credenceShouldNotStackWithSkin() {
    Assert.assertFalse(credence.stacksWith(skin));
    Assert.assertFalse(skin.stacksWith(credence));
  }

  @Test
  public void unifiedCredenceShouldStackWithSpellHaste() {
    Assert.assertTrue(unifiedCredence.stacksWith(spellHaste));
    Assert.assertTrue(spellHaste.stacksWith(unifiedCredence));
  }

  @Test
  public void focusShouldNotStackWithIntCasterShield() {
    Assert.assertFalse(focus.stacksWith(shield));
    Assert.assertFalse(shield.stacksWith(focus));
  }

  @Test
  public void spellHasteShouldStackWithOlderSpellHaste() {
    Assert.assertTrue(spellHaste.stacksWith(olderSpellHaste));
    Assert.assertTrue(olderSpellHaste.stacksWith(spellHaste));
  }

  @Test
  public void unifiedCredenceShouldBeRecastedWhenSpellHasteComponentRunsOut() {
  }

  private static SpellData getSpellData(String name) {
    for(SpellData sd : rawSpellData.values()) {
      if(sd.getName().equals(name)) {
        return sd;
      }
    }

    return null;
  }

}
