package monopoly.setup;

import java.util.LinkedHashMap;
import java.util.Map;

public final class DeedProfiles {

    /** Rents array: [site, 1 house, 2 houses, 3 houses, 4 houses, hotel] */
    public static final class StreetDeed {
        public final int index;
        public final ColourGroup group;
        public final int price;
        public final int mortgage;
        public final int houseCost;
        public final int[] rents;

        public StreetDeed(int index, ColourGroup group, int price, int mortgage, int houseCost, int[] rents) {
            this.index = index;
            this.group = group;
            this.price = price;
            this.mortgage = mortgage;
            this.houseCost = houseCost;
            if (rents.length != 6) throw new IllegalArgumentException("Street rents must have 6 entries.");
            this.rents = rents;
        }
    }

    public static final class RailroadDeed {
        public final int index;
        public final int price;
        public final int mortgage;
        /** Rent by railroads owned: 1..4 => 25,50,100,200 */
        public final int[] rentByCount;

        public RailroadDeed(int index, int price, int mortgage, int[] rentByCount) {
            this.index = index;
            this.price = price;
            this.mortgage = mortgage;
            if (rentByCount.length != 4) throw new IllegalArgumentException("Railroad rents must have 4 entries.");
            this.rentByCount = rentByCount;
        }
    }

    public static final class UtilityDeed {
        public final int index;
        public final int price;
        public final int mortgage;
        /** Rent multiplier: 4× dice if 1 utility, 10× dice if 2 utilities */
        public final int multiplierIfOne;
        public final int multiplierIfTwo;

        public UtilityDeed(int index, int price, int mortgage, int multiplierIfOne, int multiplierIfTwo) {
            this.index = index;
            this.price = price;
            this.mortgage = mortgage;
            this.multiplierIfOne = multiplierIfOne;
            this.multiplierIfTwo = multiplierIfTwo;
        }
    }

    /** UK/London board economics (indexed by board position 0..39). */
    public static Map<Integer, Object> ukClassic2017ByIndex() {
        Map<Integer, Object> m = new LinkedHashMap<>();

        // --- Streets (index, group, price, mortgage, houseCost, rents[site,1,2,3,4,hotel]) ---

        // Brown (houseCost 50)
        m.put(1,  new StreetDeed(1,  ColourGroup.BROWN,       60,  30,  50, new int[]{ 2, 10, 30,  90, 160,  250}));
        m.put(3,  new StreetDeed(3,  ColourGroup.BROWN,       60,  30,  50, new int[]{ 4, 20, 60, 180, 320,  450}));

        // Light Blue (houseCost 50)
        m.put(6,  new StreetDeed(6,  ColourGroup.LIGHT_BLUE, 100,  50,  50, new int[]{ 6, 30, 90, 270, 400,  550}));
        m.put(8,  new StreetDeed(8,  ColourGroup.LIGHT_BLUE, 100,  50,  50, new int[]{ 6, 30, 90, 270, 400,  550}));
        m.put(9,  new StreetDeed(9,  ColourGroup.LIGHT_BLUE, 120,  60,  50, new int[]{ 8, 40,100, 300, 450,  600}));

        // Pink (houseCost 100)
        m.put(11, new StreetDeed(11, ColourGroup.PINK,       140,  70, 100, new int[]{10, 50,150, 450, 625,  750}));
        m.put(13, new StreetDeed(13, ColourGroup.PINK,       140,  70, 100, new int[]{10, 50,150, 450, 625,  750}));
        m.put(14, new StreetDeed(14, ColourGroup.PINK,       160,  80, 100, new int[]{12, 60,180, 500, 700,  900}));

        // Orange (houseCost 100)
        m.put(16, new StreetDeed(16, ColourGroup.ORANGE,     180,  90, 100, new int[]{14, 70,200, 550, 750,  950}));
        m.put(18, new StreetDeed(18, ColourGroup.ORANGE,     180,  90, 100, new int[]{14, 70,200, 550, 750,  950}));
        m.put(19, new StreetDeed(19, ColourGroup.ORANGE,     200, 100, 100, new int[]{16, 80,220, 600, 800, 1000}));

        // Red (houseCost 150)
        m.put(21, new StreetDeed(21, ColourGroup.RED,        220, 110, 150, new int[]{18, 90,250, 700, 875, 1050}));
        m.put(23, new StreetDeed(23, ColourGroup.RED,        220, 110, 150, new int[]{18, 90,250, 700, 875, 1050}));
        m.put(24, new StreetDeed(24, ColourGroup.RED,        240, 120, 150, new int[]{20,100,300, 750, 925, 1100}));

        // Yellow (houseCost 150)
        m.put(26, new StreetDeed(26, ColourGroup.YELLOW,     260, 130, 150, new int[]{22,110,330, 800, 975, 1150}));
        m.put(27, new StreetDeed(27, ColourGroup.YELLOW,     260, 130, 150, new int[]{22,110,330, 800, 975, 1150}));
        m.put(29, new StreetDeed(29, ColourGroup.YELLOW,     280, 140, 150, new int[]{24,120,360, 850,1025, 1200}));

        // Green (houseCost 200)
        m.put(31, new StreetDeed(31, ColourGroup.GREEN,      300, 150, 200, new int[]{26,130,390, 900,1100, 1275}));
        m.put(32, new StreetDeed(32, ColourGroup.GREEN,      300, 150, 200, new int[]{26,130,390, 900,1100, 1275}));
        m.put(34, new StreetDeed(34, ColourGroup.GREEN,      320, 160, 200, new int[]{28,150,450,1000,1200, 1400}));

        // Dark Blue (houseCost 200)
        m.put(37, new StreetDeed(37, ColourGroup.DARK_BLUE,  350, 175, 200, new int[]{35,175,500,1100,1300, 1500}));
        m.put(39, new StreetDeed(39, ColourGroup.DARK_BLUE,  400, 200, 200, new int[]{50,200,600,1400,1700, 2000}));

        // --- Railroads / Stations (price 200, mortgage 100, rent 25/50/100/200) ---
        int[] rrRents = new int[]{25, 50, 100, 200};
        m.put(5,  new RailroadDeed(5,  200, 100, rrRents));
        m.put(15, new RailroadDeed(15, 200, 100, rrRents));
        m.put(25, new RailroadDeed(25, 200, 100, rrRents));
        m.put(35, new RailroadDeed(35, 200, 100, rrRents));

        // --- Utilities (price 150, mortgage 75, rent = dice×4 or dice×10) ---
        m.put(12, new UtilityDeed(12, 150, 75, 4, 10));
        m.put(28, new UtilityDeed(28, 150, 75, 4, 10));

        return m;
    }

    private DeedProfiles() {}
}


