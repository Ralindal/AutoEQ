package autoeq.modules.automove;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import autoeq.ThreadScoped;
import autoeq.eq.Command;
import autoeq.eq.EverquestSession;
import autoeq.eq.Me;
import autoeq.eq.Module;

import com.google.inject.Inject;


@ThreadScoped
public class AutoMoveModule implements Module {
  private final EverquestSession session;

  private long lastMillis;

  private final Set<Node> nodes = new HashSet<>();

  private Node startNode;
  private int lastNodeCount;

  @Inject
  public AutoMoveModule(EverquestSession session) {
    this.session = session;
  }

  public int getPriority() {
    return 0;
  }

  @Override
  public int getBurstCount() {
    return 1;
  }

  @Override
  public List<Command> pulse() {
    learn();

    if(System.currentTimeMillis() - lastMillis > 10000 && lastNodeCount != nodes.size() && startNode != null) {
      try {
        try(PrintWriter writer = new PrintWriter(new FileWriter("d:/nodes.ini"))) {

          Set<Path> traversedPaths = new HashSet<>();
          LinkedList<Node> starterNodes = new LinkedList<>();

          starterNodes.add(startNode);

          writer.println("[Nodes]");
          int pathNo = 1;

          while(!starterNodes.isEmpty()) {
            Node node = starterNodes.removeFirst();
            boolean first = true;

            while(node != null) {
              if(!first) {
                writer.print(" ");
              }
              else {
                writer.print("path" + pathNo++ + "=");
              }
              first = false;
              writer.printf("%d,%d|0", node.getY(), node.getX());

              Node currentNode = node;
              node = null;

              for(Node neighbour : currentNode.getNeighbours()) {
                if(!traversedPaths.contains(new Path(neighbour, currentNode))) {
                  if(node == null) {
                    node = neighbour;
                    traversedPaths.add(new Path(neighbour, currentNode));
                  }
                  else {
                    starterNodes.add(currentNode);
                    break;
                  }
                }
              }
            }

            writer.println();
          }
        }
      }
      catch(IOException e) {
      }

      lastMillis = System.currentTimeMillis();
      lastNodeCount = nodes.size();
    }

    return null;
  }

  // private static final int GRID_SIZE = 2;

  private Node lastNode;

  private void learn() {
    Me me = session.getMe();

    Node newNode = new Node((int)((me.getX() + 1) / 2) * 2, (int)((me.getY() + 1) / 2) * 2, (int)((me.getZ() + 1) / 2) * 2);
    boolean addNode = true;

    for(Node node : nodes) {
      float distSq = newNode.distanceSquared(node);

      if(distSq < 4) {
        addNode = false;
      }
      if(distSq < 4) {
        if(lastNode != node && lastNode.distanceSquared(node) <= 8) {
          lastNode.addNeighbour(node);
        }
        lastNode = node;
        return;
      }
    }

    if(addNode) {
      nodes.add(newNode);

      if(startNode == null) {
        startNode = newNode;
      }

      if(lastNode != null) {
        if(lastNode.distanceSquared(newNode) <= 8) {
          lastNode.addNeighbour(newNode);
        }
      }

      lastNode = newNode;
    }
  }

  public static class Path {
    private final Node a;
    private final Node b;

    public Path(Node a, Node b) {
      if(a.hashCode() < b.hashCode()) {
        this.a = a;
        this.b = b;
      }
      else {
        this.a = b;
        this.b = a;
      }
    }

    @Override
    public boolean equals(Object obj) {
      if(!(obj instanceof Path)) {
        return false;
      }

      Path p = (Path)obj;

      if(p.a.equals(a) && p.b.equals(b)) {
        return true;
      }

      return false;
    }

    @Override
    public int hashCode() {
      return a.hashCode() ^ b.hashCode();
    }
  }
}
