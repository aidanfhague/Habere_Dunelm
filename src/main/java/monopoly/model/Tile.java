package monopoly.model;

public class Tile {
    private final int index;     // 0..39
    private final String name;
    private final TileType type;

    public Tile(int index, String name, TileType type) {
        this.index = index;
        this.name = name;
        this.type = type;
    }

    public int getIndex() { return index; }
    public String getName() { return name; }
    public TileType getType() { return type; }
}

