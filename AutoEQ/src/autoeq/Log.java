package autoeq;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.logging.Level;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Log {
  private static PrintStream OUTPUT_STREAM;
  private static PrintStream ERROR_STREAM;

  public static void initialize(OutputStream outputStream, Level level) {
    OUTPUT_STREAM = System.out;
    ERROR_STREAM = System.err;

    System.setOut(new PrintStream(new LogStream(OUTPUT_STREAM, Level.FINE, level, outputStream)));
    System.setErr(new PrintStream(new LogStream(ERROR_STREAM, Level.SEVERE, level, outputStream)));
  }

  public static void initialize(OutputStream outputStream) {
    initialize(outputStream, Level.FINEST);
  }

  public static void initialize() {
    initialize(null, Level.FINEST);
  }

  private static class LogStream extends OutputStream {
    private static final Pattern LOGLINE = Pattern.compile("(\\[([A-Z]+)\\] ?)?(.*)");

    private final PrintStream printStream;
    private final StringBuilder buffer = new StringBuilder();
    private final DateFormat dateFormat = new SimpleDateFormat("dd-MM HH:mm:ss.SSS");
    private final Level defaultLevel;
    private final Level minimumLevel;
    private final PrintStream outputStream;
    private static final String SPACES = String.format("%128s", "");

    public LogStream(PrintStream printStream, Level defaultLevel, Level minimumLevel, OutputStream outputStream) {
      this.printStream = printStream;
      this.defaultLevel = defaultLevel;
      this.minimumLevel = minimumLevel;
      this.outputStream = outputStream != null ? new PrintStream(outputStream) : null;
    }

    @Override
    public synchronized void write(int b) throws IOException {
      try {
        char c = (char)b;

        if(c == 10 || c == 13) {
          if(buffer.length() > 0) {
            Matcher matcher = LOGLINE.matcher(buffer.toString());

            matcher.matches();

            Level level = defaultLevel;

            if(matcher.group(2) != null) {
              level = Level.parse(matcher.group(2));
            }

            if(level.intValue() >= minimumLevel.intValue()) {
              String text = matcher.group(3);
              String end = "";
              int len = text.length();

              if(!Character.isWhitespace(text.charAt(0))) {
                end = SPACES.substring(0, 127 - len % 128) + method();
              }

              String line = "[" + dateFormat.format(new Date()) + "] " + text + end;

              printStream.println(line);
              if(outputStream != null) {
                outputStream.println(line);
              }
            }

            buffer.setLength(0);
          }
        }
        else {
          buffer.append(c);
        }
      }
      catch(Exception e) {
        e.printStackTrace(OUTPUT_STREAM);
      }
    }

    private static String method() {
      StackTraceElement[] elements = new Exception().getStackTrace();

      for(int i = elements.length - 1; i >= 0; i--) {
        if(elements[i].toString().startsWith("java.io.PrintStream.println(") || elements[i].toString().startsWith("automq.eq.EverquestSession.log(")) {
          return " -- " + elements[i + 1].toString();
        }
      }

      return "";
    }
  }
}
