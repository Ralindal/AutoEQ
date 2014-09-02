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

  private final SpellData symbol = getSpellData("Symbol of the Triumvirate");
  private final SpellData symbolOfGezat = getSpellData("Symbol of Gezat");
  private final SpellData symbolOfGezatRkII = getSpellData("Symbol of Gezat Rk. II");
  private final SpellData wardOfTheReverentRkII = getSpellData("Ward of the Reverent Rk. II");
  private final SpellData skin = getSpellData("Timbercore Skin");
  private final SpellData certitude = getSpellData("Certitude");
  private final SpellData credence = getSpellData("Credence");
  private final SpellData unifiedCredence = getSpellData("Unified Credence");
  private final SpellData spellHaste = getSpellData("Blessing of Assurance");
  private final SpellData olderSpellHaste = getSpellData("Blessing of Resolve");
  private final SpellData focus = getSpellData("Exigent Focusing");
  private final SpellData shield = getSpellData("Shield of Dreams Rk. II");
  private final SpellData shackles = getSpellData("Atol's Unresistable Shackles");
  private final SpellData darkness = getSpellData("Encroaching Darkness");
  private final SpellData pbaeMez = getSpellData("Disorientation");
  private final SpellData singleTargetMez = getSpellData("Confound");
  private final SpellData singleTargetFlashMez = getSpellData("Confounding Flash");
  private final SpellData regen1 = getSpellData("Talisman of the Steadfast");
  private final SpellData regen2 = getSpellData("Talisman of the Indomitable");
  private final SpellData heal = getSpellData("Reverent Light");
  private final SpellData[] dots = new SpellData[] {
    getSpellData("Dread Pyre"),
    getSpellData("Ashengate Pyre"),
    getSpellData("Coruscating Shadow"),
    getSpellData("Blazing Shadow"),
    getSpellData("Blistering Shadow"),
    getSpellData("Pyre of the Forsaken"),
    getSpellData("Grip of Zalikor"),
    getSpellData("Binaesa Venom"),
    getSpellData("Termination"),
    getSpellData("Doom"),
    getSpellData("Halstor's Pallid Haze"),
    getSpellData("Stifle"),
    getSpellData("Confounding Constriction"),
  };
  private final SpellData dot1 = getSpellData("Dread Pyre");
  private final SpellData dot2 = getSpellData("Ashengate Pyre");
//  private final SpellData dot3 = getSpellData("Coruscating Shadow");
//  private final SpellData dot4 = getSpellData("Blazing Shadow");
  private final SpellData nuke = getSpellData("Ethereal Conflagration");
  private final SpellData flash = getSpellData("Flash of Anger");
  private final SpellData steadfast = rawSpellData.get(34042);
  //private final SpellData steadfast = getSpellData("Steadfast Defense");
  private final SpellData finalstand = getSpellData("Final Stand Discipline");
  private final SpellData barbedTongue = getSpellData("Barbed Tongue Discipline");
  private final SpellData fieldGuardian = getSpellData("Field Guardian");
  private final SpellData seventhWind = getSpellData("Seventh Wind");
//  private final SpellData di = getSpellData("Divine Interposition");
  private final SpellData veturikaPerseverance = getSpellData("Veturika's Perseverance");
  private final SpellData foresight = getSpellData("Foresight");
//  private final SpellData intensityOfTheResolute = getSpellData("Intensity of the Resolute");
//  private final SpellData prolongedDestruction = getSpellData("Prolonged Destruction");
  private final SpellData manaReverberation = rawSpellData.get(36160);
  private final SpellData champion = getSpellData("Champion");
  private final SpellData offensive = getSpellData("Offensive Discipline");
  private final SpellData brutalOnslaught = getSpellData("Brutal Onslaught Discipline");
  private final SpellData rest = getSpellData("Rest");
  private final SpellData defensiveProficiency = getSpellData("Defensive Proficiency");
  private final SpellData noTimeToBleed = getSpellData("No Time to Bleed");
  private final SpellData braceForImpact = getSpellData("Brace for Impact");
  private final SpellData shiningBastion = getSpellData("Shining Bastion");

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
  public void defensiveProficiencyShouldStackWithFinalStand() {
    Assert.assertTrue(defensiveProficiency.stacksWith(finalstand));
    Assert.assertTrue(finalstand.stacksWith(defensiveProficiency));
  }

  @Test
  public void defensiveProficiencyShouldStackWithNoTimeToBleed() {
    Assert.assertTrue(defensiveProficiency.stacksWith(noTimeToBleed));
    Assert.assertTrue(noTimeToBleed.stacksWith(defensiveProficiency));
  }

  @Test
  public void braceForImpactShouldStackWithNoTimeToBleed() {
    Assert.assertTrue(braceForImpact.stacksWith(noTimeToBleed));
    Assert.assertTrue(noTimeToBleed.stacksWith(braceForImpact));
  }

  @Test
  public void shiningBastionShouldStackWithNoTimeToBleed() {
    Assert.assertTrue(shiningBastion.stacksWith(noTimeToBleed));
    Assert.assertTrue(noTimeToBleed.stacksWith(shiningBastion));
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
    Assert.assertFalse(symbol.stacksWith(credence));
    Assert.assertTrue(credence.stacksWith(symbol));
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
  public void unifiedCredenceShouldStackWithCredence() {
    Assert.assertTrue(unifiedCredence.stacksWith(credence));
    Assert.assertTrue(credence.stacksWith(unifiedCredence));
  }

  @Test
  public void unifiedCertitudeShouldOverwriteSymbolOfGezatButNotInReverse() {
    Assert.assertTrue(certitude.blocks(symbolOfGezat));
    Assert.assertFalse(symbolOfGezat.blocks(certitude));
    Assert.assertTrue(certitude.stacksWith(symbolOfGezat));
    Assert.assertFalse(symbolOfGezat.stacksWith(certitude));
  }

  @Test
  public void unifiedCertitudeShouldOverwriteSymbolOfGezatRkIIButNotInReverse() {
    Assert.assertTrue(certitude.blocks(symbolOfGezatRkII));
    Assert.assertFalse(symbolOfGezatRkII.blocks(certitude));
    Assert.assertTrue(certitude.stacksWith(symbolOfGezatRkII));
    Assert.assertFalse(symbolOfGezatRkII.stacksWith(certitude));
    Assert.assertFalse(wardOfTheReverentRkII.stacksWith(certitude));
    Assert.assertTrue(certitude.stacksWith(wardOfTheReverentRkII));  // ward is a spell that cannot be cast directly, but it has lower AC so should be overwritten
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

  @Test
  public void shacklesShouldNotStackWithDarkness() {
    Assert.assertFalse(shackles.stacksWith(darkness));
    Assert.assertFalse(darkness.stacksWith(shackles));
  }

  @Test
  public void singleTargetMezOverwritesPBAEMezButNotReverse() {
    Assert.assertTrue(singleTargetMez.stacksWith(pbaeMez));
    Assert.assertFalse(pbaeMez.stacksWith(singleTargetMez));
  }

  @Test
  public void singleTargetMezzesDonotStack() {
    Assert.assertFalse(singleTargetFlashMez.stacksWith(singleTargetMez));
    Assert.assertFalse(singleTargetMez.stacksWith(singleTargetFlashMez));
  }

  @Test
  public void healShouldStackWithRegen() {
    Assert.assertTrue(heal.stacksWith(regen1));
    Assert.assertTrue(regen1.stacksWith(heal));
  }

  @Test
  public void regensShouldNotStack() {
    Assert.assertFalse(regen2.stacksWith(regen1));
    Assert.assertFalse(regen1.stacksWith(regen2));
  }

  @Test
  public void dotsAndNukesShouldStackWithEachOther() {
    Assert.assertTrue(dot1.stacksWith(dot2));
    Assert.assertTrue(dot2.stacksWith(dot1));
    Assert.assertTrue(nuke.stacksWith(dot1));
    Assert.assertTrue(dot1.stacksWith(nuke));
  }

  @Test
  public void dotsShouldStackWithEachOther() {
    for(SpellData dot1 : dots) {
      for(SpellData dot2 : dots) {
        Assert.assertTrue(dot1.getName() + " does not stack with " + dot2.getName(), dot1.stacksWith(dot2));
      }
    }
  }

  @Test
  public void flashOfAngerShouldStackWithDiscs() {
    Assert.assertTrue(flash.stacksWith(finalstand));
    Assert.assertTrue(flash.stacksWith(steadfast));
    Assert.assertTrue(finalstand.stacksWith(flash));
    Assert.assertTrue(steadfast.stacksWith(flash));
  }

  @Test
  public void barbedTongueShouldStackWithDiscs() {
    Assert.assertTrue(barbedTongue.stacksWith(finalstand));
    Assert.assertTrue(barbedTongue.stacksWith(steadfast));
    Assert.assertTrue(finalstand.stacksWith(barbedTongue));
    Assert.assertTrue(steadfast.stacksWith(barbedTongue));
  }

  @Test
  public void fieldGuardianShouldStackWithDiscs() {
    Assert.assertTrue(fieldGuardian.stacksWith(finalstand));
    Assert.assertTrue(fieldGuardian.stacksWith(steadfast));
    Assert.assertTrue(finalstand.stacksWith(fieldGuardian));
    Assert.assertTrue(steadfast.stacksWith(fieldGuardian));
  }

  @Test
  public void seventhWindShouldNotStackWithDiscs() {
    Assert.assertFalse(seventhWind.stacksWith(finalstand));
    Assert.assertFalse(seventhWind.stacksWith(steadfast));
    Assert.assertFalse(finalstand.stacksWith(seventhWind));
    Assert.assertFalse(steadfast.stacksWith(seventhWind));
  }

  @Test
  public void brutalOnslaughtShouldNotStackWithOffensive() {
    Assert.assertFalse(brutalOnslaught.stacksWith(offensive));
    Assert.assertFalse(offensive.stacksWith(brutalOnslaught));
    Assert.assertFalse(rest.stacksWith(offensive));
    Assert.assertFalse(offensive.stacksWith(rest));
    Assert.assertFalse(brutalOnslaught.stacksWith(rest));
    Assert.assertFalse(rest.stacksWith(brutalOnslaught));
  }

  @Test
  public void discsShouldNotStack() {
    Assert.assertFalse(steadfast.stacksWith(finalstand));
    Assert.assertFalse(finalstand.stacksWith(steadfast));
    Assert.assertFalse(rest.stacksWith(finalstand));
    Assert.assertFalse(finalstand.stacksWith(rest));
    Assert.assertFalse(steadfast.stacksWith(rest));
    Assert.assertFalse(rest.stacksWith(steadfast));
  }

  @Test
  public void veturikaPerseveranceShouldStacksWithForesight() {
    Assert.assertTrue(veturikaPerseverance.stacksWith(foresight));
    Assert.assertTrue(foresight.stacksWith(veturikaPerseverance));
  }

//  @Test
//  public void intensityShouldNotStackWithFury() {
//    Assert.assertFalse(intensityOfTheResolute.stacksWith(prolongedDestruction));
//    Assert.assertFalse(prolongedDestruction.stacksWith(intensityOfTheResolute));
//  }

  @Test
  public void championShouldStackWithManaReverberation() {
    Assert.assertTrue(manaReverberation.getCastTime() > 2000);
    Assert.assertTrue(champion.stacksWith(manaReverberation));
    Assert.assertTrue(manaReverberation.stacksWith(champion));
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
