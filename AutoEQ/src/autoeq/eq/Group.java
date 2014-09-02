package autoeq.eq;

public class Group {
  private final EverquestSession session;

  public Group(EverquestSession session) {
    this.session = session;
  }

  public boolean hasClass(String... classes) {
    for(Spawn spawn : session.getGroupMembers()) {
      if(spawn.isOfClass(classes)) {
        return true;
      }
    }

    return false;
  }

  public boolean hasAliveClass(String... classes) {
    for(Spawn spawn : session.getGroupMembers()) {
      if(spawn.isAlive() && spawn.isOfClass(classes)) {
        return true;
      }
    }

    return false;
  }
}