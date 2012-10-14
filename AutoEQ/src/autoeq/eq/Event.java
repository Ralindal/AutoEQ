package autoeq.eq;

import java.util.LinkedHashSet;

public class Event<O> {
  private final O owner;

  private final LinkedHashSet<Listener> listeners = new LinkedHashSet<>();
  private final Interface publicInterface = new Interface();

  public Event(O owner) {
    this.owner = owner;
  }

  public void trigger() {
    for(Listener listener : listeners) {
      listener.execute();
    }
  }

  public void clear() {
    listeners.clear();
  }

  public Interface getInterface() {
    return publicInterface;
  }

  public class Interface {
    public O call(Listener listener) {
      listeners.add(listener);
      return owner;
    }

    public O unregister(Listener listener) {
      listeners.remove(listener);
      return owner;
    }
  }
}
