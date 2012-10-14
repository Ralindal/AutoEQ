package autoeq.modules.automove;

import java.util.HashSet;
import java.util.Set;

public class Node {
  private final int x;
  private final int y;
  private final int z;

  private final Set<Node> neighbours = new HashSet<>();

  public Node(int x, int y, int z) {
    this.x = x;
    this.y = y;
    this.z = z;
  }

  public Set<Node> getNeighbours() {
    return neighbours;
  }

  public int getX() {
    return x;
  }

  public int getY() {
    return y;
  }

  public int getZ() {
    return z;
  }

  public int distanceSquared(Node node) {
    int dx = node.x - x;
    int dy = node.y - y;
    int dz = node.z - z;
    return dx * dx + dy * dy + dz * dz;
  }

  public void addNeighbour(Node node) {
    if(this == node) {
      throw new IllegalArgumentException("Can't add yourself as neighbour: " + this);
    }
    neighbours.add(node);
  }

  @Override
  public boolean equals(Object obj) {
    if(!(obj instanceof Node)) {
      return false;
    }

    Node n = (Node)obj;

    if(n.x == x && n.y == y && n.z == z) {
      return true;
    }

    return false;
  }

  @Override
  public int hashCode() {
    return Float.floatToIntBits(x) ^ Float.floatToIntBits(y) ^ Float.floatToIntBits(z);
  }
}
