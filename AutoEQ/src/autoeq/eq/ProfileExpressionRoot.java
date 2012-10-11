package autoeq.eq;

import java.util.Set;

public class ProfileExpressionRoot {
  private final Set<ProfileSet> profileSets;

  public ProfileExpressionRoot(Set<ProfileSet> profileSets) {
    this.profileSets = profileSets;
  }
  
  public boolean isActive(String profileName) {
    for(ProfileSet set : profileSets) {
      if(profileName.equals(set.getActiveProfile())) {
        return true;
      }
    }
    
    return false;
  }
}
