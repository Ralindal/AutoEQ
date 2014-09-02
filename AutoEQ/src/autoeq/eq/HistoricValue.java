package autoeq.eq;

import java.util.ArrayList;
import java.util.List;

public class HistoricValue<T> {
  private final List<T> data = new ArrayList<>();  // most recent data at end
  private final List<Long> times = new ArrayList<>();
  private final long maxMillis;
  private final long granularity;

  public HistoricValue(T startValue, long maxMillis, long granularity) {
    if(startValue != null) {
      add(startValue);
    }
    this.maxMillis = maxMillis;
    this.granularity = granularity;
  }

  public HistoricValue(long maxMillis, long granularity) {
    this(null, maxMillis, granularity);
  }

  public HistoricValue(long maxMillis) {
    this(null, maxMillis, 0);
  }

  public HistoricValue(T startValue, long maxMillis) {
    this(startValue, maxMillis, 0);
  }

  public synchronized void reset() {
    data.clear();
    times.clear();
  }

  public synchronized T getMostRecent() {
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

  public synchronized T getValue(long millisAgo) {
    long millis = System.currentTimeMillis();

    for(int i = times.size() - 1; i >= 0; i--) {
      long time = times.get(i);

      if(millis - time >= millisAgo) {
        return data.get(i);
      }
    }

    return data.isEmpty() ? null : data.get(0);
  }

  public synchronized void add(T value) {
    long millis = System.currentTimeMillis();

    if(data.isEmpty() || !value.equals(data.get(data.size() - 1))) {
      int count = times.size();

      if(count > 1 && times.get(count - 1) - times.get(count - 2) < granularity) {

        /*
         * Below granularity, replace last element
         */

        data.set(count - 1, value);
        times.set(count - 1, millis);
      }
      else {
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
    }
  }

  public List<T> getMostRecentSubListCopy(long sinceMillisAgo) {
    long since = System.currentTimeMillis() - sinceMillisAgo;

    for(int i = times.size() - 1; i >= 0; i--) {
      long time = times.get(i);

      if(since > time) {
        return i + 1 == times.size() ? new ArrayList<T>() : new ArrayList<>(data.subList(i + 1, times.size()));
      }
    }

    return new ArrayList<>(data);
  }
}
