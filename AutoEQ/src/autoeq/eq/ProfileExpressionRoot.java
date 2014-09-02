package autoeq.eq;

import java.util.Set;

public class ProfileExpressionRoot {
  private final Set<ProfileSet> profileSets;
  private final EverquestSession session;

  public ProfileExpressionRoot(Set<ProfileSet> profileSets, EverquestSession session) {
    this.profileSets = profileSets;
    this.session = session;
  }

  public boolean isActive(String profileName) {
    for(ProfileSet set : profileSets) {
      if(profileName.equals(set.getActiveProfile())) {
        return true;
      }
    }

    return false;
  }

  public boolean profile(String profileName) {
    for(ProfileSet set : profileSets) {
      if(profileName.equals(set.getActiveProfile())) {
        return true;
      }
    }

    return false;
  }

  public EverquestSession getSession() {
    return session;
  }

  public Me getMe() {
    return session.getMe();
  }

  public Group group() {
    return new Group(session);
  }
}
