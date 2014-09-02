package autoeq.ini;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class Section implements Iterable<String> {
  private final String name;
  private final List<Section> parents;
  private final Map<String, List<String>> values = new LinkedHashMap<>();

  @SuppressWarnings("unchecked")
  public Section(String name, List<Section> parents) {
    this.name = name;
    this.parents = parents == null ? (List<Section>)Collections.EMPTY_LIST : parents;
  }

  public String getName() {
    return name;
  }

  public void put(String key, String value) {
    List<String> list = values.get(key);

    if(list == null) {
      list = new ArrayList<>();
      values.put(key, list);
    }

    list.add(value);
  }

  public void putAtStart(String key, String value) {
    List<String> list = values.get(key);

    if(list == null) {
      list = new ArrayList<>();
      values.put(key, list);
    }

    list.add(0, value);
  }

  public void set(String key, String value) {
    List<String> list = values.get(key);

    if(list == null) {
      list = new ArrayList<>();
      values.put(key, list);
    }

    list.clear();
    list.add(value);
  }

  public String getDefault(String key, String defaultValue) {
    List<String> list = getAll(key);

    return list.isEmpty() ? defaultValue : list.get(0);
  }

  public String get(String key) {
    return getDefault(key, null);
  }

  public List<String> getAll(String key) {
    List<String> results = new ArrayList<>();

    if(values.containsKey(key)) {
      results.addAll(values.get(key));
    }

    for(Section parent : parents) {
      results.addAll(parent.getAll(key));
    }

    return results;
  }

  private Set<String> allKeys = null;

  public Set<String> getAllKeys() {
    if(allKeys == null) {
      allKeys = new HashSet<>();

      allKeys.addAll(values.keySet());

      for(Section parent : parents) {
        allKeys.addAll(parent.getAllKeys());
      }
    }

    return allKeys;
  }

  @Override
  public Iterator<String> iterator() {
    return values.keySet().iterator();
  }
}
