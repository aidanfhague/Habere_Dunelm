package monopoly.engine.trade;

import monopoly.engine.GameState;
import monopoly.engine.PropertyState;
import monopoly.setup.ColourGroup;
import monopoly.setup.DeedProfiles;
import monopoly.setup.DeedProfiles.StreetDeed;

import java.util.*;

public final class TradeAdvisor {

    /**
     * Suggest a few simple trades aimed at completing colour sets.
     * v1: proposes cash-for-property deals (no swaps).
     */
    public static List<TradeOffer> suggestTradesForCurrentPlayer(GameState state, Map<Integer, Object> deedsByIndex) {
        int me = state.getCurrentPlayerIndex();

        // Find "nearly complete" groups: I own all but 1 street in a group
        Map<ColourGroup, List<StreetDeed>> groupToStreets = streetsByGroup(deedsByIndex);

        List<TradeOffer> offers = new ArrayList<>();

        for (var entry : groupToStreets.entrySet()) {
            ColourGroup g = entry.getKey();
            List<StreetDeed> streets = entry.getValue();

            int ownedByMe = 0;
            List<Integer> missing = new ArrayList<>();

            for (StreetDeed sd : streets) {
                PropertyState ps = state.getPropertyState(sd.index);
                Integer owner = ps.getOwnerPlayerIndex();
                if (owner != null && owner == me) ownedByMe++;
                else missing.add(sd.index);
            }

            if (ownedByMe == streets.size() - 1 && missing.size() == 1) {
                int wantedTile = missing.get(0);
                PropertyState psWanted = state.getPropertyState(wantedTile);
                Integer otherOwner = psWanted.getOwnerPlayerIndex();
                if (otherOwner == null) continue; // unowned: buy/auction, not trade

                // v1 restriction alignment: don't suggest if mortgaged or has buildings
                if (psWanted.isMortgaged()) continue;
                if (psWanted.getBuildings() > 0) continue;

                // Offer: cash roughly based on purchase price (or mortgage) with a premium
                Object deed = deedsByIndex.get(wantedTile);
                int basePrice = getPurchasePrice(deed);

                int premium = Math.max(25, basePrice / 4); // simple premium
                int offerCash = basePrice + premium;

                // Cap offer to keep some reserve
                int myCash = state.getPlayers().get(me).getCash();
                int reserve = 200;
                offerCash = Math.min(offerCash, Math.max(0, myCash - reserve));
                if (offerCash <= 0) continue;

                TradeOffer offer = new TradeOffer(
                        me,
                        otherOwner,
                        Set.of(),                // tiles A->B
                        Set.of(wantedTile),      // tiles B->A
                        offerCash,               // cash A->B
                        0,                       // cash B->A
                        0, 0,                    // A->B GOJF: chance, community
                        0, 0,                    // B->A GOJF: chance, community
                        java.util.Map.of(),      // mortgage choices for tiles going to B
                        java.util.Map.of()       // mortgage choices for tiles going to A
                );


                offers.add(offer);
            }
        }

        // Sort: higher cash offers first (usually more likely to be accepted)
        offers.sort(Comparator.comparingInt(TradeOffer::getCashFromAtoB).reversed());

        // Return top few
        return offers.size() > 5 ? offers.subList(0, 5) : offers;
    }

    private static Map<ColourGroup, List<StreetDeed>> streetsByGroup(Map<Integer, Object> deedsByIndex) {
        Map<ColourGroup, List<StreetDeed>> m = new EnumMap<>(ColourGroup.class);
        for (Object v : deedsByIndex.values()) {
            if (v instanceof StreetDeed sd) {
                m.computeIfAbsent(sd.group, k -> new ArrayList<>()).add(sd);
            }
        }
        return m;
    }

    private static int getPurchasePrice(Object deed) {
        if (deed instanceof DeedProfiles.StreetDeed sd) return sd.price;
        if (deed instanceof DeedProfiles.RailroadDeed rd) return rd.price;
        if (deed instanceof DeedProfiles.UtilityDeed ud) return ud.price;
        return 0;
    }

    private TradeAdvisor() {}
}

