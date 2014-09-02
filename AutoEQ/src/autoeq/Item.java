package autoeq;

public class Item {
  private final String name;
  private final int id;
  private final int hitPoints;
  private final boolean nodrop;
  private final boolean lore;
  private final boolean attunable;

  public Item(int id, String name, int hitPoints, boolean nodrop, boolean lore, boolean attunable) {
    this.id = id;
    this.name = name;
    this.hitPoints = hitPoints;
    this.nodrop = nodrop;
    this.lore = lore;
    this.attunable = attunable;
  }

  public int getId() {
    return id;
  }

  public String getName() {
    return name;
  }

  public int getHitPoints() {
    return hitPoints;
  }

  public boolean isNoDrop() {
    return nodrop;
  }

  public boolean isLore() {
    return lore;
  }

  public boolean isAttunable() {
    return attunable;
  }
}
