package autoeq.modules.pull;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import autoeq.ExpressionEvaluator;
import autoeq.TargetPattern;
import autoeq.ThreadScoped;
import autoeq.commandline.CommandLineParser;
import autoeq.effects.Effect;
import autoeq.effects.Effect.Type;
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
import autoeq.modules.camp.CampModule;
import autoeq.modules.pull.PullConf.Mode;
import autoeq.modules.pull.PullConf.Order;
import autoeq.modules.pull.PullConf.State;

import com.google.inject.Inject;

@ThreadScoped
public class PullModule implements Module {
  private static final Pattern NODE = Pattern.compile("([-0-9]+),([-0-9]+)(?:,([-0-9]+))?(?:\\|([0-9]+))?");

  private final EverquestSession session;
  private final List<List<Node>> paths = new ArrayList<>();
  private final List<String> conditions;
  private final String validTargets;
  private final String prePullBandolier;
  private final String postPullBandolier;

  private final Condition earlyExit = new Condition() {
    @Override
    public boolean isValid() {
      return session.getMe().inCombat();
    }
  };

  private Effect pullMethod;
  private PullConf conf = new PullConf();

  private final CampModule campModule;

  @Inject
  public PullModule(final EverquestSession session, CampModule campModule) {
    this.session = session;
    this.campModule = campModule;

    Section section = session.getIni().getSection("Pull");

    if(section != null) {
      validTargets = section.getDefault("ValidTargets", "war pal shd mnk rog ber rng bst brd clr shm dru enc mag nec wiz");
      conditions = section.getAll("Condition");  // Conditions that must be satisfied before starting a pull
      prePullBandolier = section.get("PrePullBandolier");
      postPullBandolier = section.get("PostPullBandolier");
    }
    else {
      validTargets = "";
      conditions = new ArrayList<>();
      prePullBandolier = null;
      postPullBandolier = null;
    }

    session.addUserCommand("pull", Pattern.compile(".+"), CommandLineParser.getHelpString(PullConf.class), new UserCommand() {
      @Override
      public void onCommand(Matcher matcher) {
        PullConf newConf = new PullConf(conf);

        CommandLineParser.parse(newConf, matcher.group(0));

        conf = newConf;

        if(conf.getPath() != null && conf.getPath().trim().length() > 0) {
          loadPullPath(conf.getPath());
        }

        pullMethod = conf.getEffect() != null && conf.getEffect().trim().length() > 0 ? session.getEffect(conf.getEffect(), 10) : null;

        session.echo(String.format("==> Pull %s [%s]: method: %s, ignoreagro: %ds, minimum: %d, order: %s, zrange: %d, nameds: %s%s",
          conf.getState().toString(),
          conf.getMode().toString() + (conf.getMode() == PullConf.Mode.PATH ? ": " + conf.getPath() : ""),
          (conf.getEffect() == null ? "wait for agro" : conf.getEffect()),
          conf.getIgnoreAgro(),
          conf.getMinimum(),
          conf.getOrder(),
          conf.getZRange(),
          conf.isPullNameds() ? "pull" : "leave",
          conf.getIgnoredHostiles() == null || conf.getIgnoredHostiles().isEmpty() ? "" : " ignoredHostiles: " + conf.getIgnoredHostiles()
        ));
      }
    });
  }

  private void loadPullPath(String path) {
    try {
      Ini ini = new Ini(new File(session.getGlobalIni().getValue("Global", "Path"), "pullpaths.ini"));

      Section section = ini.getSection(path);

      if(section != null) {
        paths.clear();

        for(String key : section) {
          List<Node> nodes = new ArrayList<>();
          paths.add(nodes);

          List<String> pathParts = new ArrayList<>();

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
        session.echo("==> Pull path '" + path + "' not found.");
      }
    }
    catch(IOException e) {
      session.echo("==> Unable to load pull path: " + e.getMessage());
    }
  }

  @Override
  public List<Command> pulse() {
    final Me me = session.getMe();

    if(conf.getState() == State.ON && paths.size() > 0 && me.isAlive()) {
      checkPaths();  // suspends pulling of paths
    }

    if(conf.getState() == State.ON && ((paths.size() > 0 && conf.getMode() == Mode.PATH) || (conf.getMode() == Mode.CAMP)) && me.isAlive() && !me.isMoving() && !me.isCasting() && me.getExtendedTargetCount() == 0) {
      if(ExpressionEvaluator.evaluate(conditions, new ExpressionRoot(session, null, null, null, null), this)) {
        Command command = new Command() {
          @Override
          public double getPriority() {
            return 900;
          }

          @Override
          public boolean execute(final EverquestSession session) {
            if(session.tryLockMovement()) {
              try {
                // Possibly ready to pull

                // 1) Check pull path areas to see if there's any valid spawns

                if(conf.getMode() == Mode.PATH) {
                  session.delay(1000);

                  List<Node> selectedPath = selectPath();

                  if(selectedPath != null) {
                    if(prePullBandolier != null) {
                      session.doCommand("/bandolier activate " + prePullBandolier);
                    }

                    try {
                      MoveUtils2.moveTowards(session, selectedPath.get(0).x, selectedPath.get(0).y);

                      Path pullPath = new Path(session);

                      pullPath.record();

                      try {
                        try {
                          Node endNode = pullAlongPath(selectedPath);

                          pullPath.stopRecording();

                          if(!me.inCombat()) {
                            if(conf.getIgnoreAgro() == 0 && conf.getMinimum() == 0) {
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
                        if(selectedPath.size() > 1) {
                          session.doCommand(String.format("/face nolook loc %.2f,%.2f", selectedPath.get(1).y, selectedPath.get(1).x));
                          session.delay(500);  // Time to finish face otherwise a spell cast might stop the turning
                        }

                        // session.echo("PULL: Done");
                      }
                      finally {
                        MoveUtils2.stop(session);
                        pullPath.stopRecording();
                      }
                    }
                    finally {
                      if(postPullBandolier != null) {
                        session.doCommand("/bandolier activate " + postPullBandolier);
                      }
                    }
                  }
                }
                else {
                  final Spawn spawn = getNearestMatchingSpawn(campModule.getCampX(), campModule.getCampY(), null, campModule.getCampSize());

                  if(spawn != null && spawn.getDistance() > 25) {
                    MoveUtils2.moveTo(session, spawn, new Condition() {
                      @Override
                      public boolean isValid() {
                        return !spawn.isAlive() || session.getMe().inCombat() || spawn.getDistance() < 20;
                      }
                    });
                  }
                }
              }
              finally {
                session.unlockMovement();
              }

              return true;
            }

            return false;
          }
        };

        return Collections.singletonList(command);
      }
    }

    return null;
  }

  private final Map<List<Node>, Long> suspendedPaths = new HashMap<>();

  private void checkPaths() {
    int pathNo = 0;

    for(List<Node> path : paths) {
      for(Node node : path) {
        List<Spawn> nearbySpawns = getHostilePCs(node);
        Long suspendTimeOut = suspendedPaths.get(path);

        if(nearbySpawns.size() > 0 && (suspendTimeOut == null || suspendTimeOut < System.currentTimeMillis() + 2 * 60 * 1000)) {
          String nearbySpawnNames = "";

          for(Spawn nearbySpawn : nearbySpawns) {
            if(!nearbySpawnNames.isEmpty()) {
              nearbySpawnNames += ", ";
            }

            nearbySpawnNames += nearbySpawn.getName();
          }

          session.echo("PULL: Suspended path " + pathNo + " for 180s because of: " + nearbySpawnNames);
          suspendedPaths.put(path, System.currentTimeMillis() + 3 * 60 * 1000);
        }
      }

      pathNo++;
    }
  }

  private boolean isPathSuspended(List<Node> path) {
    Long suspendTimeOut = suspendedPaths.get(path);

    return suspendTimeOut != null && suspendTimeOut > System.currentTimeMillis();
  }

  private List<Node> selectPath() {
    if(conf.getOrder() == Order.PATH) {
      for(List<Node> path : paths) {
        if(!isPathSuspended(path)) {
          if(!conf.isPullNameds()) {
            List<Node> shortenedPath = new ArrayList<>();

            outer:
            for(Node node : path) {
              for(Spawn spawn : getSpawns(node, 50)) {
                if(spawn.isNamedMob()) {
                  break outer;
                }
              }

              shortenedPath.add(node);
            }

            path = shortenedPath;
          }

          for(Node node : path) {
            List<Spawn> nearbySpawns = getSpawns(node);

            if(nearbySpawns.size() > 0) {
              return path;
            }
          }
        }
      }
    }
    else if(conf.getOrder() == Order.NEAREST) {
      List<Node> bestPath = null;
      List<Spawn> bestSpawns = null;
      double closestDistance = Double.MAX_VALUE;

      for(List<Node> path : paths) {
        if(!isPathSuspended(path)) {
          if(!conf.isPullNameds()) {
            List<Node> shortenedPath = new ArrayList<>();

            outer:
            for(Node node : path) {
              for(Spawn spawn : getSpawns(node, 50)) {
                if(spawn.isNamedMob()) {
                  break outer;
                }
              }

              shortenedPath.add(node);
            }

            path = shortenedPath;
          }

          for(Node node : path) {
            List<Spawn> nearbySpawns = getSpawns(node);

            if(nearbySpawns.size() > 0) {
              for(Spawn spawn : nearbySpawns) {
                if(spawn.getDistance() < closestDistance) {
                  bestPath = path;
                  bestSpawns = nearbySpawns;
                  closestDistance = spawn.getDistance();
                }
              }

              break;
            }
          }
        }
      }

      return bestSpawns == null ? null : bestPath;
    }
    else if(conf.getOrder() == Order.DENSITY) {
      int pathNo = 1;
      List<Node> bestPath = null;
      Set<Spawn> bestSpawns = null;
      int bestDensity = 0;
      int bestPathNo = 0;

      for(List<Node> path : paths) {
        if(!isPathSuspended(path)) {
          Set<Spawn> spawns = new HashSet<>();

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
      }

      if(bestPath != null) {
        session.echo("PULL: Selected path " + bestPathNo + " with density " + bestDensity);
      }

      return bestSpawns == null ? null : bestPath;
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

  private Node pullAlongPath(List<Node> path) {
    session.echo("PULL: Pulling along path " + path.get(0) + " - " + path.get(path.size() - 1));

    final Me me = session.getMe();

    long firstAgroMillis = 0;

    for(int i = 0; i < path.size(); i++) {
      Node node = path.get(i);
      Spawn closestSpawn = null;

      for(int j = i; j < path.size(); j++) {
        for(Spawn spawn : getSpawns(path.get(j))) {
          if(closestSpawn == null || spawn.getDistance() < closestSpawn.getDistance()) {
            closestSpawn = spawn;
          }
        }
      }

      if(closestSpawn == null) {
        session.echo("PULL: Aborting, no spawns available (anymore)");
        return node;
      }
      else {
        if(me.getTarget() != closestSpawn && closestSpawn.getDistance() < 250) {
          session.doCommand("/target id " + closestSpawn.getId());
        }
      }

      Condition condition = null;
      final Spawn finalClosestSpawn = closestSpawn;

      if(conf.getIgnoreAgro() == 0 && conf.getMinimum() == 0) {
        condition = new Condition() {
          private int conditionMet;
          private int lineOfSight;

          @Override
          public boolean isValid() {
            if(conditionMet > 1) {
              return true;
            }

            if(me.getTarget() != finalClosestSpawn) {
              if(finalClosestSpawn.getDistance() < 350) {
                session.doCommand("/target id " + finalClosestSpawn.getId());
              }
            }
            else {
              if(finalClosestSpawn.inLineOfSight()) {
                lineOfSight++;
              }
              else {
                lineOfSight = 0;
              }
              // Already pre-targetted, see if we can agro it
              if(lineOfSight > 1 && agroFromDistanceOnTheMove(finalClosestSpawn)) {
                conditionMet++;
                if(conditionMet > 1) {
                  return true;
                }
              }
            }

            return session.getMe().inCombat();
          }
        };
      }

      MoveUtils2.moveTowards(session, node.x, node.y, condition);

//      if(ignoreAgroMillis == 0 && pullMethod != null && pullMethod.isReady()) {
//        Spawn closestMatch = null;
//
//        for(Spawn intendedSpawn : intendedSpawns) {
//          if((intendedSpawn.inLineOfSight() || intendedSpawn.getDistance() < 50) && intendedSpawn.getDistance() < pullMethod.getSpell().getRange()) {
//            if(closestMatch == null || closestMatch.getDistance() > intendedSpawn.getDistance()) {
//              closestMatch = intendedSpawn;
//            }
//          }
//        }
//
//        if(closestMatch != null) {
////          session.echo("PULL: Agroing " + closestMatch + " on the move, using " + pullMethod);
//          session.getMe().activateEffect(pullMethod, closestMatch);
//        }
//      }

      if(me.inCombat() || (condition != null && condition.isValid())) {
        if(firstAgroMillis == 0) {
          firstAgroMillis = System.currentTimeMillis();
        }
        if((conf.getIgnoreAgro() == 0 && conf.getMinimum() == 0)
            || (conf.getIgnoreAgro() > 0 && System.currentTimeMillis() - firstAgroMillis > conf.getIgnoreAgro() * 1000)
            || (conf.getMinimum() > 0 && me.getExtendedTargetCount() >= conf.getMinimum())) {
          return node;
        }
      }

      if(conf.getIgnoreAgro() == 0 && conf.getMinimum() == 0) {
        List<Spawn> spawnsAtNode = getSpawns(node);

        if(spawnsAtNode.size() > 0) {
          return node;
        }
      }
    }

    return path.get(path.size() - 1);
  }

  private long aggroLockout;

  private boolean agroFromDistanceOnTheMove(final Spawn spawn) {
    if(aggroLockout < System.currentTimeMillis()) {
      if(pullMethod != null && pullMethod.isReady()) {
        if((spawn.inLineOfSight() || spawn.getDistance() < 50) && spawn.getDistance() < pullMethod.getRange()) {
          aggroLockout = System.currentTimeMillis() + (pullMethod.getType() == Type.COMMAND ? 1500 : 250);
          session.getMe().activateEffect(null, pullMethod, new ArrayList<Spawn>() {{
            add(spawn);
          }});

          while(pullMethod.getType() != Type.COMMAND && !session.getMe().isBard() && session.getMe().isCasting() && pullMethod.getCastTime() > 0) {
            session.delayUntilUpdate();
          }

          if(pullMethod.getCastTime() > 0) {
            session.delay(500);
            session.echo("PULL: Cast done");
          }

          if(pullMethod.getType() == Type.COMMAND) {
            return true;
          }
        }
      }
    }

    return false;
  }

  private void agroSpawn(final Spawn spawn, @SuppressWarnings("unused") Node node) {
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
          session.getMe().activateEffect(null, pullMethod, new ArrayList<Spawn>() {{
            add(spawn);
          }});
        }
      }
//      MoveUtils.moveTowards(session, node.x, node.y);
    }
    finally {
      session.log("PULL: Agroed, returning to last node");
      returnPath.playbackReverse();
    }
  }

  @Override
  public int getBurstCount() {
    return 8;
  }

  public List<Spawn> getHostilePCs(Node node) {
    List<Spawn> spawns = new ArrayList<>();
    Me me = session.getMe();

    for(Spawn spawn : session.getSpawns()) {
      if(spawn.getType() == SpawnType.PC && !spawn.isAlly()) {
        if(spawn.getDistance(node.x, node.y) < node.size + 100) {
          if((node.z == null && Math.abs(spawn.getZ() - me.getZ()) < conf.getZRange()) || (node.z != null && Math.abs(spawn.getZ() - node.z) < 20)) {
            if(conf.getIgnoredHostiles() == null || conf.getIgnoredHostiles().isEmpty() || !spawn.getName().matches(conf.getIgnoredHostiles())) {
              spawns.add(spawn);
            }
          }
        }
      }
    }

    return spawns;
  }

  /**
   * Returns the nearest spawn to your location, within a defined circular area.
   *
   * @param x center x
   * @param y center y
   * @param z center z
   * @param radius radius
   * @return nearest spawn to your location, or null if no matching spawns found
   */
  public Spawn getNearestMatchingSpawn(float x, float y, Float z, int radius) {
    Spawn closestSpawn = null;
    Me me = session.getMe();

    for(Spawn spawn : session.getSpawns()) {
      if(spawn.getDistance(x, y) < radius) {
        if((z == null && Math.abs(spawn.getZ() - me.getZ()) < conf.getZRange()) || (z != null && Math.abs(spawn.getZ() - z) < 20)) {
          if(isValidTarget(spawn) && !spawn.isPullIgnored()) {
            if(closestSpawn == null || spawn.getDistance() < closestSpawn.getDistance()) {
              closestSpawn = spawn;
            }
          }
        }
      }
    }

    return closestSpawn;
  }

  public List<Spawn> getSpawns(Node node, int extraRadius) {
    List<Spawn> spawns = new ArrayList<>();
    Me me = session.getMe();

    for(Spawn spawn : session.getSpawns()) {
      if(spawn.getDistance(node.x, node.y) < node.size + extraRadius) {
        if((node.z == null && Math.abs(spawn.getZ() - me.getZ()) < conf.getZRange()) || (node.z != null && Math.abs(spawn.getZ() - node.z) < 20)) {
          if(isValidTarget(spawn) && !spawn.isPullIgnored()) {
            spawns.add(spawn);
          }
        }
      }
    }

    return spawns;
  }

  public List<Spawn> getSpawns(Node node) {
    return getSpawns(node, 0);
  }

  private boolean isValidTarget(Spawn spawn) {
    if(spawn.getType() == SpawnType.NPC) {
      if(TargetPattern.isValidTarget(validTargets, spawn)) {
        return true;
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
