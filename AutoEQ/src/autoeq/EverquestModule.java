package autoeq;

import java.util.HashMap;
import java.util.Map;


import autoeq.eq.EverquestSession;
import autoeq.modules.camp.CampModule;

import com.google.inject.AbstractModule;
import com.google.inject.Key;
import com.google.inject.Provider;
import com.google.inject.Scope;

public class EverquestModule extends AbstractModule {
  private final EverquestSession everquestSession;

  public EverquestModule(EverquestSession everquestSession) {
    this.everquestSession = everquestSession;
  }

  @Override
  protected void configure() {
    final Map<Key<?>, Object> cache = new HashMap<Key<?>, Object>();
    
    bindScope(ThreadScoped.class, new Scope() {
      public <T> Provider<T> scope(final Key<T> key, final Provider<T> creator) {
        return new Provider<T>() {
          @SuppressWarnings("unchecked")
          public T get() {
            T value = (T)cache.get(key);
            if(value == null) {
              value = creator.get();
              cache.put(key, value);
            }
            return value;
          }
        };
      }
    });
    bind(EverquestSession.class).toInstance(everquestSession);
    bind(CampModule.class);
  }
}
