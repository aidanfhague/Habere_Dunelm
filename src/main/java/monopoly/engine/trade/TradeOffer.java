package monopoly.engine.trade;

import java.util.*;

public final class TradeOffer {
    private final int fromPlayerIndex;
    private final int toPlayerIndex;

    // Tiles and cash
    private final Set<Integer> tilesFromAtoB;
    private final Set<Integer> tilesFromBtoA;
    private final int cashFromAtoB;
    private final int cashFromBtoA;

    // GOJF cards (counts). v1: at most 1 of each type usually, but allow ints.
    private final int chanceGojfAtoB;
    private final int communityGojfAtoB;
    private final int chanceGojfBtoA;
    private final int communityGojfBtoA;

    // Mortgage choices for mortgaged tiles being received
    // (only relevant if the tile in that direction is mortgaged)
    private final Map<Integer, MortgageTransferChoice> mortgageChoiceForTilesGoingToB; // tilesFromAtoB
    private final Map<Integer, MortgageTransferChoice> mortgageChoiceForTilesGoingToA; // tilesFromBtoA

    public TradeOffer(
            int fromPlayerIndex,
            int toPlayerIndex,
            Set<Integer> tilesFromAtoB,
            Set<Integer> tilesFromBtoA,
            int cashFromAtoB,
            int cashFromBtoA,
            int chanceGojfAtoB,
            int communityGojfAtoB,
            int chanceGojfBtoA,
            int communityGojfBtoA,
            Map<Integer, MortgageTransferChoice> mortgageChoiceForTilesGoingToB,
            Map<Integer, MortgageTransferChoice> mortgageChoiceForTilesGoingToA
    ) {
        if (fromPlayerIndex == toPlayerIndex) throw new IllegalArgumentException("Trade must be between different players.");
        if (cashFromAtoB < 0 || cashFromBtoA < 0) throw new IllegalArgumentException("Cash amounts must be >= 0.");
        if (chanceGojfAtoB < 0 || communityGojfAtoB < 0 || chanceGojfBtoA < 0 || communityGojfBtoA < 0) {
            throw new IllegalArgumentException("GOJF counts must be >= 0.");
        }

        this.fromPlayerIndex = fromPlayerIndex;
        this.toPlayerIndex = toPlayerIndex;

        this.tilesFromAtoB = Collections.unmodifiableSet(new LinkedHashSet<>(Objects.requireNonNullElse(tilesFromAtoB, Set.of())));
        this.tilesFromBtoA = Collections.unmodifiableSet(new LinkedHashSet<>(Objects.requireNonNullElse(tilesFromBtoA, Set.of())));

        this.cashFromAtoB = cashFromAtoB;
        this.cashFromBtoA = cashFromBtoA;

        this.chanceGojfAtoB = chanceGojfAtoB;
        this.communityGojfAtoB = communityGojfAtoB;
        this.chanceGojfBtoA = chanceGojfBtoA;
        this.communityGojfBtoA = communityGojfBtoA;

        this.mortgageChoiceForTilesGoingToB = Collections.unmodifiableMap(new HashMap<>(Objects.requireNonNullElse(mortgageChoiceForTilesGoingToB, Map.of())));
        this.mortgageChoiceForTilesGoingToA = Collections.unmodifiableMap(new HashMap<>(Objects.requireNonNullElse(mortgageChoiceForTilesGoingToA, Map.of())));
    }

    // Convenience builder for simple offers (no cards, no mortgage choices)
    public static TradeOffer simpleCashForTile(int from, int to, int tileFromTo, int cashFromAtoB) {
        return new TradeOffer(
                from, to,
                Set.of(), Set.of(tileFromTo),
                cashFromAtoB, 0,
                0, 0, 0, 0,
                Map.of(), Map.of()
        );
    }

    public int getFromPlayerIndex() { return fromPlayerIndex; }
    public int getToPlayerIndex() { return toPlayerIndex; }

    public Set<Integer> getTilesFromAtoB() { return tilesFromAtoB; }
    public Set<Integer> getTilesFromBtoA() { return tilesFromBtoA; }

    public int getCashFromAtoB() { return cashFromAtoB; }
    public int getCashFromBtoA() { return cashFromBtoA; }

    public int getChanceGojfAtoB() { return chanceGojfAtoB; }
    public int getCommunityGojfAtoB() { return communityGojfAtoB; }
    public int getChanceGojfBtoA() { return chanceGojfBtoA; }
    public int getCommunityGojfBtoA() { return communityGojfBtoA; }

    public Map<Integer, MortgageTransferChoice> getMortgageChoiceForTilesGoingToB() { return mortgageChoiceForTilesGoingToB; }
    public Map<Integer, MortgageTransferChoice> getMortgageChoiceForTilesGoingToA() { return mortgageChoiceForTilesGoingToA; }

    public MortgageTransferChoice choiceForTileGoingToB(int tile) {
        return mortgageChoiceForTilesGoingToB.getOrDefault(tile, MortgageTransferChoice.KEEP_MORTGAGED);
    }

    public MortgageTransferChoice choiceForTileGoingToA(int tile) {
        return mortgageChoiceForTilesGoingToA.getOrDefault(tile, MortgageTransferChoice.KEEP_MORTGAGED);
    }

    @Override
    public String toString() {
        return "TradeOffer{" +
                "A=" + fromPlayerIndex +
                ", B=" + toPlayerIndex +
                ", A->B tiles=" + tilesFromAtoB +
                ", B->A tiles=" + tilesFromBtoA +
                ", A->B cash=" + cashFromAtoB +
                ", B->A cash=" + cashFromBtoA +
                ", A->B GOJF(ch=" + chanceGojfAtoB + ",cc=" + communityGojfAtoB + ")" +
                ", B->A GOJF(ch=" + chanceGojfBtoA + ",cc=" + communityGojfBtoA + ")" +
                '}';
    }
}


