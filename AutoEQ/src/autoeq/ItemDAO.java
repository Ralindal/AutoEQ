package autoeq;

public interface ItemDAO {
  Item getItem(String name);
  Item getItem(int id);
  void addItem(Item item);
}
