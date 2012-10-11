package autoeq.eq;

public class Priority {
  public static int decodePriority(String priority, int basePriority) {
    if(priority == null) {
      return basePriority;
    }
    
    String p = priority.trim();
    
    if(p.startsWith("-")) {
      return basePriority - Integer.parseInt(p.substring(1));
    }
    else if(p.startsWith("+")) {
      return basePriority + Integer.parseInt(p.substring(1));
    }
    else {
      return Integer.parseInt(p);
    }
  }
}
