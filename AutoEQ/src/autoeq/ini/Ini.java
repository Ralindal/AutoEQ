package autoeq.ini;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

public class Ini implements Iterable<Section> {
  private final Map<String, Section> sections;
  
  public Ini(File iniFile) throws IOException {
    sections = readIniFile(iniFile);
  }
  
  public Ini() {
    sections = new HashMap<String, Section>();
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
  
  public Iterator<Section> iterator() {
    return sections.values().iterator();
  }
  
  private static Map<String, Section> readIniFile(File iniFile) throws IOException {
    BufferedReader reader = new BufferedReader(new FileReader(iniFile));
    Map<String, Section> sections = new LinkedHashMap<String, Section>();
    Section currentSection = null;
    
    for(;;) {
      String line = reader.readLine();
      
      if(line == null) {
        break;
      }
      
      line = line.trim();
      
      if(line.startsWith("[")) {
        String sectionName = line.substring(1, line.length() - 1);
        currentSection = sections.get(sectionName);
        
        if(currentSection == null) {
          currentSection = new Section(sectionName, null);
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
