package autoeq.eq;

import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

public class TimeToLiveAnalyzer {
  private final Map<String, float[]> ttlsByName = new HashMap<>();
  private final Map<Integer, float[]> ttlsByLevel = new HashMap<>();

  public void submit(TreeMap<Integer, Long> healthTimes, int level, String name) {
    normalize(healthTimes);
    interpolate(healthTimes);

    float[] nameTTLs = ttlsByName.get(name);

    if(nameTTLs == null) {
      ttlsByName.put(name, toFloatArray(healthTimes));
    }
    else {
      for(int i = 0; i <= 100; i++) {
        nameTTLs[i] = nameTTLs[i] * 0.8f + (float)healthTimes.get(i) * 0.2f;
      }
    }

    float[] levelTTLs = ttlsByLevel.get(level);

    if(levelTTLs == null) {
      ttlsByLevel.put(level, toFloatArray(healthTimes));
    }
    else {
      for(int i = 0; i <= 100; i++) {
        levelTTLs[i] = levelTTLs[i] * 0.8f + (float)healthTimes.get(i) * 0.2f;
      }
    }
  }

  private static float[] toFloatArray(TreeMap<Integer, Long> healthTimes) {
    float[] ttls = new float[101];

    for(int i = 0; i <= 100; i++) {
      ttls[i] = healthTimes.get(i);
    }

    return ttls;
  }

  private static void normalize(TreeMap<Integer, Long> healthTimes) {
    long deathTime = healthTimes.get(0);

    for(Map.Entry<Integer, Long> entry : healthTimes.entrySet()) {
      entry.setValue(deathTime - entry.getValue());
    }
  }

  private static void interpolate(TreeMap<Integer, Long> healthTimes) {
    for(int i = 1; i <= 100; i++) {
      if(!healthTimes.containsKey(i)) {
        int previousHealth = healthTimes.lowerKey(i);
        Integer nextHealth = healthTimes.higherKey(i);

        if(nextHealth != null) {
          double previousTTL = healthTimes.get(previousHealth);
          double nextTTL = healthTimes.get(nextHealth);

          healthTimes.put(i, (long)(previousTTL + ((nextTTL - previousTTL) / (nextHealth - previousHealth) * (i - previousHealth))));
        }
        else {
          long previousTTL = healthTimes.get(previousHealth);
          long previous2TTL = healthTimes.get(previousHealth - 1);

          healthTimes.put(i, previousTTL + (previousTTL - previous2TTL));
        }
      }
    }
  }

  public int getTimeToLive(Spawn spawn) {
    float[] levelTTLs = ttlsByLevel.get(spawn.getLevel());
    float[] nameTTLs = ttlsByName.get(spawn.getName());
    int hitPointsPct = spawn.getHitPointsPct();

    if(levelTTLs != null && nameTTLs != null) {
      return (int)((levelTTLs[hitPointsPct] + nameTTLs[hitPointsPct]) / 2 / 1000);
    }
    if(levelTTLs != null) {
      return (int)(levelTTLs[hitPointsPct] / 1000);
    }
    if(nameTTLs != null) {
      return (int)(nameTTLs[hitPointsPct] / 1000);
    }

    return 300;  // 5 minutes
  }
}
