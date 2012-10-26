package autoeq;

public interface EventHandler<T extends Event> {
  void handle(T event);
}
