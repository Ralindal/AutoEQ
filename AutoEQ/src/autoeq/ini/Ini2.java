package autoeq.ini;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Advanced INI parser, supporting comments, multi-value keys, inheritance and root sections.
 *
 * [Section]
 * a=2
 * b=3
 *
 * [SubSection] : [Section]
 * c=2
 */
public class Ini2 implements Iterable<Section> {
  private final File iniFile;
  private final Map<String, Section> sections;

  @SuppressWarnings("resource")
  public Ini2(File iniFile, File... additionalIniFiles) throws IOException {
    this.iniFile = iniFile;
    List<BufferedReader> readers = new ArrayList<>();

    for(File additionalIniFile : additionalIniFiles) {
      if(additionalIniFile.exists()) {
        readers.add(new BufferedReader(new FileReader(additionalIniFile)));
      }
    }

    if(iniFile.exists()) {
      readers.add(new BufferedReader(new FileReader(iniFile)));
    }

    sections = readIniFile(readers);

    for(BufferedReader reader : readers) {
      reader.close();
    }
  }

  public Ini2(BufferedReader... readers) throws IOException {
    iniFile = null;
    sections = readIniFile(Arrays.asList(readers));
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

  private static final String IDENTIFIER = "[-A-Za-z0-9$\\.]+";
  private static final String PARENTS = IDENTIFIER + "(?:\\s*,\\s*" + IDENTIFIER + ")*";
  private static final Pattern SECTION_PATTERN = Pattern.compile("\\[(" + IDENTIFIER + ")\\](\\s*:\\s*\\[(" + PARENTS + ")\\])?");
  private static final Pattern COMMENT_START = Pattern.compile("(?!<\\\\)#");

  private static Map<String, Section> readIniFile(List<BufferedReader> allIniFiles) throws IOException {
    Map<String, Section> sections = new LinkedHashMap<>();
    boolean override = false;

    for(BufferedReader reader : allIniFiles) {
      Section currentSection = null;

      outer:
      for(;;) {
        String line = "";

        for(;;) {
          String partialLine = reader.readLine();

          if(partialLine == null) {
            break outer;
          }

          Matcher matcher = COMMENT_START.matcher(partialLine);

          if(matcher.find()) {
            partialLine = partialLine.substring(0, matcher.start());
          }

          partialLine = partialLine.replaceAll("\\\\#", "#");
          partialLine = partialLine.trim();
          line += partialLine;

          if(line.endsWith("\\")) {
            line = line.substring(0, line.length() - 1);
          }
          else {
            reader.mark(5000);
            String nextLine = reader.readLine();
            reader.reset();

            if(nextLine == null || !nextLine.startsWith("  ")) {
              break;
            }

            line += " ";
          }
        }

        Matcher matcher = SECTION_PATTERN.matcher(line);

        if(matcher.matches()) {
          String sectionName = matcher.group(1);
          currentSection = sections.get(sectionName);

          override = currentSection != null;

          List<Section> parentSections = new ArrayList<>();

          if(matcher.group(3) != null) {
            for(String parentSectionName : matcher.group(3).split(",")) {
              Section parentSection = sections.get(parentSectionName.trim());

              if(parentSection == null) {
                throw new RuntimeException("Parse Error, Parent '" + parentSectionName.trim() + "' not found for section '" + sectionName + "'");
              }

              parentSections.add(parentSection);
            }
          }

          int lastDot = sectionName.lastIndexOf('.');

          if(lastDot > 0) {
            Section rootSection = sections.get("$" + sectionName.substring(0, lastDot));

            if(rootSection != null) {
              parentSections.add(rootSection);
            }
          }

          if(currentSection == null) {
            currentSection = new Section(sectionName, parentSections);
            sections.put(currentSection.getName(), currentSection);
          }
        }
        else if(currentSection != null) {
          int eq = line.indexOf('=');

          if(eq > 1) {
            if(override) {
              currentSection.putAtStart(line.substring(0, eq).trim(), line.substring(eq + 1).trim());
            }
            else {
              currentSection.put(line.substring(0, eq).trim(), line.substring(eq + 1).trim());
            }
          }
        }
      }
    }

    return sections;
  }
}
