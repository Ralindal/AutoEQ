package autoeq.ini;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Ini2 implements Iterable<Section> {
  private final File iniFile;
  private final Map<String, Section> sections;

  public Ini2(File iniFile) throws IOException {
    this.iniFile = iniFile;
    sections = readIniFile(iniFile);
  }

  public Ini2() {
    sections = new HashMap<>();
    iniFile = null;
  }

  public void addSection(Section section) {
    sections.put(section.getName(), section);
  }

  public Section getSection(String sectionName) {
    return sections.get(sectionName);
  }

  public String getValue(String sectionName, String key) {
    Section section = sections.get(sectionName);

    if(section != null) {
      return section.get(key);
    }

    return "";
  }

  @Override
  public Iterator<Section> iterator() {
    return sections.values().iterator();
  }

  public void save() throws IOException {
    if(iniFile != null) {
      try(PrintWriter writer = new PrintWriter(new FileWriter(iniFile))) {
        boolean first = true;

        for(Section section : sections.values()) {
          if(!first) {
            writer.println();
          }
          writer.println("[" + section.getName() + "]");
          first = false;

          for(String key : section) {
            for(String value : section.getAll(key)) {
              writer.println(key + "=" + value);
            }
          }
        }
      }
    }
  }

  private static final Pattern SECTION_PATTERN = Pattern.compile("\\[([-A-Za-z0-9\\.]+)\\](\\s*:\\s*\\[([-A-Za-z0-9\\.]+)\\])?");

  private static Map<String, Section> readIniFile(File iniFile) throws IOException {
    try(BufferedReader reader = new BufferedReader(new FileReader(iniFile))) {
      Map<String, Section> sections = new LinkedHashMap<>();
      Section currentSection = null;

      for(;;) {
        String line = reader.readLine();

        if(line == null) {
          break;
        }

        line = line.trim();
        Matcher matcher = SECTION_PATTERN.matcher(line);

        if(matcher.matches()) {
          String sectionName = matcher.group(1);
          currentSection = sections.get(sectionName);

          Section parentSection = null;

          if(matcher.group(3) != null) {
            parentSection = sections.get(matcher.group(3));
            if(parentSection == null) {
              throw new RuntimeException("Parse Error, Parent '" + matcher.group(3) + "' not found for section '" + sectionName + "'");
            }
          }

          if(currentSection == null) {
            currentSection = new Section(sectionName, parentSection);
            sections.put(currentSection.getName(), currentSection);
          }
        }
        else if(currentSection != null) {
          int eq = line.indexOf('=');

          if(eq > 1) {
            currentSection.put(line.substring(0, eq).trim(), line.substring(eq + 1).trim());
          }
        }
      }
      return sections;
    }
  }
}
