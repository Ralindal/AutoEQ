package autoeq.modules.loot;

public interface LootManager {
  public LootType getLootType(String name);
  public void addLoot(String name, LootType type);
}
