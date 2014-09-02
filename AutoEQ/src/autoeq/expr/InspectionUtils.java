package autoeq.expr;

public class InspectionUtils {
  /**
   * Returns true if all classes in the sources list are assignment compatible with the targets list. In other words, if
   * all targets[n].isAssignableFrom( sources[n] ) then this method returns true. Any null values in sources are
   * considered wild-cards and will skip the isAssignableFrom check as if it passed.
   */
  public static boolean areTypesCompatible(Class<?>[] targets, Class<?>[] sources) {
    if(targets.length != sources.length) {
      return false;
    }

    for(int i = 0; i < targets.length; i++) {
      if(sources[i] == null) {
        continue;
      }

      if(targets[i].isAssignableFrom(sources[i])) {
        continue;
      }

      if(!translateFromPrimitive(targets[i]).isAssignableFrom(sources[i])) {
        return false;
      }
    }

    return true;
  }

  public static boolean areTypesAlmostCompatible(Class<?>[] targets, Class<?>... sources) {
    if(targets.length != sources.length) {
      return false;
    }

    for(int i = 0; i < targets.length; i++) {
      Class<?> source = sources[i];

      if(source == null) {
        continue;
      }

      if(!isTypeAlmostCompatible(targets[i], source)) {
        return false;
      }
    }

    return true;
  }

  public static boolean isTypeAlmostCompatible(Class<?> targetClass, Class<?> sourceClass) {
    if(targetClass.isAssignableFrom(sourceClass)) {
      return true;
    }

    if(targetClass.isEnum() && String.class.isAssignableFrom(sourceClass)) {
      return true;
    }

    Class<?> targetWrapper = translateFromPrimitive(targetClass);
    Class<?> sourceWrapper = translateFromPrimitive(sourceClass);

    if(targetWrapper.isAssignableFrom(Long.class) && sourceWrapper.isAssignableFrom(Integer.class)) {
      return true;
    }

    if(targetWrapper.isAssignableFrom(sourceWrapper)) {
      return true;
    }

    return false;
  }

  public static Object[] convertSourcesToBeCompatible(Class<?>[] targets, Object... sources) {
    if(targets.length != sources.length) {
      throw new IllegalArgumentException("call areTypesAlmostCompatible first to check if the types match");
    }

    Object[] results = new Object[sources.length];

    for(int i = 0; i < targets.length; i++) {
      results[i] = convertToType(targets[i], sources[i]);
    }

    return results;
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  protected static Object convertToType(Class<?> target, Object source) {
    Object result = source;

    if(source == null) {
      return result;
    }

    if(target.isAssignableFrom(source.getClass())) {
      return result;
    }

    if(target.isEnum() && source instanceof String) {
      try {
        return Enum.valueOf((Class<? extends Enum>)target, (String)source);
      }
      catch(IllegalArgumentException e) {
        throw new IllegalArgumentException("call areTypesAlmostCompatible first to check if the types match");
      }
    }

    Class<?> targetWrapper = translateFromPrimitive(target);
    Class<?> sourceWrapper = translateFromPrimitive(source.getClass());

    if(targetWrapper.isAssignableFrom(Long.class) && sourceWrapper.isAssignableFrom(Integer.class)) {
      return result;
    }

    if(!targetWrapper.isAssignableFrom(sourceWrapper)) {
      throw new IllegalArgumentException("call areTypesAlmostCompatible first to check if the types match");
    }

    return result;
  }

  /**
   * If this specified class represents a primitive type (int, float, etc.) then it is translated into its wrapper type
   * (Integer, Float, etc.). If the passed class is not a primitive then it is just returned.
   */
  public static Class<?> translateFromPrimitive(Class<?> primitive) {
    if(!primitive.isPrimitive())
      return (primitive);

    if(Boolean.TYPE.equals(primitive))
      return (Boolean.class);
    if(Character.TYPE.equals(primitive))
      return (Character.class);
    if(Byte.TYPE.equals(primitive))
      return (Byte.class);
    if(Short.TYPE.equals(primitive))
      return (Short.class);
    if(Integer.TYPE.equals(primitive))
      return (Integer.class);
    if(Long.TYPE.equals(primitive))
      return (Long.class);
    if(Float.TYPE.equals(primitive))
      return (Float.class);
    if(Double.TYPE.equals(primitive))
      return (Double.class);

    throw new RuntimeException("Error translating type:" + primitive);
  }
}