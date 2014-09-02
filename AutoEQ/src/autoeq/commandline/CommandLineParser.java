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
        String hint = valueAnn.hint();
        String parameterPart = "";

        if(field.getType() == String.class) {
          parameterPart = hint.isEmpty() ? "<text>" : "<" + hint + ">";
        }
        else if(field.getType() == int.class) {
          parameterPart = hint.isEmpty() ? "<number>" : "<" + hint + ">";
        }
        else if(field.getType() == double.class) {
          parameterPart = hint.isEmpty() ? "<fraction>" : "<" + hint + ">";
        }
        else if(field.getType() == boolean.class) {
          parameterName = parameterName + "|no" + parameterName;
        }
        else if(field.getType().isEnum()) {
          for(Object constant : field.getType().getEnumConstants()) {
            if(!parameterPart.isEmpty()) {
              parameterPart += "|";
            }
            parameterPart += constant;
          }
          parameterPart = parameterPart.toLowerCase();
        }
        else {
          throw new RuntimeException("Unsupported type: " + field.getType());
        }

        String regex = null;

        if(valueAnn.defaultParameter()) {
          regex = "[" + parameterPart + "]";
        }
        else if(parameterPart.isEmpty()) {
          regex = "[" + parameterName + "]";
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

        parameterName = parameterName.toLowerCase();

        String parameterPart = "";

        if(field.getType() == String.class) {
          String doubleQuoted = "\"[^\"\\\\]*(?:\\\\.[^\"\\\\]*)*\"";
          String singleQuoted = "'[^'\\\\]*(?:\\\\.[^'\\\\]*)*'";
          String unquoted = "[^\\\\ ]*(?:\\\\.[^\\\\ ]*)*";

          parameterPart = "(?:" + doubleQuoted + "|" + singleQuoted + "|" + unquoted + ")";
        }
        else if(field.getType() == int.class) {
          parameterPart = "-?[0-9]+";
        }
        else if(field.getType() == double.class) {
          parameterPart = "-?[0-9]+(?:\\.[0-9]+)";
        }
        else if(field.getType() == boolean.class) {
          parameterPart = "";
        }
        else if(field.getType().isEnum()) {
          for(Object constant : field.getType().getEnumConstants()) {
            if(!parameterPart.isEmpty()) {
              parameterPart += "|";
            }
            parameterPart += constant;
          }
          parameterPart = "(?:" + parameterPart + ")(?=(?:\\s|$))";  // followed by white-space or eol
        }
        else {
          throw new RuntimeException("Unsupported type: " + field.getType());
        }

        String regex = null;

        if(valueAnn.defaultParameter()) {
          regex = "()(" + parameterPart + ")";
          parameterName = "";
        }
        else if(parameterPart.isEmpty()) {
          regex = "(" + parameterName  + "|no" + parameterName + ")()";
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
        String parameterName = matcher.group(groupNo);

        if(parameterName != null) {
          parameterName = parameterName.toLowerCase();
        }

        CommandLineParser.Group group = groups.get(parameterName);

        if(group == null && parameterName != null && parameterName.startsWith("no") && parameterName.length() > 2) {
          group = groups.get(parameterName.substring(2));

          if(group != null && group.field.getType() != boolean.class) {
            group = null;
          }
        }

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
            else if(group.field.getType() == boolean.class) {
              group.field.setBoolean(configuration, groups.get(parameterName) != null);
            }
            else if(group.field.getType().isEnum()) {
              group.field.set(configuration, valueOfEnum(group.field.getType(), value));
            }
            else if(group.field.getType() == String.class) {
              value = value.replaceAll("\\\\(.)", "$1");

              if(value.startsWith("'") || value.startsWith("\"")) {
                value = value.substring(1, value.length() - 1);
              }

              group.field.set(configuration, value);
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