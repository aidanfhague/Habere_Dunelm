package monopoly.setup;

import monopoly.model.Board;
import monopoly.model.Tile;
import monopoly.model.TileType;

import java.util.ArrayList;
import java.util.List;

public class StandardBoardFactory {

    public static Board createBasic40TileBoard() {
        // Placeholder names/types. Replace later with real UK set.
        List<Tile> tiles = new ArrayList<>(Board.SIZE);

        tiles.add(new Tile(0, "GO", TileType.GO));

        // Fill 1..39 with placeholders for now
        for (int i = 1; i < Board.SIZE; i++) {
            TileType type = TileType.OTHER;
            String name = "Tile " + i;

            // Put a few classic tiles in the right indices to start shaping the board
            if (i == 4) { type = TileType.TAX; name = "Income Tax"; }
            if (i == 7) { type = TileType.CHANCE; name = "Chance"; }
            if (i == 10) { type = TileType.JAIL; name = "Jail / Just Visiting"; }
            if (i == 20) { type = TileType.FREE_PARKING; name = "Free Parking"; }
            if (i == 30) { type = TileType.GO_TO_JAIL; name = "Go To Jail"; }
            if (i == 33) { type = TileType.COMMUNITY_CHEST; name = "Community Chest"; }
            if (i == 36) { type = TileType.CHANCE; name = "Chance"; }

            tiles.add(new Tile(i, name, type));
        }

        return new Board(tiles);
    }
}
