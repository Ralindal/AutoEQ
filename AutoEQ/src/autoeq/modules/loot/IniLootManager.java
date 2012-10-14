package autoeq.modules.loot;

import java.io.File;
import java.io.IOException;

import autoeq.ini.Ini2;
import autoeq.ini.Section;


public class IniLootManager implements LootManager {
  private final File iniFile;
  
  private Ini2 ini;
  private long iniLastModified;
  
  public IniLootManager(String path) throws IOException {
    iniFile = new File(path + "/jloot.ini");
    reloadIni();
  }
  
  @Override
  public void addLoot(String name, LootType type) {
    if(checkIni()) {
      Section section = ini.getSection(name.substring(0, 1).toUpperCase());
      
      if(section == null) {
        section = new Section(name.substring(0, 1).toUpperCase(), null);
        ini.addSection(section);
      }
      
      String typeString = type.name().substring(0, 1).toUpperCase() + type.name().toLowerCase().substring(1);
      
      section.put(name, typeString);
      
      try {
        ini.save();
      }
      catch(IOException e) {
        e.printStackTrace();
      }
    }
  }

  @Override
  public LootType getLootType(String name) {
    if(checkIni()) {
      Section section = ini.getSection(name.substring(0, 1).toUpperCase());
  
      if(section != null && section.get(name) != null) {
        return LootType.valueOf(section.get(name).toUpperCase());
      }
      
      System.err.println("Unknown loot : " + name);
    }
      
    return null;
  }
  
  private boolean checkIni() {
    if(iniFile.exists() && iniFile.lastModified() != iniLastModified) {
      try {
        reloadIni();
      }
      catch(IOException e) {
        return false;
      }
    }
    
    return true;
  }
  
  private void reloadIni() throws IOException {
    if(iniFile.exists()) {
      ini = new Ini2(iniFile);
      iniLastModified = iniFile.lastModified();
    }
    else {
      ini = new Ini2();
    }
  }
}
