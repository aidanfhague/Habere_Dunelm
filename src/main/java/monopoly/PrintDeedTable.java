package monopoly;

import monopoly.setup.DeedProfiles;
import monopoly.setup.DeedProfiles.*;

import java.util.Map;

public class PrintDeedTable {
    public static void main(String[] args) {
        Map<Integer, Object> byIndex = DeedProfiles.ukClassic2017ByIndex();

        System.out.println("UK Classic-style deed economics (indexed by position from GO)");
        System.out.println();

        // Streets
        System.out.println("STREETS:");
        System.out.printf("%5s %7s %8s %10s %6s %6s %6s %6s %6s %6s%n",
                "Idx", "Price", "Mortgage", "HouseCost", "Site", "H1", "H2", "H3", "H4", "Hotel");

        byIndex.values().stream()
                .filter(v -> v instanceof StreetDeed)
                .map(v -> (StreetDeed) v)
                .sorted((a, b) -> Integer.compare(a.index, b.index))
                .forEach(d -> System.out.printf("%5d %7d %8d %10d %6d %6d %6d %6d %6d %6d%n",
                        d.index, d.price, d.mortgage, d.houseCost,
                        d.rents[0], d.rents[1], d.rents[2], d.rents[3], d.rents[4], d.rents[5]
                ));

        System.out.println();
        System.out.println("RAILROADS (rent by number owned):");
        System.out.printf("%5s %7s %8s %6s %6s %6s %6s%n", "Idx", "Price", "Mortgage", "1RR", "2RR", "3RR", "4RR");

        byIndex.values().stream()
                .filter(v -> v instanceof RailroadDeed)
                .map(v -> (RailroadDeed) v)
                .sorted((a, b) -> Integer.compare(a.index, b.index))
                .forEach(d -> System.out.printf("%5d %7d %8d %6d %6d %6d %6d%n",
                        d.index, d.price, d.mortgage,
                        d.rentByCount[0], d.rentByCount[1], d.rentByCount[2], d.rentByCount[3]
                ));

        System.out.println();
        System.out.println("UTILITIES (rent multiplier):");
        System.out.printf("%5s %7s %8s %10s %10s%n", "Idx", "Price", "Mortgage", "1 util", "2 utils");

        byIndex.values().stream()
                .filter(v -> v instanceof UtilityDeed)
                .map(v -> (UtilityDeed) v)
                .sorted((a, b) -> Integer.compare(a.index, b.index))
                .forEach(d -> System.out.printf("%5d %7d %8d %10s %10s%n",
                        d.index, d.price, d.mortgage,
                        d.multiplierIfOne + "×dice", d.multiplierIfTwo + "×dice"
                ));
    }
}

