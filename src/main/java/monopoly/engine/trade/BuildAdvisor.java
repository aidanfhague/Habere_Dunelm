package monopoly.ai;

import monopoly.engine.GameAction;
import monopoly.engine.GameActionType;
import monopoly.engine.GameEngine;
import monopoly.engine.GameState;
import monopoly.engine.PropertyState;
import monopoly.setup.ColourGroup;
import monopoly.setup.DeedProfiles;
import monopoly.setup.DeedProfiles.StreetDeed;

import java.util.*;

/**
 * Simple heuristic advisor:
 * - Only builds when player owns full colour set
 * - Obeys even-building rule
 * - Uses a basic EV test: expected incremental rent over a short horizon
 * - Keeps a cash safety reserve
 *
 */
public final class BuildAdvisor {

    // Deed Map
    private static final Map<Integer, Object> DEEDS = DeedProfiles.ukClassic2017ByIndex();

    // Tuning knobs (easy to tweak / learn later)
    private final int safetyReserve;          // keep this much cash after building
    private final int horizonTurns;           // short EV horizon
    private final double pLandingPerTurn;     // placeholder landing prob
    private final double minRoi;              // EV/Cost threshold

    public BuildAdvisor() {
        this(200, 20, 1.0 / 40.0, 0.7);
    }

    public BuildAdvisor(int safetyReserve, int horizonTurns, double pLandingPerTurn, double minRoi) {
        this.safetyReserve = safetyReserve;
        this.horizonTurns = horizonTurns;
        this.pLandingPerTurn = pLandingPerTurn;
        this.minRoi = minRoi;
    }

    /**
     * Returns a BUILD_HOUSE or BUILD_HOTEL action if advisable, else null.
     */
    public GameAction maybeBuild(GameState state, GameEngine engine) {
        int me = state.getCurrentPlayerIndex();
        int cash = state.getCurrentPlayer().getCash();

        // Count opponents still alive
        int opponents = 0;
        for (int i = 0; i < state.getPlayers().size(); i++) {
            if (i != me && !state.getPlayers().get(i).isBankrupt()) opponents++;
        }
        if (opponents <= 0) return null;

        // Find best single build move by EV/ROI
        BuildCandidate best = null;

        // 1) Consider house builds
        for (var e : DEEDS.entrySet()) {
            if (!(e.getValue() instanceof StreetDeed sd)) continue;

            int idx = sd.index;
            PropertyState ps = state.getPropertyState(idx);

            if (ps.getOwnerPlayerIndex() == null || ps.getOwnerPlayerIndex() != me) continue;
            if (ps.isMortgaged()) continue;
            if (ps.hasHotel()) continue;
            if (ps.getHouses() >= 4) continue;

            // must own full colour group
            if (!ownsFullGroup(state, me, sd.group)) continue;

            // even-building rule: target house count after build must keep max-min <= 1 across group
            int newHouseCount = ps.getHouses() + 1;
            if (!respectsEvenBuilding(state, sd.group, idx, newHouseCount)) continue;

            // bank house supply
            if (state.getHousesRemaining() <= 0) continue;

            int cost = sd.houseCost;
            if (cash - cost < safetyReserve) continue;

            int currentRent = sd.rents[ps.getBuildings()]; // buildings 0..4 -> rent index matches
            int nextRent = sd.rents[ps.getBuildings() + 1];

            int deltaRent = Math.max(0, nextRent - currentRent);

            double ev = opponents * horizonTurns * pLandingPerTurn * deltaRent;
            double roi = cost == 0 ? 0 : (ev / cost);

            if (roi >= minRoi) {
                BuildCandidate c = new BuildCandidate(idx, false, cost, ev, roi);
                if (best == null || c.roi > best.roi) best = c;
            }
        }

        // 2) Consider hotel builds (only when entire group is at 4 houses, then upgrade one)
        for (var e : DEEDS.entrySet()) {
            if (!(e.getValue() instanceof StreetDeed sd)) continue;

            int idx = sd.index;
            PropertyState ps = state.getPropertyState(idx);

            if (ps.getOwnerPlayerIndex() == null || ps.getOwnerPlayerIndex() != me) continue;
            if (ps.isMortgaged()) continue;
            if (ps.hasHotel()) continue;
            if (ps.getHouses() != 4) continue;

            if (!ownsFullGroup(state, me, sd.group)) continue;
            if (!groupAllHaveFourHouses(state, sd.group)) continue;

            // bank hotel supply
            if (state.getHotelsRemaining() <= 0) continue;

            int cost = sd.houseCost;
            if (cash - cost < safetyReserve) continue;

            int currentRent = sd.rents[4];
            int nextRent = sd.rents[5];
            int deltaRent = Math.max(0, nextRent - currentRent);

            double ev = opponents * horizonTurns * pLandingPerTurn * deltaRent;
            double roi = cost == 0 ? 0 : (ev / cost);

            if (roi >= minRoi) {
                BuildCandidate c = new BuildCandidate(idx, true, cost, ev, roi);
                if (best == null || c.roi > best.roi) best = c;
            }
        }

        if (best == null) return null;

        // Return action for the best candidate
        if (best.hotel) {
            return GameAction.onTile(GameActionType.BUILD_HOTEL, best.tileIdx);
        } else {
            return GameAction.onTile(GameActionType.BUILD_HOUSE, best.tileIdx);
        }
    }

    // ---- group helpers ----

    private boolean ownsFullGroup(GameState state, int ownerIdx, ColourGroup group) {
        for (var e : DEEDS.entrySet()) {
            if (e.getValue() instanceof StreetDeed sd && sd.group == group) {
                PropertyState ps = state.getPropertyState(sd.index);
                if (ps.getOwnerPlayerIndex() == null || ps.getOwnerPlayerIndex() != ownerIdx) return false;
            }
        }
        return true;
    }

    private boolean respectsEvenBuilding(GameState state, ColourGroup group, int tileIdx, int newHousesOnTile) {
        int min = Integer.MAX_VALUE;
        int max = Integer.MIN_VALUE;

        for (var e : DEEDS.entrySet()) {
            if (e.getValue() instanceof StreetDeed sd && sd.group == group) {
                int houses = state.getPropertyState(sd.index).getHouses();
                if (sd.index == tileIdx) houses = newHousesOnTile;
                min = Math.min(min, houses);
                max = Math.max(max, houses);
            }
        }
        return (max - min) <= 1;
    }

    private boolean groupAllHaveFourHouses(GameState state, ColourGroup group) {
        for (var e : DEEDS.entrySet()) {
            if (e.getValue() instanceof StreetDeed sd && sd.group == group) {
                if (state.getPropertyState(sd.index).getHouses() != 4) return false;
            }
        }
        return true;
    }

    // internal record-like container (works on Java 17+ without requiring "record")
    private static final class BuildCandidate {
        final int tileIdx;
        final boolean hotel;
        final int cost;
        final double ev;
        final double roi;

        BuildCandidate(int tileIdx, boolean hotel, int cost, double ev, double roi) {
            this.tileIdx = tileIdx;
            this.hotel = hotel;
            this.cost = cost;
            this.ev = ev;
            this.roi = roi;
        }
    }
}

