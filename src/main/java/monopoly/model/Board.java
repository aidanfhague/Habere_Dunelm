package monopoly.model;

import java.util.List;

public class Board {
    public static final int SIZE = 40;

    private final List<Tile> tiles;

    public Board(List<Tile> tiles) {
        if (tiles.size() != SIZE) {
            throw new IllegalArgumentException("Board must have exactly " + SIZE + " tiles.");
        }
        this.tiles = List.copyOf(tiles);
    }

    public Tile tileAt(int position) {
        int idx = Math.floorMod(position, SIZE);
        return tiles.get(idx);
    }

    public List<Tile> getTiles() {
        return tiles;
    }
}
