package autoeq.eq;

import java.util.ArrayList;
import java.util.List;

public class RollingAverage {
  private final List<Double> data = new ArrayList<>();  // most recent data at end
  private final List<Long> times = new ArrayList<>();
  private final long maxMillis;

  public RollingAverage(long maxMillis) {
    this.maxMillis = maxMillis;
  }

  public synchronized void reset() {
    data.clear();
    times.clear();
  }

  public synchronized Double getMostRecent() {
    return data.isEmpty() ? null : data.get(data.size() - 1);
  }

  /**
   * Returns the maximum duration of values that has been record in milliseconds.
   */
  public synchronized long getPeriod() {
    if(times.isEmpty()) {
      return 0;
    }
    else {
      return System.currentTimeMillis() - times.get(0);
    }
  }

  public synchronized Double getValue(long millisAgo) {
    long millis = System.currentTimeMillis();

    for(int i = times.size() - 1; i >= 0; i--) {
      long time = times.get(i);

      if(millis - time >= millisAgo) {
        return data.get(i);
      }
    }

    return data.isEmpty() ? null : data.get(0);
  }

  public synchronized void add(Double value) {
    long millis = System.currentTimeMillis();

    data.add(value);
    times.add(millis);

    if(data.size() % 16 == 0) {

      /*
       * Remove all elements older than the max allowed time, except for the first one.
       * This is done only every now and then to prevent unnecessary moving of elements in the
       * array list.
       */

      for(int i = 0; i < times.size(); i++) {
        long time = times.get(i);

        if(millis - time <= maxMillis) {
          if(i > 1) {
            times.subList(0, i - 1).clear();
            data.subList(0, i - 1).clear();
          }
          break;
        }
      }
    }
  }

  public List<Double> getMostRecentSubListCopy(long sinceMillisAgo) {
    long since = System.currentTimeMillis() - sinceMillisAgo;

    for(int i = times.size() - 1; i >= 0; i--) {
      long time = times.get(i);

      if(since > time) {
        return i + 1 == times.size() ? new ArrayList<Double>() : new ArrayList<>(data.subList(i + 1, times.size()));
      }
    }

    return new ArrayList<>(data);
  }

  public double computeAveragePerEntry(long sinceMillisAgo) {
    long since = System.currentTimeMillis() - sinceMillisAgo;
    double totalValue = 0;
    int entryCount = 0;

    for(int i = times.size() - 1; i >= 0; i--) {
      long time = times.get(i);

      if(since > time) {
        break;
      }

      totalValue += data.get(i);
      entryCount++;
    }

    if(entryCount > 0) {
      return totalValue / entryCount;
    }

    return 0;
  }

  public double computeAverageOverTime(long sinceMillisAgo) {
    long since = System.currentTimeMillis() - sinceMillisAgo;
    double totalValue = 0;
    long startTime = System.currentTimeMillis();

    for(int i = times.size() - 1; i >= 0; i--) {
      long time = times.get(i);

      if(since > time) {
        break;
      }

      totalValue += data.get(i);
      startTime = time;
    }

    /*
     * Adjust total value for size of period
     */

    long period = System.currentTimeMillis() - startTime;

    if(period > 0) {
      return totalValue / period * sinceMillisAgo;
    }

    return 0;
  }
}
