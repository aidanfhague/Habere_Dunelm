package monopoly.setup;

import monopoly.model.TileType;

import java.util.LinkedHashMap;
import java.util.Map;

public final class BoardLayout {

    public record BoardSpot(int index, String name, TileType type) {}

    /** UK classic-style layout by index (0..39). Names are placeholders you can change anytime. */
    public static Map<Integer, BoardSpot> ukClassic2017ByIndex() {
        Map<Integer, BoardSpot> m = new LinkedHashMap<>();

        // NOTE: You can rename any "name" string without affecting rules.
        // Types are the important part for engine logic.

        m.put(0,  new BoardSpot(0,  "Matriculation/LOAN DROP", TileType.GO));

        m.put(1,  new BoardSpot(1,  "Student Union", TileType.PROPERTY));
        m.put(2,  new BoardSpot(2,  "Afternoon in the Swan", TileType.COMMUNITY_CHEST));
        m.put(3,  new BoardSpot(3,  "Elvet Riverside", TileType.PROPERTY));
        m.put(4,  new BoardSpot(4,  "Income Tax", TileType.TAX));
        m.put(5,  new BoardSpot(5,  "Klute", TileType.RAILROAD));
        m.put(6,  new BoardSpot(6,  "Maiden Castle", TileType.PROPERTY));
        m.put(7,  new BoardSpot(7,  "Chance", TileType.CHANCE));
        m.put(8,  new BoardSpot(8,  "Hild Bede", TileType.PROPERTY));
        m.put(9,  new BoardSpot(9,  "Business", TileType.PROPERTY));

        m.put(10, new BoardSpot(10, "Billy B / Small Island Coffee", TileType.JAIL));

        m.put(11, new BoardSpot(11, "St. Aidan's", TileType.PROPERTY));
        m.put(12, new BoardSpot(12, "Lumley Challenge", TileType.UTILITY));
        m.put(13, new BoardSpot(13, "John Snow", TileType.PROPERTY));
        m.put(14, new BoardSpot(14, "South", TileType.PROPERTY));
        m.put(15, new BoardSpot(15, "Fabio's", TileType.RAILROAD));
        m.put(16, new BoardSpot(16, "Stevenson", TileType.PROPERTY));
        m.put(17, new BoardSpot(17, "Hustings", TileType.COMMUNITY_CHEST));
        m.put(18, new BoardSpot(18, "Josephine Butler", TileType.PROPERTY));
        m.put(19, new BoardSpot(19, "Botanic Gardens", TileType.PROPERTY));

        m.put(20, new BoardSpot(20, "Upper Mountjoy", TileType.FREE_PARKING));

        m.put(21, new BoardSpot(21, "Van Mildert", TileType.PROPERTY));
        m.put(22, new BoardSpot(22, "Chance", TileType.CHANCE));
        m.put(23, new BoardSpot(23, "Collingwood", TileType.PROPERTY));
        m.put(24, new BoardSpot(24, "Grey", TileType.PROPERTY));
        m.put(25, new BoardSpot(25, "Osbournes", TileType.RAILROAD));
        m.put(26, new BoardSpot(26, "TLC", TileType.PROPERTY));
        m.put(27, new BoardSpot(27, "St. Mary's", TileType.PROPERTY));
        m.put(28, new BoardSpot(28, "Mary's Challenge", TileType.UTILITY));
        m.put(29, new BoardSpot(29, "St. Cuthbert's", TileType.PROPERTY));

        m.put(30, new BoardSpot(30, "Pull an All-Nighter", TileType.GO_TO_JAIL));

        m.put(31, new BoardSpot(31, "St. Chad's", TileType.PROPERTY));
        m.put(32, new BoardSpot(32, "St. John's", TileType.PROPERTY));
        m.put(33, new BoardSpot(33, "Hustings", TileType.COMMUNITY_CHEST));
        m.put(34, new BoardSpot(34, "Hatfield", TileType.PROPERTY));
        m.put(35, new BoardSpot(35, "Jimmy Allen's", TileType.RAILROAD));
        m.put(36, new BoardSpot(36, "Chance", TileType.CHANCE));
        m.put(37, new BoardSpot(37, "University College", TileType.PROPERTY));
        m.put(38, new BoardSpot(38, "Super Tax", TileType.TAX));
        m.put(39, new BoardSpot(39, "Durham Cathedral", TileType.PROPERTY));

        // Sanity check
        if (m.size() != 40) {
            throw new IllegalStateException("Board layout must define exactly 40 tiles; got " + m.size());
        }
        return m;
    }

    private BoardLayout() {}
}

