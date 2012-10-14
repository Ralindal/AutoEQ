package autoeq.eq;

import java.util.Iterator;
import java.util.LinkedList;

public class HistoryValue<T> implements Iterable<Point<T>> {
  private final LinkedList<Point<T>> points = new LinkedList<>();
  private final long maxMillis;

  public HistoryValue(long maxMillis) {
    this.maxMillis = maxMillis;
  }

  public HistoryValue(T startValue, long maxMillis) {
    this.maxMillis = maxMillis;
    add(startValue);
  }

  public synchronized void reset() {
    points.clear();
  }

  public synchronized T getMostRecent() {
    return points.getFirst().getValue();
  }

  /**
   * Returns the maximum duration of values that has been record in milliseconds.
   */
  public synchronized long getPeriod() {
    if(points.size() == 0) {
      return 0;
    }
    else {
      return System.currentTimeMillis() - points.getLast().getMillis();
    }
  }

  public synchronized T getValue(long millisAgo) {
    long millis = System.currentTimeMillis();
    T value = null;

    for(Point<T> point : points) {
      value = point.getValue();

      if(millis - point.getMillis() >= millisAgo) {
        break;
      }
    }

    return value;
  }

  public synchronized void add(T value) {
    add(value, System.currentTimeMillis());
  }

  public synchronized void add(T value, long millis) {
    if(points.size() == 0 || !value.equals(points.getFirst().getValue())) {
      points.addFirst(new Point<>(millis, value));

      Iterator<Point<T>> iterator = points.descendingIterator();
      int removeCount = 0;

      /*
       * Counts the amount of entries at the end of the list that are outside the maxMillis range.
       */

      while(iterator.hasNext()) {
        Point<T> point = iterator.next();

        if(millis - point.getMillis() <= maxMillis) {
          break;
        }

        removeCount++;
      }

      /*
       * Remove all but one of those entries (we keep one that is outside the specified range since it
       * atleast partially gives information of the range we're interested in).
       */

      while(--removeCount > 0) {
        points.removeLast();
      }
    }
  }

  @Override
  public Iterator<Point<T>> iterator() {
    return points.iterator();
  }
}
