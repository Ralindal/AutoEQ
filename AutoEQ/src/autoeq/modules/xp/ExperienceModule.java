package autoeq.modules.xp;

import java.util.LinkedList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import autoeq.ThreadScoped;
import autoeq.eq.ChatListener;
import autoeq.eq.Command;
import autoeq.eq.EverquestSession;
import autoeq.eq.Me;
import autoeq.eq.Module;

import com.google.inject.Inject;


@ThreadScoped
public class ExperienceModule implements Module, ChatListener {
  private static final Pattern XP_PATTERN = Pattern.compile("(You gain .*experience!!|You gained raid experience!)");

  private final EverquestSession session;
  private final LinkedList<Point> points = new LinkedList<>();
  private int killCount;

  @Inject
  public ExperienceModule(EverquestSession session) {
    this.session = session;

    session.addChatListener(this);
  }

  public int getPriority() {
    return 9;
  }

  @Override
  public List<Command> pulse() {
    return null;
  }

  @Override
  public Pattern getFilter() {
    return XP_PATTERN;
  }

  @Override
  public void match(Matcher matcher) {
    Me me = session.getMe();

    points.addFirst(new Point(me.getAAExperience() + me.getAACount() + me.getAASaved(), me.getExperience() + me.getLevel()));
    killCount++;

    if(points.size() > 200) {
      points.removeLast();
    }

    long currentTime = System.currentTimeMillis();

    float xp1hAgo = 0;
    float aaxp10mAgo = 0;
    float aaxp1hAgo = 0;
    int kills10m = 0;
    int kills1h = 0;
    long time10m = 1;
    long time1h = 1;

    for(Point point : points) {
      if(point.time > currentTime - 10 * 60 * 1000) {
        aaxp10mAgo = point.aa;
        kills10m++;
        time10m = currentTime - point.time;
      }
      if(point.time > currentTime - 60 * 60 * 1000) {
        xp1hAgo = point.xp;
        aaxp1hAgo = point.aa;
        kills1h++;
        time1h = currentTime - point.time;
      }
    }

    xp1hAgo = me.getExperience() + me.getLevel() - xp1hAgo;
    aaxp10mAgo = me.getAAExperience() + me.getAACount() + me.getAASaved() - aaxp10mAgo;
    aaxp1hAgo = me.getAAExperience() + me.getAACount() + me.getAASaved() - aaxp1hAgo;

    // System.err.println("Time: " + time10m + "; " + time1h + "; " + aaxp10mAgo);
    // Time: 259391; 259391; -0.4423828

    session.echo(
      String.format(
        "XP: L%d (%4.1f%%) @ %5.3f/h - AA %d+%d (%.0f%%) @ %4.1f/h %4.2f/10m - Cnt %d @ %4.0f/h %4.1f/10m",
        me.getLevel(),
        me.getExperience() * 100,
        xp1hAgo / time1h * 60 * 60 * 1000,
        me.getAACount(),
        me.getAASaved(),
        me.getAAExperience() * 100,
        aaxp1hAgo / time1h * 60 * 60 * 1000,
        aaxp10mAgo / time10m * 10 * 60 * 1000,
        killCount,
        (float)(kills1h - 1) / time1h * 60 * 60 * 1000,
        (float)(kills10m - 1) / time10m * 10 * 60 * 1000
      )
    );
  }

  @Override
  public boolean isLowLatency() {
    return false;
  }

  private class Point {
    public final float aa;
    public final float xp;
    public final long time;

    public Point(float aa, float xp) {
      this.aa = aa;
      this.xp = xp;
      this.time = System.currentTimeMillis();
    }
  }
}
