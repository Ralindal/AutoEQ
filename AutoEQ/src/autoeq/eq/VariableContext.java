package autoeq.eq;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class VariableContext {
  private final Map<String, Object> variables = new HashMap<>();
  private final Map<String, Long> setTimes = new HashMap<>();
  private final Map<String, Long> expiryTimes = new HashMap<>();

  public synchronized Object getVariable(String name) {
    return getVariableOrDefault(name, null);
  }

  public synchronized String getVariableAsString(String name) {
    return getVariableOrDefaultAsString(name, null);
  }

  public synchronized Object getVariableOrDefault(String name, Object defaultValue) {
    checkExpiry(name);

    return variables.containsKey(name) ? variables.get(name) : defaultValue;
  }

  public synchronized String getVariableOrDefaultAsString(String name, String defaultValue) {
    checkExpiry(name);

    return variables.containsKey(name) ? (String)variables.get(name) : defaultValue;
  }

  public synchronized boolean hasVariable(String name) {
    checkExpiry(name);

    return variables.containsKey(name);
  }

  public synchronized boolean hasVariableOlderThan(String name, long olderThan) {
    checkExpiry(name);

    return variables.containsKey(name) && setTimes.get(name) < System.currentTimeMillis() - olderThan;
  }

  private void checkExpiry(String name) {
    Long expiryTime = expiryTimes.get(name);

    if(expiryTime != null && System.currentTimeMillis() > expiryTime) {
      variables.remove(name);
      setTimes.remove(name);
      expiryTimes.remove(name);
    }
  }

  public synchronized void clearVariable(String name) {
    variables.remove(name);
    setTimes.remove(name);
    expiryTimes.remove(name);
  }

  public synchronized void setVariable(String name) {
    setVariable(name, true);
  }

  public synchronized void setVariable(String name, Object obj) {
    checkExpiry(name);
    variables.put(name, obj);
    if(!setTimes.containsKey(name)) {
      setTimes.put(name, System.currentTimeMillis());
    }
  }

  public synchronized void setExpiringVariable(String name, long expireMillis) {
    setExpiringVariable(name, expireMillis, true);
  }

  public synchronized void setExpiringVariable(String name, long expireMillis, Object obj) {
    setVariable(name, obj);
    expiryTimes.put(name, System.currentTimeMillis() + expireMillis);
  }

  public synchronized void setExpiringVariableIfNotSet(String name, long expireMillis, Object obj) {
    if(!hasVariable(name)) {
      setVariable(name, obj);
      expiryTimes.put(name, System.currentTimeMillis() + expireMillis);
    }
  }

  public synchronized Set<String> keySet() {
    return variables.keySet();
  }
}
