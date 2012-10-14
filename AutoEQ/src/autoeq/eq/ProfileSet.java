package autoeq.eq;

import java.util.HashMap;
import java.util.Map;

public class ProfileSet {
  // Maps lowercased cleaned key names to original mixed-case names.
  private final Map<String, String> profileNames = new HashMap<>();

  private String activeKey;

  public ProfileSet(String[] profiles) {
    for(int i = 0; i < profiles.length; i++) {
      String cleanedName = profiles[i].replaceAll("\\+", "");

      profileNames.put(cleanedName.toLowerCase(), cleanedName);

      if(profiles[i].contains("+")) {
        activeKey = cleanedName.toLowerCase();
      }
    }
  }

  public String getActiveProfile() {
    return profileNames.get(activeKey);
  }

  public boolean contains(String profileName) {
    return profileNames.containsKey(profileName.toLowerCase());
  }

  public void toggle(String profileName) {
    if(!contains(profileName)) {
      throw new RuntimeException("No such profile: " + profileName);
    }

    activeKey = profileName.toLowerCase().equals(activeKey) ? null : profileName.toLowerCase();
  }

  @Override
  public String toString() {
    String s = "";

    for(String name : profileNames.values()) {
      if(s.length() > 0) {
        s += ", ";
      }
      s += name;
      if(activeKey != null && activeKey.equals(name.toLowerCase())) {
        s += "*";
      }
    }

    return s;
  }
}
