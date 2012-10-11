package autoeq.eq;

public interface ResourceProvider<T> {
  public T obtainResource();
  public void releaseResource(T resource);
}
