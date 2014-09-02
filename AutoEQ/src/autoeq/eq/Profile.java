package autoeq.eq;

public class Profile {
  private final ProfileSet profileSet;
  private final String profileName;
  private final String[] defaultProfiles;

  public Profile(ProfileSet profileSet, String profileName, String... defaultProfiles) {
    this.profileSet = profileSet;
    this.profileName = profileName;
    this.defaultProfiles = defaultProfiles;
  }

  public ProfileSet getProfileSet() {
    return profileSet;
  }

  public void activate() {
    profileSet.activate(profileName.toLowerCase());
  }

  public void deactivate() {
    profileSet.deactivate(profileName.toLowerCase());
  }

  public boolean toggle() {
    return profileSet.toggle(profileName.toLowerCase());
  }

  public String[] getDefaultProfiles() {
    return defaultProfiles;
  }
}
