package autoeq.commandline;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class CommandLineParser {
    private static class Group {
      private final Field field;

      public Group(Field field) {
        this.field = field;
      }
    }

    public static String getHelpString(Class<?> configurationClass) {
      String helpLine = "";

      for(Field field : configurationClass.getDeclaredFields()) {
        Parameter valueAnn = field.getAnnotation(Parameter.class);

        if(valueAnn != null) {
          String parameterName = valueAnn.name().isEmpty() ? field.getName() : valueAnn.name();
          String parameterPart = "";

          if(field.getType() == String.class) {
            parameterPart = "<text>";
          }
          else if(field.getType() == int.class) {
            parameterPart = "<number>";
          }
          else if(field.getType() == double.class) {
            parameterPart = "<fraction>";
          }
          else if(field.getType().isEnum()) {
            for(Object constant : field.getType().getEnumConstants()) {
              if(!parameterPart.isEmpty()) {
                parameterPart += "|";
              }
              parameterPart += constant;
            }
            parameterPart = ("(" + parameterPart + ")").toLowerCase();
          }
          else {
            throw new RuntimeException("Unsupported type: " + field.getType());
          }

          String regex = null;

          if(valueAnn.defaultParameter()) {
            regex = "[" + parameterPart + "]";
          }
          else {
            regex = "[" + parameterName + " " + parameterPart + "]";
          }

          if(!helpLine.isEmpty()) {
            helpLine += " ";
          }
          helpLine += regex;
        }
      }

      return helpLine;
    }

    public static void parse(Object configuration, String input) {
      Map<String, CommandLineParser.Group> groups = new HashMap<>();

      String fullRegex = "";

      for(Field field : configuration.getClass().getDeclaredFields()) {
        Parameter valueAnn = field.getAnnotation(Parameter.class);

        if(valueAnn != null) {
          String parameterName = valueAnn.name().isEmpty() ? field.getName() : valueAnn.name();

          String parameterPart = "";

          if(field.getType() == String.class) {
            parameterPart = "[^ ]+";
          }
//          else if(field.getType() == boolean.class) {
//            regex += "()";
//          }
          else if(field.getType() == int.class) {
            parameterPart = "-?[0-9]+";
          }
          else if(field.getType() == double.class) {
            parameterPart = "-?[0-9]+(?:\\.[0-9]+)";
          }
          else if(field.getType().isEnum()) {
            for(Object constant : field.getType().getEnumConstants()) {
              if(!parameterPart.isEmpty()) {
                parameterPart += "|";
              }
              parameterPart += constant;
            }
            parameterPart = "(?:" + parameterPart + ")";
          }
          else {
            throw new RuntimeException("Unsupported type: " + field.getType());
          }

          String regex = null;

          if(valueAnn.defaultParameter()) {
            regex = "()(" + parameterPart + ")";
            parameterName = "";
          }
          else {
            regex = "(" + parameterName + ") (" + parameterPart + ")";
          }

          if(!fullRegex.isEmpty()) {
            fullRegex += "|";
          }
          fullRegex += regex;
          groups.put(parameterName, new Group(field));
        }
      }

      Matcher matcher = Pattern.compile("(?i)(?:" + fullRegex + ")\\s*").matcher(input);

      while(matcher.find()) {
        for(int groupNo = 1; groupNo <= matcher.groupCount(); groupNo += 2) {
          CommandLineParser.Group group = groups.get(matcher.group(groupNo));

          if(group != null) {
            group.field.setAccessible(true);
            try {
              String value = matcher.group(groupNo + 1);

              if(group.field.getType() == int.class) {
                group.field.setInt(configuration, Integer.parseInt(value));
              }
              else if(group.field.getType() == double.class) {
                group.field.setDouble(configuration, Double.parseDouble(value));
              }
              else if(group.field.getType().isEnum()) {
                group.field.set(configuration, valueOfEnum(group.field.getType(), value));
              }
              else {
                group.field.set(configuration, value);
              }
            }
            catch(IllegalArgumentException | IllegalAccessException e) {
              throw new RuntimeException(e);
            }
          }
        }
      }
    }

    @SuppressWarnings("unchecked")
    private static <T extends Enum<T>> T valueOfEnum(Class<?> type, String value) {
      try {
        return Enum.valueOf((Class<T>)type, value);
      }
      catch(IllegalArgumentException e) {
        return Enum.valueOf((Class<T>)type, value.toUpperCase());
      }
    }
  }