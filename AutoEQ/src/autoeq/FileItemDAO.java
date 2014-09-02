package autoeq;

import java.io.BufferedWriter;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.LineNumberReader;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

public class FileItemDAO implements ItemDAO {
  private final Map<String, Item> items = new LinkedHashMap<>();
  private final Map<Integer, Item> itemsByID = new HashMap<>();

  private volatile boolean changed;

  public FileItemDAO() throws IOException {

    /*
     * Read item data
     */

    try {
      try(LineNumberReader reader = new LineNumberReader(new FileReader("items.txt"))) {
        String line;

        while((line = reader.readLine()) != null) {
          String[] fields = line.split("(?<!\\\\)\\^");

          Item item = new Item(Integer.parseInt(fields[0]), fields[1], Integer.parseInt(fields[2]), fields[3].equals("T"), fields[4].equals("T"), fields[5].equals("T"));

          addItemToMaps(item);
        }
      }
    }
    catch(FileNotFoundException e) {
    }

    /*
     * Start save Thread
     */

    new Thread() {
      {
        setDaemon(true);
      }

      @Override
      public void run() {
        for(;;) {
          try {
            Thread.sleep(60000);

            if(changed) {
              changed = false;

              try(PrintWriter writer = new PrintWriter(new BufferedWriter(new FileWriter("items.txt.tmp")))) {
                for(Item item : items.values()) {
                  writer.println(item.getId() + "^" + item.getName() + "^" + item.getHitPoints() + "^" + (item.isNoDrop() ? "T" : "F") + "^" + (item.isLore() ? "T" : "F") + "^" + (item.isAttunable() ? "T" : "F"));
                }
              }

              if(Files.exists(Paths.get("items.txt"))) {
                Files.move(Paths.get("items.txt"), Paths.get("items.txt.bak"), StandardCopyOption.REPLACE_EXISTING);
              }
              Files.move(Paths.get("items.txt.tmp"), Paths.get("items.txt"));
            }
          }
          catch(IOException e) {
            System.err.println("Error writing items file: " + e);
          }
          catch(InterruptedException e) {
            System.err.println("Item write thread interrupted: " + e);
          }
        }
      }
    }.start();
  }

  @Override
  public Item getItem(String name) {
    return items.get(name.toLowerCase());
  }

  @Override
  public Item getItem(int id) {
    return itemsByID.get(id);
  }

  @Override
  public void addItem(Item item) {
    if(getItem(item.getName()) == null) {
      System.out.println("FileItemDAO - Adding item: " + item.getName());

      addItemToMaps(item);
      changed = true;
    }
  }

  private void addItemToMaps(Item item) {
    items.put(item.getName().toLowerCase(), item);
    itemsByID.put(item.getId(), item);
  }
}
