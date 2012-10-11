package autoeq.modules.pull;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


import autoeq.ExpressionEvaluator;
import autoeq.TargetPattern;
import autoeq.ThreadScoped;
import autoeq.effects.Effect;
import autoeq.eq.Command;
import autoeq.eq.Condition;
import autoeq.eq.EverquestSession;
import autoeq.eq.ExpressionRoot;
import autoeq.eq.Me;
import autoeq.eq.Module;
import autoeq.eq.Spawn;
import autoeq.eq.SpawnType;
import autoeq.eq.UserCommand;
import autoeq.ini.Ini;
import autoeq.ini.Section;

import com.google.inject.Inject;

@ThreadScoped
public class PullModule implements Module {
  private static final Pattern NODE = Pattern.compile("([-0-9]+),([-0-9]+)(?:,([-0-9]+))?(?:\\|([0-9]+))?");

  private final EverquestSession session;
  private final List<List<Node>> paths = new ArrayList<List<Node>>();
  private final List<String> conditions;
  private final String validTargets;

  private final Condition earlyExit = new Condition() {
    @Override
    public boolean isValid() {
      return session.getMe().inCombat();
    }
  };

  private long ignoreAgroMillis = 0;
  private Effect pullMethod;
  private String order = "path";
  private int zrange = 50;

  private String pullPath = "";
  private boolean active;

  @Inject
  public PullModule(final EverquestSession session) {
    this.session = session;

    Section section = session.getIni().getSection("Pull");

    if(section != null) {
      active = section.getDefault("Active", "true").toLowerCase().equals("true");
      validTargets = section.getDefault("ValidTargets", "war pal shd mnk rog ber rng bst brd clr shm dru enc mag nec wiz");
      conditions = section.getAll("Condition");
    }
    else {
      active = false;
      validTargets = "";
      conditions = new ArrayList<String>();
    }

    session.addUserCommand("pulloption", Pattern.compile("(status|effect (.*)|ignoreagro ([0-9]+)|order (path|density)|zrange ([0-9]+))"), "(status|effect <method>|ignoreagro <seconds>|order <path|density>|zrange <range>)", new UserCommand() {
      @Override
      public void onCommand(Matcher matcher) {
        String option = matcher.group(1).trim();

        if(option.startsWith("effect ")) {
          pullMethod = session.getEffect(matcher.group(2).trim(), 10);
        }
        if(option.startsWith("ignoreagro ")) {
          ignoreAgroMillis = Integer.parseInt(matcher.group(3).trim()) * 1000;
        }
        if(option.startsWith("order ")) {
          order = matcher.group(4).trim().toLowerCase();
        }
        if(option.startsWith("zrange ")) {
          zrange = Integer.parseInt(matcher.group(5).trim());
        }

        session.echo("==> Pull options: method: " + (pullMethod == null ? "wait for agro" : pullMethod.toString()) + ", ignoreagro: " + ignoreAgroMillis / 1000 + "s, order: " + order + ", zrange: " + zrange);
      }
    });

    session.addUserCommand("pull", Pattern.compile("(.*)"), "[path]", new UserCommand() {
      @Override
      public void onCommand(Matcher matcher) {
        String path = matcher.group(1).trim();

        if(path.length() > 0) {
          try {
            Ini ini = new Ini(new File(session.getGlobalIni().getValue("Global", "Path"), "pullpaths.ini"));

            Section section = ini.getSection(path);

            if(section != null) {
              pullPath = path;
              paths.clear();

              for(String key : section) {
                List<Node> nodes = new ArrayList<Node>();
                paths.add(nodes);

                List<String> pathParts = new ArrayList<String>();

                pathParts.addAll(Arrays.asList(section.get(key).trim().split(" ")));

                String parentPath;

                while((parentPath = section.get(pathParts.get(0))) != null) {
                  pathParts.remove(0);
                  pathParts.addAll(0, Arrays.asList(parentPath.trim().split(" ")));
                }

                for(String s : pathParts) {
                  Matcher m = NODE.matcher(s);

                  if(m.matches()) {
                    Float z = null;
                    int size = 50;

                    if(m.group(3) != null) {
                      z = (float)Integer.parseInt(m.group(3));
                    }
                    if(m.group(4) != null) {
                      size = Integer.parseInt(m.group(4));
                    }

                    nodes.add(new Node(Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)), z, size));
                  }
                }
              }
            }
            else {
              session.doCommand("/echo ==> Pull path '" + path + "' not found.");
            }
          }
          catch(IOException e) {
            session.doCommand("/echo ==> Unable to load pull path.");
          }

        }
        else if(pullPath.length() > 0) {
          active = !active;
        }

        session.echo("==> Pulling is " + (active ? "on" : "off") + ".  Path: " + pullPath);
      }
    });
  }

  @Override
  public List<Command> pulse() {
    Me me = session.getMe();

    if(active && paths.size() > 0 && me.isAlive() && !me.isMoving() && !me.isCasting() && !me.inCombat() && session.tryLockMovement()) {
      try {
        // Possibly ready to pull

        // 1) Check pull path areas to see if there's any valid spawns
        session.delay(1000);

        Pair<List<Node>, List<Spawn>> result = selectPath();

        if(result != null) {
          List<Node> path = result.getA();
          MoveUtils2.moveTowards(session, path.get(0).x, path.get(0).y);

          Path pullPath = new Path(session);

          pullPath.record();

          try {
            try {
              Node endNode = pullAlongPath(path, result.getB());

              pullPath.stopRecording();

              if(!me.inCombat()) {
                if(ignoreAgroMillis == 0) {
                  List<Spawn> spawnsAtNode = getSpawns(endNode);

                  if(spawnsAtNode.size() > 0) {
                    agroSpawn(spawnsAtNode.get(0), endNode);
                  }
                }
              }
            }
            catch(MoveException e) {
              session.log("Problem during pull, attempting to return home: " + e);
            }
            // returnHome();

            session.log("PULL: Returning home");

            pullPath.playbackReverse();
            if(path.size() > 1) {
              session.doCommand(String.format("/face nolook loc %.2f,%.2f", path.get(1).y, path.get(1).x));
            }

            session.echo("PULL: Done");
          }
          finally {
            MoveUtils2.stop(session);
            pullPath.stopRecording();
          }
        }
      }
      finally {
        session.unlockMovement();
      }
    }

    return null;
  }

  private class Pair<A, B> {
    private A a;
    private B b;

    public Pair(A a, B b) {
      this.a = a;
      this.b = b;
    }

    public A getA() {
      return a;
    }

    public B getB() {
      return b;
    }
  }

  private Pair<List<Node>, List<Spawn>> selectPath() {
    if(order.equals("path")) {
      for(List<Node> path : paths) {
        for(Node node : path) {
          List<Spawn> nearbySpawns = getSpawns(node);

          if(nearbySpawns.size() > 0) {
            return new Pair<List<Node>, List<Spawn>>(path, nearbySpawns);
          }
        }
      }
    }
    else if(order.equals("density")) {
      int pathNo = 1;
      List<Node> bestPath = null;
      Set<Spawn> bestSpawns = null;
      int bestDensity = 0;
      int bestPathNo = 0;

      for(List<Node> path : paths) {
        Set<Spawn> spawns = new HashSet<Spawn>();

        for(Node node : path) {
          spawns.addAll(getSpawns(node));
        }

        if(spawns.size() > bestDensity) {
          bestPath = path;
          bestSpawns = spawns;
          bestDensity = spawns.size();
          bestPathNo = pathNo;
        }

        pathNo++;
      }

      if(bestPath != null) {
        session.echo("PULL: Selected path " + bestPathNo + " with density " + bestDensity);
      }

      return new Pair<List<Node>, List<Spawn>>(bestPath, new ArrayList<Spawn>(bestSpawns));
    }

    return null;
  }

  // TODO More Agro methods
  // TODO Avoid stealing mobs
  // TODO Avoid pulling along paths with PC's
  // TODO Pull closest in the bubble
  // TODO Pull closest mob in general instead of first found in pullpath list (optionally)
  // TODO If move gives up because stuck, need to take more action to atleast get home somehow...

//  private void returnHome() {
//    Me me = session.getMe();
//
//    // Check which path to return home along (smart checking here)
//    List<Node> closestPath = null;
//    int closestNodeIndex = -1;
//    int closestDistance = Integer.MAX_VALUE;
//
//    for(List<Node> path : paths) {
//      int index = 0;
//
//      for(Node node : path) {
//        int distance = (int)me.getDistance(node.x, node.y);
//
//        if(distance < closestDistance) {
//          closestDistance = distance;
//          closestPath = path;
//          closestNodeIndex = index;
//        }
//
//        index++;
//      }
//    }
//
//    if(closestPath == null) {
//      session.echo("PULL: returnHome(): Can't return home as there's no paths!");
//      MoveUtils.stop(session);
//      return;
//    }
//
//    session.echo("PULL: returnHome(): Returning home from " + closestPath.get(closestNodeIndex) + " to " + closestPath.get(0));
//
//    for(int index = closestNodeIndex; index >= 0; index--) {
//      Node node = closestPath.get(index);
//      MoveUtils.moveTowards(session, node.x, node.y);
//    }
//
//    MoveUtils.stop(session);
//
//    if(closestPath.size() > 1) {
//      session.doCommand(String.format("/face nolook loc %.2f,%.2f", closestPath.get(1).y, closestPath.get(1).x));
//    }
//  }

  private Node pullAlongPath(List<Node> path, List<Spawn> intendedSpawns) {
    session.echo("PULL: Pulling along path " + path.get(0) + " - " + path.get(path.size() - 1));

    Me me = session.getMe();
    long firstAgroMillis = 0;

    for(Node node : path) {
      MoveUtils2.moveTowards(session, node.x, node.y, ignoreAgroMillis == 0 ? earlyExit : null);

      if(ignoreAgroMillis == 0 && pullMethod != null && pullMethod.isReady()) {
        Spawn closestMatch = null;

        for(Spawn intendedSpawn : intendedSpawns) {
          if((intendedSpawn.inLineOfSight() || intendedSpawn.getDistance() < 50) && intendedSpawn.getDistance() < pullMethod.getSpell().getRange()) {
            if(closestMatch == null || closestMatch.getDistance() > intendedSpawn.getDistance()) {
              closestMatch = intendedSpawn;
            }
          }
        }

        if(closestMatch != null) {
          session.echo("PULL: Agroing " + closestMatch + " on the move, using " + pullMethod);
          session.getMe().activeEffect(pullMethod, closestMatch);
        }
      }

      if(me.inCombat()) {
        if(firstAgroMillis == 0) {
          firstAgroMillis = System.currentTimeMillis();
        }
        if(ignoreAgroMillis == 0 || System.currentTimeMillis() - firstAgroMillis > ignoreAgroMillis) {
          return node;
        }
      }

      if(ignoreAgroMillis == 0) {
        List<Spawn> spawnsAtNode = getSpawns(node);

        if(spawnsAtNode.size() > 0) {
          return node;
        }
      }
    }

    return path.get(path.size() - 1);
  }

  private void agroSpawn(Spawn spawn, Node node) {
    Path returnPath = new Path(session);

    returnPath.record();

    try {
      session.echo("PULL: Agroing " + spawn);
      MoveUtils2.moveTo(session, spawn.getX(), spawn.getY(), earlyExit);

      if(pullMethod == null) {
        session.echo("PULL: Waiting for agro");
        session.delay(5000, earlyExit);
      }
      else {
        session.echo("PULL: Using " + pullMethod);
        if(pullMethod.isReady()) {
          session.getMe().activeEffect(pullMethod, spawn);
        }
      }
//      MoveUtils.moveTowards(session, node.x, node.y);
    }
    finally {
      session.log("PULL: Agroed, returning to last node");
      returnPath.playbackReverse();
    }
  }

  public int getPriority() {
    return 0;
  }

  @Override
  public boolean isLowLatency() {
    return false;
  }

  public List<Spawn> getSpawns(Node node) {
    List<Spawn> spawns = new ArrayList<Spawn>();
    Me me = session.getMe();

    for(Spawn spawn : session.getSpawns()) {
      if(spawn.getDistance(node.x, node.y) < node.size) {
        if((node.z == null && Math.abs(spawn.getZ() - me.getZ()) < zrange) || (node.z != null && Math.abs(spawn.getZ() - node.z) < 10)) {
          if(isValidTarget(spawn)) {
            spawns.add(spawn);
          }
        }
      }
    }

    return spawns;
  }

  private boolean isValidTarget(Spawn spawn) {
    if(spawn.getType() == SpawnType.NPC) {
      if(TargetPattern.isValidTarget(validTargets, spawn)) {
        if(ExpressionEvaluator.evaluate(conditions, new ExpressionRoot(session, spawn, null, null), this)) {
          return true;
        }
      }
    }

    return false;
  }

  private class Node {
    public final float x;
    public final float y;
    public final Float z;
    public final int size;

    public Node(float y, float x, Float z, int size) {
      this.x = x;
      this.y = y;
      this.z = z;
      this.size = size;
    }

    @Override
    public String toString() {
      return "(" + (int)x + "," + (int)y + ":" + size + ")";
    }
  }
}
