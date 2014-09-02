package autoeq.ini;

import static org.junit.Assert.assertEquals;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;

import org.junit.Test;

public class Ini2Test {

  @Test
  public void moreSpecificIniShouldOverrideValuesInSameSection() throws IOException {
    Ini2 ini2 = new Ini2(
      new BufferedReader(new StringReader(
        "[Bla]\n" +
        "Key=X\n"
      )),
      new BufferedReader(new StringReader(
        "[Bla]\n" +
        "Key=Y\n"
      ))
    );

    assertEquals("Y", ini2.getSection("Bla").get("Key"));
  }

  @Test
  public void parentShouldNotOverrideValuesInSameSection() throws IOException {
    Ini2 ini2 = new Ini2(
      new BufferedReader(new StringReader(
        "[Parent]\n" +
        "Key=X\n" +
        "\n" +
        "[Bla] : [Parent]\n" +
        "Key=Y\n"
      ))
    );

    assertEquals("Y", ini2.getSection("Bla").get("Key"));
  }

  @Test
  public void laterSectionWithSameNameShouldOverrideValues() throws IOException {
    Ini2 ini2 = new Ini2(
      new BufferedReader(new StringReader(
        "[Bla]\n" +
        "Key=X\n" +
        "\n" +
        "[Bla]\n" +
        "Key=Y\n"
      ))
    );

    assertEquals("Y", ini2.getSection("Bla").get("Key"));
    assertEquals(2, ini2.getSection("Bla").getAll("Key").size());
  }

  @Test
  public void parentInLessSpecificIniFileShouldNotOverrideValuesInSameSection() throws IOException {
    Ini2 ini2 = new Ini2(
      new BufferedReader(new StringReader(
        "[Parent]\n" +
        "Key=X\n"
      )),
      new BufferedReader(new StringReader(
        "[Bla] : [Parent]\n" +
        "Key=Y\n"
      ))
    );

    assertEquals("Y", ini2.getSection("Bla").get("Key"));
  }
}
