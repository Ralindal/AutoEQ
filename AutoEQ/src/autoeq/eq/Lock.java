package autoeq.eq;

public class Lock<T> {
  private T owner;
  private int lockCount;

  public synchronized void lock(T owner) {
    if(this.owner != owner) {
      throw new IllegalStateException(this + " is owned by " + this.owner + " cannot be locked by " + owner);
    }
    lockCount++;
  }

  public synchronized boolean tryLock(T owner) {
    if(owner == null) {
      throw new RuntimeException("parameter 'owner' cannot be null");
    }

    if(this.owner == owner || this.owner == null) {
      this.owner = owner;
      lockCount++;
      return true;
    }
    return false;
  }

  public synchronized void unlock(T owner) {
    if(owner == null) {
      throw new RuntimeException("parameter 'owner' cannot be null");
    }
    if(this.owner != owner) {
      throw new IllegalStateException(this + " is owned by " + this.owner + " cannot be unlocked by " + owner);
    }

    lockCount--;

    if(lockCount == 0) {
      this.owner = null;
    }
  }
}
