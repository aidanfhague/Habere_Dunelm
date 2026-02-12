package monopoly.engine;

import monopoly.model.Board;
import monopoly.model.Player;
import monopoly.model.Tile;
import monopoly.setup.DeedProfiles;
import monopoly.setup.DeedProfiles.RailroadDeed;
import monopoly.setup.DeedProfiles.StreetDeed;
import monopoly.setup.DeedProfiles.UtilityDeed;
import monopoly.setup.BoardDestinations;
import monopoly.engine.trade.TradeOffer;
import monopoly.engine.trade.TradeResponse;
import monopoly.engine.trade.MortgageTransferChoice;


import monopoly.model.TileType;
import java.util.ArrayList;
import java.util.List;
import monopoly.engine.Card;
import monopoly.engine.CardType;




import java.util.Map;

public class GameEngine {
    private final GameConfig config;
    private final Dice dice;
    private final GameState state;

    // Static economics by board index
    private final Map<Integer, Object> deedsByIndex = DeedProfiles.ukClassic2017ByIndex();

    public GameEngine(GameConfig config, Dice dice, GameState state) {
        this.config = config;
        this.dice = dice;
        this.state = state;
    }

    public GameState getState() { return state; }

    public ActionResult startTurnIfNeeded() {
        if (state.getStatus() == GameStatus.FINISHED) {
            return ActionResult.fail("Game is finished.");
        }

        if (state.getPhase() != TurnPhase.START_TURN) {
            return ActionResult.ok();
        }

        Player p = state.getCurrentPlayer();
        if (p.isBankrupt()) {
            state.advanceTurnSkippingBankrupt();
            return ActionResult.ok("Skipping bankrupt player.");
        }

        state.resetDoublesThisTurn();

        if (p.isInJail()) {
            state.setPhase(TurnPhase.IN_JAIL_DECISION);
            return ActionResult.ok(p.getName() + " is in JAIL. Choose: use card (if any) or ROLL_DICE (fine handling is simplified).");
        }

        state.setPhase(TurnPhase.MUST_ROLL);
        return ActionResult.ok(p.getName() + " to play. Action: ROLL_DICE.");
    }

    public ActionResult apply(GameAction action) {
        startTurnIfNeeded();

        if (state.getStatus() == GameStatus.FINISHED) {
            int w = state.getWinnerIndex();
            return ActionResult.fail("Game over. Winner: " + state.getPlayers().get(w).getName());
        }

        if (action == null || action.type() == null) {
            return ActionResult.fail("Action (or action type) is null.");
        }

        return switch (action.type()) {
            case ROLL_DICE -> handleRoll();
            case BUY_PROPERTY -> handleBuy();
            case START_AUCTION -> handleStartAuction();
            case AUCTION_BID -> handleAuctionBid(action.amount());
            case AUCTION_PASS -> handleAuctionPass();
            case BUILD_HOUSE -> handleBuildHouse(action.tileIndex());
            case BUILD_HOTEL -> handleBuildHotel(action.tileIndex());
            case SELL_HOUSE -> handleSellHouse(action.tileIndex());
            case SELL_HOTEL -> handleSellHotel(action.tileIndex());
            case MORTGAGE -> handleMortgage(action.tileIndex());
            case UNMORTGAGE -> handleUnmortgage(action.tileIndex());
            case USE_GET_OUT_OF_JAIL_FREE -> handleUseGetOutOfJailFree();
            case END_TURN -> handleEndTurn();
            case ACCEPT_TRADE -> handleTradeResponse(TradeResponse.ACCEPT);
            case REJECT_TRADE -> handleTradeResponse(TradeResponse.REJECT);
            case CANCEL_TRADE -> handleTradeResponse(TradeResponse.CANCEL);
            case PROPOSE_TRADE -> handleProposeTrade(action.payload(), false);
            case COUNTER_TRADE -> handleProposeTrade(action.payload(), true);


        };
    }

    // ------------------ Core flow: roll -> land -> decision/rent ------------------

    private ActionResult handleRoll() {
        Player p = state.getCurrentPlayer();

        if (state.getPhase() != TurnPhase.MUST_ROLL
                && state.getPhase() != TurnPhase.CAN_ROLL_AGAIN
                && state.getPhase() != TurnPhase.IN_JAIL_DECISION) {
            return ActionResult.fail("Not allowed to roll right now.");
        }

        // Jail: simplified (try doubles; after attempts run out, pay fine and leave)
        if (p.isInJail()) {
            Dice.Roll r = dice.roll2d6();
            state.setLastRollTotal(r.total());

            if (r.isDouble()) {
                p.releaseFromJail();
                movePlayerHandlingGo(p, r.total());
                state.setLandedTileIndex(p.getPosition());
                return afterLandingResolveOrPrompt(p, "Rolled " + r + " in jail: doubles -> released and moved.");
            } else {
                p.decrementJailTurn();
                if (p.getJailTurnsRemaining() <= 0) {
                    p.subtractCash(config.getJailFine());
                    p.releaseFromJail();

                    Dice.Roll exit = dice.roll2d6();
                    state.setLastRollTotal(exit.total());
                    movePlayerHandlingGo(p, exit.total());
                    state.setLandedTileIndex(p.getPosition());

                    updateDebtPhaseIfNeeded(p);
                    return afterLandingResolveOrPrompt(p,
                            "Rolled " + r + " (no doubles). Out of attempts: paid £" + config.getJailFine()
                                    + ", rolled " + exit + " and moved.");
                }

                state.setPhase(TurnPhase.TURN_END);
                return ActionResult.ok("Rolled " + r + " (no doubles). Remains in jail.", "Action: END_TURN");
            }
        }

        Dice.Roll roll = dice.roll2d6();
        state.setLastRollTotal(roll.total());

        // Doubles logic
        if (roll.isDouble()) {
            state.incrementDoublesThisTurn();
            if (state.getDoublesThisTurn() >= 3) {
                p.sendToJail(config.getJailMaxTurns());
                state.setPhase(TurnPhase.TURN_END);
                return ActionResult.ok(p.getName() + " rolled " + roll + " (3rd double) -> sent to JAIL.", "Action: END_TURN");
            }
        }

        movePlayerHandlingGo(p, roll.total());
        state.setLandedTileIndex(p.getPosition());

        ActionResult landing = afterLandingResolveOrPrompt(p, p.getName() + " rolled " + roll + " and moved.");

        // If doubles and you’re not forced into debt/decision, allow rolling again.
        if (roll.isDouble() && state.getPhase() == TurnPhase.MANAGEMENT && p.getCash() >= 0) {
            state.setPhase(TurnPhase.CAN_ROLL_AGAIN);
            return landing;
        }

        // Non-doubles: move to TURN_END unless player is in a forced decision/debt phase
        if (!roll.isDouble()) {
            if (state.getPhase() == TurnPhase.MANAGEMENT) {
                state.setPhase(TurnPhase.TURN_END);
            }
        }

        return landing;
    }

    private ActionResult handleUseGetOutOfJailFree() {
        Player p = state.getCurrentPlayer();

        if (state.getPhase() != TurnPhase.IN_JAIL_DECISION) {
            return ActionResult.fail("You can only use a Get Out of Jail Free card while making a jail decision.");
        }

        if (!p.isInJail()) {
            return ActionResult.fail("You are not in jail.");
        }

        if (!p.hasGetOutOfJailFreeCard()) {
            return ActionResult.fail("You do not have a Get Out of Jail Free card.");
        }

        // Remove from player and return to correct deck bottom
        Card card = p.useGetOutOfJailFreeCard();
        if (card == null) {
            return ActionResult.fail("No Get Out of Jail Free card available.");
        }

        if (card.getType() == CardType.CHANCE) {
            state.getChanceDeck().returnToBottom(card);
        } else {
            state.getCommunityDeck().returnToBottom(card);
        }

        // Release player and continue turn normally
        p.releaseFromJail(); // use your existing method name; if it's different, change this one call
        state.setPhase(TurnPhase.MUST_ROLL);

        return ActionResult.ok(
                p.getName() + " uses a Get Out of Jail Free card (" + card.getType() + ").",
                "Card returned to bottom of " + card.getType() + " deck.",
                "Action: ROLL_DICE"
        );
    }

    /**
     * After movement, decide:
     * - if unowned buyable: LANDED_DECISION (BUY or AUCTION)
     * - else if owned: pay rent (unless mortgaged or self), then maybe debt
     * - else: MANAGEMENT (where build/mortgage is allowed)
     */
    private ActionResult afterLandingResolveOrPrompt(Player p, String prefixEvent) {
        int idx = p.getPosition();
        Tile landedTile = state.getBoard().tileAt(idx);
        Object deed = deedsByIndex.get(idx);

        if (landedTile.getType() == TileType.CHANCE) {
            return resolveChance();
        }
        if (landedTile.getType() == TileType.COMMUNITY_CHEST) {
            return resolveCommunityChest();
        }


        // Not buyable
        if (deed == null) {
            state.setPhase(TurnPhase.MANAGEMENT);
            return ActionResult.ok(
                    prefixEvent,
                    p.getName() + " landed on " + landedTile.getName() + " (tile " + idx + ")",
                    "Action: END_TURN (or BUILD/MORTGAGE)"
            );
        }

        PropertyState ps = state.getPropertyState(idx);

        // Unowned -> must BUY or AUCTION
        if (ps.getOwnerPlayerIndex() == null) {
            state.setPhase(TurnPhase.LANDED_DECISION);
            int price = getPurchasePrice(deed);
            return ActionResult.ok(
                    prefixEvent,
                    p.getName() + " landed on unowned buyable tile " + idx + " (price £" + price + ")",
                    "Action: BUY_PROPERTY or START_AUCTION"
            );
        }

        // Owned by self -> no rent
        if (ps.getOwnerPlayerIndex() == state.getCurrentPlayerIndex()) {
            state.setPhase(TurnPhase.MANAGEMENT);
            return ActionResult.ok(prefixEvent, "Landed on owned tile " + idx + " (no rent).", "Action: END_TURN (or BUILD/MORTGAGE)");
        }

        // Mortgaged -> no rent
        if (ps.isMortgaged()) {
            state.setPhase(TurnPhase.MANAGEMENT);
            return ActionResult.ok(prefixEvent, "Landed on mortgaged tile " + idx + " (no rent).", "Action: END_TURN (or BUILD/MORTGAGE)");
        }

        // Pay rent
        int rent = computeRent(idx, deed, ps);
        int ownerIdx = ps.getOwnerPlayerIndex();
        Player owner = state.getPlayers().get(ownerIdx);

        p.subtractCash(rent);
        owner.addCash(rent);

        updateDebtPhaseIfNeeded(p);

        if (state.getPhase() == TurnPhase.MUST_RESOLVE_DEBT) {
            return ActionResult.ok(
                    prefixEvent,
                    p.getName() + " landed on tile " + idx + " and owes rent £" + rent + " to " + owner.getName(),
                    p.getName() + " cash is now £" + p.getCash() + " -> MUST RESOLVE DEBT (MORTGAGE)."
            );
        }

        state.setPhase(TurnPhase.MANAGEMENT);
        return ActionResult.ok(prefixEvent, p.getName() + " paid rent £" + rent + " to " + owner.getName(),
                "Action: END_TURN (or BUILD/MORTGAGE)");
    }

    // ------------------ BUY / AUCTION ------------------

    private ActionResult handleBuy() {
        if (state.getPhase() != TurnPhase.LANDED_DECISION) {
            return ActionResult.fail("BUY_PROPERTY only allowed immediately after landing on an unowned buyable tile.");
        }

        Player p = state.getCurrentPlayer();
        int idx = state.getLandedTileIndex();
        Object deed = deedsByIndex.get(idx);
        PropertyState ps = state.getPropertyState(idx);

        if (deed == null) return ActionResult.fail("This tile is not buyable.");
        if (ps.getOwnerPlayerIndex() != null) return ActionResult.fail("Tile is already owned.");

        int price = getPurchasePrice(deed);
        p.subtractCash(price);
        ps.setOwnerPlayerIndex(state.getCurrentPlayerIndex());

        updateDebtPhaseIfNeeded(p);
        if (state.getPhase() == TurnPhase.MUST_RESOLVE_DEBT) {
            return ActionResult.ok(
                    p.getName() + " bought tile " + idx + " for £" + price,
                    "Cash is now £" + p.getCash() + " -> MUST RESOLVE DEBT (MORTGAGE)."
            );
        }

        state.setPhase(TurnPhase.MANAGEMENT);
        return ActionResult.ok(p.getName() + " bought tile " + idx + " for £" + price, "Action: END_TURN (or BUILD/MORTGAGE)");
    }

    /**
     * Placeholder auction: leaves unowned for now.
     * Next iteration: implement bids/pass.
     */
    private ActionResult handleStartAuction() {
        // Allow starting auction when you have just landed on unowned buyable tile
        if (state.getPhase() != TurnPhase.LANDED_DECISION) {
            return ActionResult.fail("START_AUCTION only allowed immediately after landing on an unowned buyable tile.");
        }

        int tileIdx = state.getLandedTileIndex();
        Object deed = deedsByIndex.get(tileIdx);
        if (deed == null) return ActionResult.fail("This tile is not auctionable.");
        PropertyState ps = state.getPropertyState(tileIdx);
        if (ps.getOwnerPlayerIndex() != null) return ActionResult.fail("Tile is already owned.");

        state.startAuction(tileIdx, state.getCurrentPlayerIndex());
        state.setPhase(TurnPhase.AUCTION_ACTIVE);

        int bidder = state.getAuctionCurrentBidderIndex();
        return ActionResult.ok(
                "Auction started for tile " + tileIdx + ".",
                "Current bidder: " + state.getPlayers().get(bidder).getName(),
                "Action: AUCTION_BID(amount) or AUCTION_PASS"
        );
    }

    private ActionResult handleAuctionBid(Integer bidAmount) {
        if (state.getPhase() != TurnPhase.AUCTION_ACTIVE || !state.isAuctionInProgress()) {
            return ActionResult.fail("No auction is active.");
        }
        if (bidAmount == null) return ActionResult.fail("AUCTION_BID requires an amount.");

        int bidderIdx = state.getAuctionCurrentBidderIndex();
        Player bidder = state.getPlayers().get(bidderIdx);

        if (!state.isAuctionBidderActive(bidderIdx)) {
            return ActionResult.fail("You have already passed and cannot bid.");
        }
        if (bidder.isBankrupt()) {
            state.auctionPass(bidderIdx);
            state.advanceToNextActiveBidder();
            return ActionResult.fail("Bankrupt players cannot bid.");
        }

        int currentHigh = state.getAuctionHighBid();
        if (bidAmount <= currentHigh) {
            return ActionResult.fail("Bid must be higher than current high bid (£" + currentHigh + ").");
        }

        // Enforce “cannot end turn negative” by never allowing bids that would make cash negative
        if (bidAmount > bidder.getCash()) {
            return ActionResult.fail("Bid exceeds bidder cash. Cash: £" + bidder.getCash());
        }

        // Accept bid
        state.setAuctionHighBid(bidAmount, bidderIdx);

        // Move to next bidder
        int next = state.advanceToNextActiveBidder();

        // If only one active bidder remains, auction ends immediately (winner = high bidder)
        if (state.auctionActiveCount() <= 1 && state.getAuctionHighBidderIndex() != null) {
            return finalizeAuction();
        }

        return ActionResult.ok(
                bidder.getName() + " bids £" + bidAmount + " (new high bid).",
                "Next bidder: " + state.getPlayers().get(next).getName(),
                hintHeuristicForCurrentBidder()
        );
    }

    private ActionResult handleAuctionPass() {
        if (state.getPhase() != TurnPhase.AUCTION_ACTIVE || !state.isAuctionInProgress()) {
            return ActionResult.fail("No auction is active.");
        }

        int bidderIdx = state.getAuctionCurrentBidderIndex();
        Player bidder = state.getPlayers().get(bidderIdx);

        state.auctionPass(bidderIdx);

        // If nobody left active OR nobody ever bid, handle both cases cleanly
        if (state.auctionActiveCount() == 0) {
            int tileIdx = state.getAuctionTileIndex();
            state.endAuction();
            state.setPhase(TurnPhase.MANAGEMENT);
            return ActionResult.ok(
                    bidder.getName() + " passes.",
                    "All players passed. No sale. Tile " + tileIdx + " remains unowned.",
                    "Action: END_TURN (or BUILD/MORTGAGE)"
            );
        }

        // If only one active bidder remains and there is a high bid, auction ends
        if (state.auctionActiveCount() == 1 && state.getAuctionHighBidderIndex() != null) {
            return finalizeAuction();
        }

        // Otherwise continue to next bidder
        int next = state.advanceToNextActiveBidder();
        return ActionResult.ok(
                bidder.getName() + " passes.",
                "Next bidder: " + state.getPlayers().get(next).getName(),
                hintHeuristicForCurrentBidder()
        );
    }
    private ActionResult finalizeAuction() {
        int tileIdx = state.getAuctionTileIndex();
        Object deed = deedsByIndex.get(tileIdx);
        PropertyState ps = state.getPropertyState(tileIdx);

        Integer winnerIdx = state.getAuctionHighBidderIndex();
        int winningBid = state.getAuctionHighBid();

        // No bids were placed (high bidder null) → no sale
        if (winnerIdx == null) {
            state.endAuction();
            state.setPhase(TurnPhase.MANAGEMENT);
            return ActionResult.ok(
                    "Auction ended. No bids placed.",
                    "Tile " + tileIdx + " remains unowned.",
                    "Action: END_TURN (or BUILD/MORTGAGE)"
            );
        }

        Player winner = state.getPlayers().get(winnerIdx);

        // Safety: winner must still be able to pay
        if (winningBid > winner.getCash()) {
            // If this ever happens, it means cash changed mid-auction (it shouldn't in your current model).
            state.endAuction();
            state.setPhase(TurnPhase.MANAGEMENT);
            return ActionResult.fail("Auction winner cannot afford winning bid. Auction cancelled.");
        }

        winner.subtractCash(winningBid);
        ps.setOwnerPlayerIndex(winnerIdx);

        state.endAuction();
        state.setPhase(TurnPhase.MANAGEMENT);

        updateDebtPhaseIfNeeded(winner);
        if (state.getPhase() == TurnPhase.MUST_RESOLVE_DEBT) {
            return ActionResult.ok(
                    "Auction won by " + winner.getName() + " for £" + winningBid + " (tile " + tileIdx + ").",
                    "Winner cash now £" + winner.getCash() + " -> MUST RESOLVE DEBT (MORTGAGE)."
            );
        }

        return ActionResult.ok(
                "Auction won by " + winner.getName() + " for £" + winningBid + " (tile " + tileIdx + ").",
                "Action: END_TURN (or BUILD/MORTGAGE)"
        );
    }

    /** Optional: prints a suggested max bid for the current bidder, using the simple heuristic. */
    private String hintHeuristicForCurrentBidder() {
        int bidderIdx = state.getAuctionCurrentBidderIndex();
        int tileIdx = state.getAuctionTileIndex();
        int suggested = estimateMaxBidHeuristic(bidderIdx, tileIdx);
        return "Heuristic: " + state.getPlayers().get(bidderIdx).getName() + " maxBid≈£" + suggested
                + " (current high £" + state.getAuctionHighBid() + ")";
    }

    public int estimateMaxBidHeuristic(int bidderIdx, int tileIdx) {
        Player bidder = state.getPlayers().get(bidderIdx);
        Object deed = deedsByIndex.get(tileIdx);
        if (deed == null) return 0;

        // Placeholder landing probability per opponent per turn.
        // Replace later with Markov-chain landing probabilities.
        double pLandingPerTurn = 1.0 / 40.0;

        int opponents = 0;
        for (int i = 0; i < state.getPlayers().size(); i++) {
            if (i != bidderIdx && !state.getPlayers().get(i).isBankrupt()) opponents++;
        }

        int horizonTurns = 20;      // short horizon; easy to tune
        int safetyReserve = 200;    // keep cash buffer; easy to tune

        double expectedRentPerLanding = 0.0;

        if (deed instanceof StreetDeed sd) {
            // If bidder would complete the set, value it higher.
            boolean completesSet = wouldCompleteSetIfOwned(bidderIdx, sd);
            boolean alreadyHasSet = ownsFullStreetGroupFor(bidderIdx, sd.group);

            // Use site rent normally; if (already has set or completes set) assume 1 house rent potential
            int rentLevel = 0; // site
            if (alreadyHasSet || completesSet) rentLevel = 1; // 1 house "potential"
            expectedRentPerLanding = sd.rents[rentLevel];

            // Add a completion bonus to reflect development potential
            if (completesSet) expectedRentPerLanding *= 1.35;
        } else if (deed instanceof RailroadDeed rd) {
            int owned = countOwnedRailroadsFor(bidderIdx);
            int after = Math.min(4, owned + 1);
            expectedRentPerLanding = rd.rentByCount[after - 1];
        } else if (deed instanceof UtilityDeed ud) {
            int owned = countOwnedUtilitiesFor(bidderIdx);
            int after = Math.min(2, owned + 1);
            int mult = (after >= 2) ? ud.multiplierIfTwo : ud.multiplierIfOne;
            double avgRoll = 7.0;
            expectedRentPerLanding = avgRoll * mult;
        }

        // expected value over horizon: opponents × turns × P(landing) × rent
        double ev = opponents * horizonTurns * pLandingPerTurn * expectedRentPerLanding;

        // Convert to a bid cap:
        // - never bid more than you can afford while keeping safetyReserve (unless you’re poor already)
        int capByCash = Math.max(0, bidder.getCash() - safetyReserve);
        int capByEV = (int) Math.floor(ev);

        return Math.max(0, Math.min(capByCash, capByEV));
    }

// --- helpers for heuristic (bidder-specific ownership checks) ---

    private boolean ownsFullStreetGroupFor(int bidderIdx, monopoly.setup.ColourGroup group) {
        for (var e : deedsByIndex.entrySet()) {
            Object deed = e.getValue();
            if (deed instanceof StreetDeed sd && sd.group == group) {
                PropertyState ps = state.getPropertyState(sd.index);
                if (ps.getOwnerPlayerIndex() == null || ps.getOwnerPlayerIndex() != bidderIdx) return false;
            }
        }
        return true;
    }

    private boolean wouldCompleteSetIfOwned(int bidderIdx, StreetDeed target) {
        // If bidder already owns all others in the group, then buying this completes it
        for (var e : deedsByIndex.entrySet()) {
            Object deed = e.getValue();
            if (deed instanceof StreetDeed sd && sd.group == target.group) {
                if (sd.index == target.index) continue;
                PropertyState ps = state.getPropertyState(sd.index);
                if (ps.getOwnerPlayerIndex() == null || ps.getOwnerPlayerIndex() != bidderIdx) return false;
            }
        }
        return true;
    }

    private int countOwnedRailroadsFor(int bidderIdx) {
        int count = 0;
        for (var e : deedsByIndex.entrySet()) {
            if (e.getValue() instanceof RailroadDeed) {
                PropertyState ps = state.getPropertyState(e.getKey());
                if (ps.getOwnerPlayerIndex() != null && ps.getOwnerPlayerIndex() == bidderIdx) count++;
            }
        }
        return count;
    }

    private int countOwnedUtilitiesFor(int bidderIdx) {
        int count = 0;
        for (var e : deedsByIndex.entrySet()) {
            if (e.getValue() instanceof UtilityDeed) {
                PropertyState ps = state.getPropertyState(e.getKey());
                if (ps.getOwnerPlayerIndex() != null && ps.getOwnerPlayerIndex() == bidderIdx) count++;
            }
        }
        return count;
    }



    // ------------------ BUILD HOUSES / HOTELS (WITH GROUPS + SUPPLY LIMITS) ------------------

    private ActionResult handleBuildHouse(Integer tileIndex) {
        if (tileIndex == null) return ActionResult.fail("BUILD_HOUSE requires tileIndex.");
        if (state.getPhase() != TurnPhase.MANAGEMENT && state.getPhase() != TurnPhase.TURN_END) {
            return ActionResult.fail("BUILD_HOUSE only allowed during your turn (management/end phase).");
        }

        int idx = tileIndex;
        Object deed = deedsByIndex.get(idx);
        if (!(deed instanceof StreetDeed sd)) return ActionResult.fail("BUILD_HOUSE only applies to street properties.");

        PropertyState ps = state.getPropertyState(idx);
        if (ps.getOwnerPlayerIndex() == null || ps.getOwnerPlayerIndex() != state.getCurrentPlayerIndex()) {
            return ActionResult.fail("You do not own this property.");
        }
        if (ps.isMortgaged()) return ActionResult.fail("Cannot build on a mortgaged property.");
        if (ps.hasHotel()) return ActionResult.fail("Already has a hotel.");
        if (ps.getHouses() >= 4) return ActionResult.fail("Already has 4 houses (build hotel instead).");

        // Must own the entire colour set
        if (!ownsFullStreetGroup(sd)) {
            return ActionResult.fail("You must own the entire colour group to build houses.");
        }

        // Even-building rule: across the set, max-min <= 1
        if (!respectsEvenBuilding(sd, idx, ps.getHouses() + 1)) {
            return ActionResult.fail("Even-building rule violated: build evenly across the set.");
        }

        // Bank supply limit: 32 houses total
        if (!state.takeHouseFromBank()) {
            return ActionResult.fail("No houses remaining in the bank (32 house limit reached).");
        }

        Player p = state.getCurrentPlayer();
        p.subtractCash(sd.houseCost);
        ps.setBuildings(ps.getBuildings() + 1);

        updateDebtPhaseIfNeeded(p);
        if (state.getPhase() == TurnPhase.MUST_RESOLVE_DEBT) {
            return ActionResult.ok(
                    "Built 1 house on tile " + idx + " for £" + sd.houseCost,
                    "Cash is now £" + p.getCash() + " -> MUST RESOLVE DEBT (MORTGAGE)."
            );
        }

        return ActionResult.ok(
                "Built 1 house on tile " + idx + " for £" + sd.houseCost,
                "Houses now: " + ps.getHouses(),
                "Bank supply now: houses=" + state.getHousesRemaining() + ", hotels=" + state.getHotelsRemaining(),
                "Action: END_TURN (or BUILD/MORTGAGE)"
        );
    }

    private ActionResult handleBuildHotel(Integer tileIndex) {
        if (tileIndex == null) return ActionResult.fail("BUILD_HOTEL requires tileIndex.");
        if (state.getPhase() != TurnPhase.MANAGEMENT && state.getPhase() != TurnPhase.TURN_END) {
            return ActionResult.fail("BUILD_HOTEL only allowed during your turn (management/end phase).");
        }

        int idx = tileIndex;
        Object deed = deedsByIndex.get(idx);
        if (!(deed instanceof StreetDeed sd)) return ActionResult.fail("BUILD_HOTEL only applies to street properties.");

        PropertyState ps = state.getPropertyState(idx);
        if (ps.getOwnerPlayerIndex() == null || ps.getOwnerPlayerIndex() != state.getCurrentPlayerIndex()) {
            return ActionResult.fail("You do not own this property.");
        }
        if (ps.isMortgaged()) return ActionResult.fail("Cannot build on a mortgaged property.");
        if (ps.hasHotel()) return ActionResult.fail("Already has a hotel.");
        if (ps.getHouses() != 4) return ActionResult.fail("Must have 4 houses on this property before building a hotel.");

        // Must own the entire colour set
        if (!ownsFullStreetGroup(sd)) {
            return ActionResult.fail("You must own the entire colour group to build a hotel.");
        }

        // Even-building requirement for hotels: all in set must have 4 houses before any hotel
        if (!groupAllHaveFourHouses(sd)) {
            return ActionResult.fail("Even-building rule: all properties in the set must have 4 houses before any hotel.");
        }

        // Bank supply limit: 12 hotels total
        if (!state.takeHotelFromBank()) {
            return ActionResult.fail("No hotels remaining in the bank (12 hotel limit reached).");
        }

        // Standard rule: building a hotel returns 4 houses to the bank
        state.returnHousesToBank(4);

        Player p = state.getCurrentPlayer();
        p.subtractCash(sd.houseCost);
        ps.setBuildings(5); // hotel

        updateDebtPhaseIfNeeded(p);
        if (state.getPhase() == TurnPhase.MUST_RESOLVE_DEBT) {
            return ActionResult.ok(
                    "Built HOTEL on tile " + idx + " for £" + sd.houseCost,
                    "Cash is now £" + p.getCash() + " -> MUST RESOLVE DEBT (MORTGAGE)."
            );
        }

        return ActionResult.ok(
                "Built HOTEL on tile " + idx + " for £" + sd.houseCost,
                "Bank supply now: houses=" + state.getHousesRemaining() + ", hotels=" + state.getHotelsRemaining(),
                "Action: END_TURN (or BUILD/MORTGAGE)"
        );
    }

    private ActionResult handleSellHouse(Integer tileIndex) {
        if (tileIndex == null) return ActionResult.fail("SELL_HOUSE requires tileIndex.");
        if (state.getPhase() != TurnPhase.MANAGEMENT && state.getPhase() != TurnPhase.TURN_END && state.getPhase() != TurnPhase.MUST_RESOLVE_DEBT) {
            return ActionResult.fail("SELL_HOUSE only allowed during your turn.");
        }

        int idx = tileIndex;
        Object deed = deedsByIndex.get(idx);
        if (!(deed instanceof StreetDeed sd)) return ActionResult.fail("SELL_HOUSE only applies to street properties.");

        PropertyState ps = state.getPropertyState(idx);
        if (ps.getOwnerPlayerIndex() == null || ps.getOwnerPlayerIndex() != state.getCurrentPlayerIndex()) {
            return ActionResult.fail("You do not own this property.");
        }
        if (ps.isMortgaged()) return ActionResult.fail("Cannot sell buildings on a mortgaged property.");
        if (ps.hasHotel()) return ActionResult.fail("This property has a hotel. Use SELL_HOTEL first.");
        if (ps.getHouses() <= 0) return ActionResult.fail("No houses to sell on this property.");

        // Even-selling rule: across the group, max-min <= 1 must still hold AFTER selling
        int newHouses = ps.getHouses() - 1;
        if (!respectsEvenSelling(sd.group, idx, newHouses)) {
            return ActionResult.fail("Even-building rule violated: sell evenly across the set.");
        }

        // execute sale
        ps.setBuildings(ps.getBuildings() - 1);
        state.returnHouseToBank();

        int saleValue = sd.houseCost / 2;
        Player p = state.getCurrentPlayer();
        p.addCash(saleValue);

        // If we were in debt resolution, check if debt cleared
        if (state.getPhase() == TurnPhase.MUST_RESOLVE_DEBT && p.getCash() >= 0) {
            state.setPhase(TurnPhase.TURN_END);
        }

        return ActionResult.ok(
                "Sold 1 house on tile " + idx + " for £" + saleValue + ".",
                "Houses now: " + ps.getHouses(),
                "Bank supply now: houses=" + state.getHousesRemaining() + ", hotels=" + state.getHotelsRemaining(),
                "Cash now £" + p.getCash() + "."
        );
    }

    private ActionResult handleSellHotel(Integer tileIndex) {
        if (tileIndex == null) return ActionResult.fail("SELL_HOTEL requires tileIndex.");
        if (state.getPhase() != TurnPhase.MANAGEMENT && state.getPhase() != TurnPhase.TURN_END && state.getPhase() != TurnPhase.MUST_RESOLVE_DEBT) {
            return ActionResult.fail("SELL_HOTEL only allowed during your turn.");
        }

        int idx = tileIndex;
        Object deed = deedsByIndex.get(idx);
        if (!(deed instanceof StreetDeed sd)) return ActionResult.fail("SELL_HOTEL only applies to street properties.");

        PropertyState ps = state.getPropertyState(idx);
        if (ps.getOwnerPlayerIndex() == null || ps.getOwnerPlayerIndex() != state.getCurrentPlayerIndex()) {
            return ActionResult.fail("You do not own this property.");
        }
        if (ps.isMortgaged()) return ActionResult.fail("Cannot sell buildings on a mortgaged property.");
        if (!ps.hasHotel()) return ActionResult.fail("No hotel to sell on this property.");

        // Selling a hotel typically turns it back into 4 houses.
        // That requires 4 houses to be available in the bank.
        if (state.getHousesRemaining() < 4) {
            return ActionResult.fail("Cannot sell hotel because bank does not have 4 houses available to replace it.");
        }

        // Even rule: after sale, this tile will have 4 houses; ensure group doesn't violate evenness
        if (!respectsEvenSelling(sd.group, idx, 4)) {
            return ActionResult.fail("Even-building rule violated: sell evenly across the set.");
        }

        // execute sale: hotel -> 4 houses
        ps.setBuildings(4); // replace hotel with 4 houses
        state.returnHotelToBank();
        state.takeHousesFromBank(4);

        int saleValue = sd.houseCost / 2;
        Player p = state.getCurrentPlayer();
        p.addCash(saleValue);

        if (state.getPhase() == TurnPhase.MUST_RESOLVE_DEBT && p.getCash() >= 0) {
            state.setPhase(TurnPhase.TURN_END);
        }

        return ActionResult.ok(
                "Sold HOTEL on tile " + idx + " for £" + saleValue + " (hotel replaced with 4 houses).",
                "Bank supply now: houses=" + state.getHousesRemaining() + ", hotels=" + state.getHotelsRemaining(),
                "Cash now £" + p.getCash() + "."
        );
    }

    /**
     * Even-selling check: after changing a single tile's house count,
     * the group must still satisfy max-min <= 1.
     */
    private boolean respectsEvenSelling(monopoly.setup.ColourGroup group, int tileIdx, int newHouseCount) {
        int min = Integer.MAX_VALUE;
        int max = Integer.MIN_VALUE;

        for (var e : deedsByIndex.entrySet()) {
            Object deed = e.getValue();
            if (deed instanceof StreetDeed sd && sd.group == group) {
                int houses = state.getPropertyState(sd.index).getHouses();
                if (sd.index == tileIdx) houses = newHouseCount;
                min = Math.min(min, houses);
                max = Math.max(max, houses);
            }
        }
        return (max - min) <= 1;
    }

    // ------------------ MORTGAGE / DEBT / BANKRUPTCY ------------------

    private ActionResult handleMortgage(Integer tileIndex) {
        if (tileIndex == null) return ActionResult.fail("MORTGAGE requires tileIndex.");

        if (state.getPhase() != TurnPhase.MUST_RESOLVE_DEBT
                && state.getPhase() != TurnPhase.MANAGEMENT
                && state.getPhase() != TurnPhase.TURN_END) {
            return ActionResult.fail("MORTGAGE is only allowed during your turn.");
        }

        int idx = tileIndex;
        Object deed = deedsByIndex.get(idx);
        if (deed == null) return ActionResult.fail("Tile is not mortgageable.");

        PropertyState ps = state.getPropertyState(idx);
        if (ps.getOwnerPlayerIndex() == null || ps.getOwnerPlayerIndex() != state.getCurrentPlayerIndex()) {
            return ActionResult.fail("You do not own this tile.");
        }
        if (ps.isMortgaged()) return ActionResult.fail("Already mortgaged.");

        // Streets: must have no buildings
        if (deed instanceof StreetDeed && ps.getBuildings() > 0) {
            return ActionResult.fail("You must sell buildings before mortgaging a street (selling not implemented yet).");
        }

        int mortgageValue = getMortgageValue(deed);
        Player p = state.getCurrentPlayer();
        ps.setMortgaged(true);
        p.addCash(mortgageValue);

        if (p.getCash() < 0) {
            state.setPhase(TurnPhase.MUST_RESOLVE_DEBT);
            if (!canRaiseCashByMortgage()) {
                bankruptCurrentPlayer();
                return ActionResult.ok(
                        "Mortgaged tile " + idx + " for £" + mortgageValue,
                        p.getName() + " still cannot clear debt -> BANKRUPT.",
                        winnerIfAny()
                );
            }
            return ActionResult.ok(
                    "Mortgaged tile " + idx + " for £" + mortgageValue,
                    "Cash now £" + p.getCash() + " -> still MUST RESOLVE DEBT."
            );
        }

        // Debt cleared
        if (state.getPhase() == TurnPhase.MUST_RESOLVE_DEBT) {
            state.setPhase(TurnPhase.TURN_END);
        }

        return ActionResult.ok(
                "Mortgaged tile " + idx + " for £" + mortgageValue,
                "Cash now £" + p.getCash(),
                "Action: END_TURN (or BUILD/MORTGAGE)"
        );
    }

    private ActionResult handleUnmortgage(Integer tileIndex) {
        if (tileIndex == null) return ActionResult.fail("UNMORTGAGE requires tileIndex.");

        // Allow during normal turn phases; disallow during auction response phases if you want strictness.
        if (state.getPhase() != TurnPhase.MANAGEMENT
                && state.getPhase() != TurnPhase.TURN_END
                && state.getPhase() != TurnPhase.MUST_RESOLVE_DEBT) {
            return ActionResult.fail("UNMORTGAGE is only allowed during your turn (management/end/debt).");
        }

        int idx = tileIndex;
        Object deed = deedsByIndex.get(idx);
        if (deed == null) return ActionResult.fail("Tile is not a deed and cannot be unmortgaged.");

        PropertyState ps = state.getPropertyState(idx);

        // Must own it
        if (ps.getOwnerPlayerIndex() == null || ps.getOwnerPlayerIndex() != state.getCurrentPlayerIndex()) {
            return ActionResult.fail("You do not own this tile.");
        }

        // Must currently be mortgaged
        if (!ps.isMortgaged()) {
            return ActionResult.fail("Tile is not mortgaged.");
        }

        int mortgageValue = getMortgageValue(deed);

        // Your rule: pay mortgage + 10% fee (again), even if you paid 10% on transfer earlier.
        int fee10pct = (mortgageValue + 9) / 10; // ceil(10%)
        int totalCost = mortgageValue + fee10pct;

        Player p = state.getCurrentPlayer();

        // Enforce “no negative cash end of turn”: don't allow action that instantly makes cash negative
        if (p.getCash() - totalCost < 0) {
            state.setPhase(TurnPhase.MUST_RESOLVE_DEBT);
            return ActionResult.fail(
                    "Cannot unmortgage tile " + idx + ": need £" + totalCost + " (mortgage £" + mortgageValue + " + 10% £" + fee10pct + "), cash £" + p.getCash() + ". Use MORTGAGE to raise cash."
            );
        }

        p.subtractCash(totalCost);
        ps.setMortgaged(false);

        // If this was part of resolving debt, check if debt cleared
        if (state.getPhase() == TurnPhase.MUST_RESOLVE_DEBT && p.getCash() >= 0) {
            state.setPhase(TurnPhase.TURN_END);
        }

        return ActionResult.ok(
                "Unmortgaged tile " + idx + " for £" + mortgageValue + " + 10% fee £" + fee10pct + " (total £" + totalCost + ").",
                "Cash now £" + p.getCash() + ".",
                "Action: END_TURN (or BUILD/MORTGAGE/UNMORTGAGE)"
        );
    }

    private ActionResult handleEndTurn() {
        Player p = state.getCurrentPlayer();

        if (p.getCash() < 0) {
            state.setPhase(TurnPhase.MUST_RESOLVE_DEBT);
            if (!canRaiseCashByMortgage()) {
                bankruptCurrentPlayer();
                return ActionResult.ok(p.getName() + " cannot clear debt -> BANKRUPT.", winnerIfAny());
            }
            return ActionResult.fail("You cannot end your turn with cash below 0. Use MORTGAGE to raise cash.");
        }

        if (state.getPhase() == TurnPhase.LANDED_DECISION) {
            return ActionResult.fail("You must BUY_PROPERTY or START_AUCTION first.");
        }
        if (state.getPhase() == TurnPhase.MUST_ROLL || state.getPhase() == TurnPhase.CAN_ROLL_AGAIN) {
            return ActionResult.fail("You must roll (or finish doubles sequence) before ending turn.");
        }
        if (state.getPhase() == TurnPhase.IN_JAIL_DECISION) {
            return ActionResult.fail("You must resolve your jail turn first (ROLL_DICE / etc.).");
        }

        String msg = p.getName() + " ends turn (cash £" + p.getCash() + ").";
        state.advanceTurnSkippingBankrupt();
        return ActionResult.ok(msg);
    }



    // ------------------ Rent helpers ------------------

    private int computeRent(int idx, Object deed, PropertyState ps) {
        if (deed instanceof StreetDeed sd) {
            int b = ps.getBuildings(); // 0..4 houses, 5 hotel
            if (b < 0 || b > 5) b = 0;
            return sd.rents[b];
        }

        if (deed instanceof RailroadDeed rd) {
            int ownerIdx = ps.getOwnerPlayerIndex();
            int ownedRailroads = countOwnedRailroads(ownerIdx);
            return rd.rentByCount[Math.max(1, Math.min(4, ownedRailroads)) - 1];
        }

        if (deed instanceof UtilityDeed ud) {
            int ownerIdx = ps.getOwnerPlayerIndex();
            int ownedUtilities = countOwnedUtilities(ownerIdx);
            int roll = state.getLastRollTotal() == null ? 0 : state.getLastRollTotal();
            int mult = (ownedUtilities >= 2) ? ud.multiplierIfTwo : ud.multiplierIfOne;
            return roll * mult;
        }

        return 0;
    }

    private int countOwnedRailroads(int ownerIdx) {
        int count = 0;
        for (var e : deedsByIndex.entrySet()) {
            if (e.getValue() instanceof RailroadDeed) {
                PropertyState ps = state.getPropertyState(e.getKey());
                if (ps.getOwnerPlayerIndex() != null && ps.getOwnerPlayerIndex() == ownerIdx) count++;
            }
        }
        return count;
    }

    private int countOwnedUtilities(int ownerIdx) {
        int count = 0;
        for (var e : deedsByIndex.entrySet()) {
            if (e.getValue() instanceof UtilityDeed) {
                PropertyState ps = state.getPropertyState(e.getKey());
                if (ps.getOwnerPlayerIndex() != null && ps.getOwnerPlayerIndex() == ownerIdx) count++;
            }
        }
        return count;
    }

    public int getPurchasePrice(Object deed) {
        if (deed instanceof StreetDeed sd) return sd.price;
        if (deed instanceof RailroadDeed rd) return rd.price;
        if (deed instanceof UtilityDeed ud) return ud.price;
        throw new IllegalArgumentException("Unknown deed type.");
    }

    private int getMortgageValue(Object deed) {
        if (deed instanceof StreetDeed sd) return sd.mortgage;
        if (deed instanceof RailroadDeed rd) return rd.mortgage;
        if (deed instanceof UtilityDeed ud) return ud.mortgage;
        throw new IllegalArgumentException("Unknown deed type.");
    }

    // ------------------ GROUP RULES (NO REFLECTION) ------------------

    private boolean ownsFullStreetGroup(StreetDeed target) {
        int current = state.getCurrentPlayerIndex();

        for (var e : deedsByIndex.entrySet()) {
            Object deed = e.getValue();
            if (deed instanceof StreetDeed sd && sd.group == target.group) {
                PropertyState ps = state.getPropertyState(sd.index);
                if (ps.getOwnerPlayerIndex() == null || ps.getOwnerPlayerIndex() != current) {
                    return false;
                }
            }
        }
        return true;
    }

    private boolean respectsEvenBuilding(StreetDeed target, int tileIdx, int newHouseCount) {
        int min = Integer.MAX_VALUE;
        int max = Integer.MIN_VALUE;

        for (var e : deedsByIndex.entrySet()) {
            Object deed = e.getValue();
            if (deed instanceof StreetDeed sd && sd.group == target.group) {
                int houses = state.getPropertyState(sd.index).getHouses();
                if (sd.index == tileIdx) houses = newHouseCount;
                min = Math.min(min, houses);
                max = Math.max(max, houses);
            }
        }
        return (max - min) <= 1;
    }

    private boolean groupAllHaveFourHouses(StreetDeed target) {
        for (var e : deedsByIndex.entrySet()) {
            Object deed = e.getValue();
            if (deed instanceof StreetDeed sd && sd.group == target.group) {
                if (state.getPropertyState(sd.index).getHouses() != 4) return false;
            }
        }
        return true;
    }

    // ------------------ Debt + bankruptcy + win ------------------

    private void updateDebtPhaseIfNeeded(Player p) {
        if (p.getCash() < 0) {
            state.setPhase(TurnPhase.MUST_RESOLVE_DEBT);
        }
    }

    private boolean canRaiseCashByMortgage() {
        int currentIdx = state.getCurrentPlayerIndex();

        for (var e : deedsByIndex.entrySet()) {
            int idx = e.getKey();
            Object deed = e.getValue();
            PropertyState ps = state.getPropertyState(idx);

            if (ps.getOwnerPlayerIndex() != null
                    && ps.getOwnerPlayerIndex() == currentIdx
                    && !ps.isMortgaged()) {

                // streets must have no buildings
                if (deed instanceof StreetDeed && ps.getBuildings() > 0) continue;
                return true;
            }
        }
        return false;
    }

    private void bankruptCurrentPlayer() {
        Player p = state.getCurrentPlayer();
        int cur = state.getCurrentPlayerIndex();
        p.setBankrupt(true);

        // Release all owned properties to unowned (simple first pass)
        for (var e : deedsByIndex.entrySet()) {
            int idx = e.getKey();
            PropertyState ps = state.getPropertyState(idx);
            if (ps.getOwnerPlayerIndex() != null && ps.getOwnerPlayerIndex() == cur) {
                ps.setOwnerPlayerIndex(null);
                ps.setMortgaged(false);

                // Return buildings to bank supply when a player is wiped (simple version)
                int buildings = ps.getBuildings();
                if (buildings >= 1 && buildings <= 4) {
                    state.returnHousesToBank(buildings);
                } else if (buildings == 5) {
                    state.returnHotelToBank();
                    state.returnHousesToBank(4);
                }

                ps.setBuildings(0);
            }
        }
    }

    private String winnerIfAny() {
        int alive = 0;
        int last = -1;
        for (int i = 0; i < state.getPlayers().size(); i++) {
            if (!state.getPlayers().get(i).isBankrupt()) {
                alive++;
                last = i;
            }
        }
        if (alive == 1) {
            state.setWinner(last);
            return "WINNER: " + state.getPlayers().get(last).getName();
        }
        return "No winner yet.";
    }

    // ------------------ Movement ------------------

    private void movePlayerHandlingGo(Player p, int steps) {
        int startPos = p.getPosition();
        int raw = startPos + steps;
        boolean passedGo = raw >= Board.SIZE;

        int newPos = raw % Board.SIZE;
        p.setPosition(newPos);

        if (passedGo) {
            p.addCash(config.getSalaryForPassingGo());
        }
    }
    // ------------------ CARD HELPERS ------------------

    public List<String> moveCurrentPlayerRelative(int delta, boolean allowGoSalaryIfPass) {
        Player p = state.getCurrentPlayer();
        int start = p.getPosition();
        int raw = start + delta;

        // wrap both directions
        int newPos = Math.floorMod(raw, Board.SIZE);

        // Passing GO only matters if moving forward and crossing 0
        if (allowGoSalaryIfPass && delta > 0 && raw >= Board.SIZE) {
            p.addCash(config.getSalaryForPassingGo());
        }

        p.setPosition(newPos);
        state.setLandedTileIndex(newPos);

        ActionResult res = afterLandingResolveOrPrompt(p, p.getName() + " moved " + delta + " spaces to tile " + newPos + ".");
        return res.getEvents();
    }

    public List<String> advanceToAbsolute(int destinationIndex, boolean collectGoIfPass) {
        Player p = state.getCurrentPlayer();
        int start = p.getPosition();

        // If moving forward and pass GO
        if (collectGoIfPass && destinationIndex < start) {
            p.addCash(config.getSalaryForPassingGo());
        }

        p.setPosition(destinationIndex);
        state.setLandedTileIndex(destinationIndex);

        ActionResult res = afterLandingResolveOrPrompt(p, p.getName() + " advanced to tile " + destinationIndex + ".");
        return res.getEvents();
    }

    public List<String> goToJailNoGoSalary() {
        Player p = state.getCurrentPlayer();
        p.sendToJail(config.getJailMaxTurns());
        state.setLandedTileIndex(p.getPosition());
        state.setPhase(TurnPhase.TURN_END);
        return List.of(p.getName() + " goes straight to JAIL (no GO salary).", "Action: END_TURN");
    }

    public List<String> awardGetOutOfJailFree(Card card) {
        Player p = state.getCurrentPlayer();

        // Remove from the correct deck while held
        if (card.getType() == CardType.CHANCE) {
            state.getChanceDeck().removeFromDeck(card);
        } else {
            state.getCommunityDeck().removeFromDeck(card);
        }

        p.addGetOutOfJailFreeCard(card);
        return List.of(p.getName() + " receives a Get Out of Jail Free card (" + card.getType() + ").");
    }

    private Card lastDrawnCard;

    public Card getLastDrawnCard() {
        return lastDrawnCard;
    }

    public List<String> payBank(int amount) {
        Player p = state.getCurrentPlayer();
        p.subtractCash(amount);
        updateDebtPhaseIfNeeded(p);
        return List.of(p.getName() + " pays the bank £" + amount + ".");
    }

    public List<String> receiveBank(int amount) {
        Player p = state.getCurrentPlayer();
        p.addCash(amount);
        return List.of(p.getName() + " receives £" + amount + " from the bank.");
    }

    public List<String> payEachOtherPlayer(int amountEach) {
        Player p = state.getCurrentPlayer();
        List<String> ev = new ArrayList<>();
        int payerIdx = state.getCurrentPlayerIndex();

        int total = 0;
        for (int i = 0; i < state.getPlayers().size(); i++) {
            if (i == payerIdx) continue;
            Player other = state.getPlayers().get(i);
            if (other.isBankrupt()) continue;
            other.addCash(amountEach);
            total += amountEach;
        }
        p.subtractCash(total);
        updateDebtPhaseIfNeeded(p);
        ev.add(p.getName() + " pays £" + amountEach + " to each other player (total £" + total + ").");
        return ev;
    }

    public List<String> collectFromEachOtherPlayer(int amountEach) {
        Player p = state.getCurrentPlayer();
        List<String> ev = new ArrayList<>();
        int receiverIdx = state.getCurrentPlayerIndex();

        int total = 0;
        for (int i = 0; i < state.getPlayers().size(); i++) {
            if (i == receiverIdx) continue;
            Player other = state.getPlayers().get(i);
            if (other.isBankrupt()) continue;
            other.subtractCash(amountEach);
            total += amountEach;
            updateDebtPhaseIfNeeded(other);
        }
        p.addCash(total);
        ev.add(p.getName() + " collects £" + amountEach + " from each other player (total £" + total + ").");
        return ev;
    }

    public List<String> payPerBuilding(int perHouse, int perHotel) {
        Player p = state.getCurrentPlayer();
        int playerIdx = state.getCurrentPlayerIndex();

        int houses = 0;
        int hotels = 0;

        for (var e : deedsByIndex.entrySet()) {
            int idx = e.getKey();
            Object deed = e.getValue();
            if (!(deed instanceof StreetDeed)) continue;

            PropertyState ps = state.getPropertyState(idx);
            if (ps.getOwnerPlayerIndex() != null && ps.getOwnerPlayerIndex() == playerIdx) {
                if (ps.hasHotel()) hotels++;
                else houses += ps.getHouses();
            }
        }

        int cost = houses * perHouse + hotels * perHotel;
        p.subtractCash(cost);
        updateDebtPhaseIfNeeded(p);
        return List.of(p.getName() + " pays building repairs: houses=" + houses + " (£" + perHouse + " each), hotels=" + hotels + " (£" + perHotel + " each). Total £" + cost + ".");
    }

    public List<String> advanceToNearestStationDoubleRent() {
        Player p = state.getCurrentPlayer();
        int start = p.getPosition();

        int dest = nearestForward(start, BoardDestinations.STATIONS);

        // passing GO?
        if (dest < start) {
            p.addCash(config.getSalaryForPassingGo());
        }

        p.setPosition(dest);
        state.setLandedTileIndex(dest);

        // If owned by someone else and not mortgaged -> pay double rent
        Object deed = deedsByIndex.get(dest);
        PropertyState ps = state.getPropertyState(dest);

        if (deed != null && ps.getOwnerPlayerIndex() != null && ps.getOwnerPlayerIndex() != state.getCurrentPlayerIndex() && !ps.isMortgaged()) {
            int baseRent = computeRent(dest, deed, ps);
            int doubleRent = baseRent * 2;

            Player owner = state.getPlayers().get(ps.getOwnerPlayerIndex());
            p.subtractCash(doubleRent);
            owner.addCash(doubleRent);
            updateDebtPhaseIfNeeded(p);

            if (state.getPhase() == TurnPhase.MUST_RESOLVE_DEBT) {
                return List.of(p.getName() + " advanced to nearest Station (" + dest + ") and paid DOUBLE rent £" + doubleRent + " to " + owner.getName() + ".",
                        "MUST RESOLVE DEBT (MORTGAGE).");
            }

            state.setPhase(TurnPhase.MANAGEMENT);
            return List.of(p.getName() + " advanced to nearest Station (" + dest + ") and paid DOUBLE rent £" + doubleRent + " to " + owner.getName() + ".");
        }

        // Otherwise treat as normal landing: may buy/auction etc.
        ActionResult res = afterLandingResolveOrPrompt(p, p.getName() + " advanced to nearest Station (" + dest + ").");
        return res.getEvents();
    }

    public List<String> advanceToNearestUtilitySpecialRent() {
        Player p = state.getCurrentPlayer();
        int start = p.getPosition();

        int dest = nearestForward(start, BoardDestinations.UTILITIES);

        if (dest < start) {
            p.addCash(config.getSalaryForPassingGo());
        }

        p.setPosition(dest);
        state.setLandedTileIndex(dest);

        Object deed = deedsByIndex.get(dest);
        PropertyState ps = state.getPropertyState(dest);

        // If owned by someone else and not mortgaged -> pay 10x dice roll
        if (deed instanceof UtilityDeed ud && ps.getOwnerPlayerIndex() != null && ps.getOwnerPlayerIndex() != state.getCurrentPlayerIndex() && !ps.isMortgaged()) {
            Dice.Roll roll = dice.roll2d6();
            state.setLastRollTotal(roll.total());

            int owed = roll.total() * 10;
            Player owner = state.getPlayers().get(ps.getOwnerPlayerIndex());

            p.subtractCash(owed);
            owner.addCash(owed);
            updateDebtPhaseIfNeeded(p);

            if (state.getPhase() == TurnPhase.MUST_RESOLVE_DEBT) {
                return List.of(p.getName() + " advanced to utility (" + dest + ") and rolled " + roll + ". Paid £" + owed + " (10x roll) to " + owner.getName() + ".",
                        "MUST RESOLVE DEBT (MORTGAGE).");
            }

            state.setPhase(TurnPhase.MANAGEMENT);
            return List.of(p.getName() + " advanced to utility (" + dest + ") and rolled " + roll + ". Paid £" + owed + " (10x roll) to " + owner.getName() + ".");
        }

        ActionResult res = afterLandingResolveOrPrompt(p, p.getName() + " advanced to nearest Utility (" + dest + ").");
        return res.getEvents();
    }

    public List<String> gambleThenMaybeJail(int threshold, int winAmount, int loseAmountAndJail) {
        Player p = state.getCurrentPlayer();
        Dice.Roll roll = dice.roll2d6();
        int total = roll.total();
        state.setLastRollTotal(total);

        if (total >= threshold) {
            p.addCash(winAmount);
            return List.of(p.getName() + " rolled " + roll + " (>= " + threshold + ") and wins £" + winAmount + ".");
        } else {
            p.subtractCash(loseAmountAndJail);
            updateDebtPhaseIfNeeded(p);
            p.sendToJail(config.getJailMaxTurns());
            state.setPhase(TurnPhase.TURN_END);

            return List.of(p.getName() + " rolled " + roll + " (< " + threshold + "), pays £" + loseAmountAndJail + " and goes straight to JAIL.",
                    (p.getCash() < 0 ? "MUST RESOLVE DEBT (MORTGAGE)." : ""),
                    "Action: END_TURN");
        }
    }

    private int nearestForward(int start, int[] candidates) {
        int best = candidates[0];
        int bestDist = Integer.MAX_VALUE;
        for (int c : candidates) {
            int dist = (c - start + Board.SIZE) % Board.SIZE;
            if (dist == 0) dist = Board.SIZE; // "next" one, not current
            if (dist < bestDist) {
                bestDist = dist;
                best = c;
            }
        }
        return best;
    }

    private ActionResult resolveChance() {
        Card c = state.getChanceDeck().drawTop();
        lastDrawnCard = c;

        var events = new java.util.ArrayList<String>();
        events.add("CHANCE: " + c.getText());
        events.addAll(c.getEffect().apply(this));
        return ActionResult.ok(events.toArray(new String[0]));
    }


    private ActionResult resolveCommunityChest() {
        Card c = state.getCommunityDeck().drawTop();
        lastDrawnCard = c;

        var events = new java.util.ArrayList<String>();
        events.add("COMMUNITY CHEST: " + c.getText());
        events.addAll(c.getEffect().apply(this));
        return ActionResult.ok(events.toArray(new String[0]));
    }

    // ------------------ TRADING (v2) ------------------

    private ActionResult handleProposeTrade(Object payload, boolean isCounter) {
        if (state.getStatus() == GameStatus.FINISHED) {
            return ActionResult.fail("Game is finished.");
        }

        if (!(payload instanceof TradeOffer offer)) {
            return ActionResult.fail((isCounter ? "COUNTER_TRADE" : "PROPOSE_TRADE") + " requires a TradeOffer payload.");
        }

        // Disallow proposing trades during active auction (optional; remove if you want totally free trading)
        if (state.getPhase() == TurnPhase.AUCTION_ACTIVE) {
            return ActionResult.fail("Trading is not allowed during an active auction.");
        }

        // If counter: must be in TRADE_RESPONSE and current player must be the receiver of the existing pending trade
        if (isCounter) {
            if (!state.hasPendingTrade() || state.getPhase() != TurnPhase.TRADE_RESPONSE) {
                return ActionResult.fail("No trade to counter.");
            }
            TradeOffer prev = state.getPendingTrade();
            int current = state.getCurrentPlayerIndex(); // receiver is currently controlling
            if (current != prev.getToPlayerIndex()) {
                return ActionResult.fail("Only the receiver can counter the pending trade.");
            }

            // Counter must flip roles (current becomes proposer)
            if (offer.getFromPlayerIndex() != current || offer.getToPlayerIndex() != prev.getFromPlayerIndex()) {
                return ActionResult.fail("Counter trade must be from receiver to original proposer.");
            }

            // Validate new offer
            ActionResult legality = validateTradeOffer(offer);
            if (!legality.isOk()) return legality;

            // Replace pending and switch control to the other player (original proposer) to respond immediately
            state.setPendingTrade(offer);
            state.beginTradeResponse(offer.getFromPlayerIndex(), offer.getToPlayerIndex());

            return ActionResult.ok(
                    "Counter-trade proposed.",
                    offer.toString(),
                    "Responder actions: ACCEPT_TRADE / REJECT_TRADE / COUNTER_TRADE / CANCEL_TRADE"
            );
        }

        // Normal propose: only the real current player may initiate
        int proposer = state.getCurrentPlayerIndex();
        if (offer.getFromPlayerIndex() != proposer) {
            return ActionResult.fail("Only the current player may initiate a trade.");
        }

        if (state.hasPendingTrade()) {
            return ActionResult.fail("There is already a pending trade. Resolve it first.");
        }

        ActionResult legality = validateTradeOffer(offer);
        if (!legality.isOk()) return legality;

        state.setPendingTrade(offer);

        // Immediate response: switch control to receiver temporarily
        state.beginTradeResponse(offer.getFromPlayerIndex(), offer.getToPlayerIndex());

        Player a = state.getPlayers().get(offer.getFromPlayerIndex());
        Player b = state.getPlayers().get(offer.getToPlayerIndex());

        return ActionResult.ok(
                a.getName() + " proposes a trade to " + b.getName() + ".",
                offer.toString(),
                "Responder actions: ACCEPT_TRADE / REJECT_TRADE / COUNTER_TRADE. Proposer may CANCEL_TRADE."
        );
    }

    private ActionResult handleTradeResponse(TradeResponse response) {
        if (!state.hasPendingTrade()) return ActionResult.fail("No pending trade.");

        TradeOffer offer = state.getPendingTrade();
        int proposer = offer.getFromPlayerIndex();
        int receiver = offer.getToPlayerIndex();
        int current = state.getCurrentPlayerIndex(); // during TRADE_RESPONSE this is the responder

        if (state.getPhase() != TurnPhase.TRADE_RESPONSE) {
            return ActionResult.fail("Not currently in TRADE_RESPONSE phase.");
        }

        if (response == TradeResponse.CANCEL) {
            if (current != proposer) return ActionResult.fail("Only the proposer may cancel the trade.");
            state.clearPendingTrade();
            state.endTradeResponse();
            return ActionResult.ok("Trade cancelled by proposer.");
        }

        // ACCEPT / REJECT done by receiver
        if (current != receiver) return ActionResult.fail("Only the receiver may accept/reject the trade.");

        if (response == TradeResponse.REJECT) {
            state.clearPendingTrade();
            state.endTradeResponse();
            return ActionResult.ok("Trade rejected.");
        }

        // ACCEPT: revalidate and execute
        ActionResult legality = validateTradeOffer(offer);
        if (!legality.isOk()) {
            state.clearPendingTrade();
            state.endTradeResponse();
            return ActionResult.fail("Trade became illegal: " + String.join(" | ", legality.getEvents()));
        }

        List<String> events = executeTradeWithMortgageRulesAndCards(offer);

        state.clearPendingTrade();
        state.endTradeResponse();

        return ActionResult.ok(events.toArray(new String[0]));
    }

    private ActionResult validateTradeOffer(TradeOffer offer) {
        int aIdx = offer.getFromPlayerIndex();
        int bIdx = offer.getToPlayerIndex();

        if (aIdx < 0 || aIdx >= state.getPlayers().size()) return ActionResult.fail("Invalid proposer index.");
        if (bIdx < 0 || bIdx >= state.getPlayers().size()) return ActionResult.fail("Invalid receiver index.");

        Player a = state.getPlayers().get(aIdx);
        Player b = state.getPlayers().get(bIdx);

        if (a.isBankrupt() || b.isBankrupt()) return ActionResult.fail("Bankrupt players cannot trade.");

        // v2: Properties must be undeveloped to trade (no buildings)
        for (int tile : offer.getTilesFromAtoB()) {
            ActionResult r = validateTransferableTile(aIdx, tile);
            if (!r.isOk()) return r;
        }
        for (int tile : offer.getTilesFromBtoA()) {
            ActionResult r = validateTransferableTile(bIdx, tile);
            if (!r.isOk()) return r;
        }

        // GOJF availability
        if (offer.getChanceGojfAtoB() > a.countGetOutOfJailFree(CardType.CHANCE)) return ActionResult.fail("Proposer lacks Chance GOJF.");
        if (offer.getCommunityGojfAtoB() > a.countGetOutOfJailFree(CardType.COMMUNITY_CHEST)) return ActionResult.fail("Proposer lacks Community GOJF.");
        if (offer.getChanceGojfBtoA() > b.countGetOutOfJailFree(CardType.CHANCE)) return ActionResult.fail("Receiver lacks Chance GOJF.");
        if (offer.getCommunityGojfBtoA() > b.countGetOutOfJailFree(CardType.COMMUNITY_CHEST)) return ActionResult.fail("Receiver lacks Community GOJF.");

        // Cash offered must be affordable
        if (offer.getCashFromAtoB() > a.getCash()) return ActionResult.fail("Proposer cannot afford cash offered.");
        if (offer.getCashFromBtoA() > b.getCash()) return ActionResult.fail("Receiver cannot afford cash offered.");

        // Mortgage transfer costs:
        // Recipient pays 10% immediately for any mortgaged property received.
        // If PAY_OFF_NOW chosen, also pays mortgage value now and mortgage becomes false.
        int extraCostToB = mortgageTransferImmediateCost(offer.getTilesFromAtoB(), true, offer);
        int extraCostToA = mortgageTransferImmediateCost(offer.getTilesFromBtoA(), false, offer);

        int aCashAfter = a.getCash()
                - offer.getCashFromAtoB() + offer.getCashFromBtoA()
                - extraCostToA; // A pays costs for mortgaged tiles it receives from B

        int bCashAfter = b.getCash()
                - offer.getCashFromBtoA() + offer.getCashFromAtoB()
                - extraCostToB; // B pays costs for mortgaged tiles it receives from A

        if (aCashAfter < 0) return ActionResult.fail("Trade would make proposer cash negative after mortgage fees/repayments.");
        if (bCashAfter < 0) return ActionResult.fail("Trade would make receiver cash negative after mortgage fees/repayments.");

        boolean exchangesTiles = !offer.getTilesFromAtoB().isEmpty() || !offer.getTilesFromBtoA().isEmpty();
        boolean exchangesCash = offer.getCashFromAtoB() > 0 || offer.getCashFromBtoA() > 0;
        boolean exchangesCards = offer.getChanceGojfAtoB() + offer.getCommunityGojfAtoB() + offer.getChanceGojfBtoA() + offer.getCommunityGojfBtoA() > 0;

        if (!exchangesTiles && !exchangesCash && !exchangesCards) return ActionResult.fail("Trade must exchange something.");

        return ActionResult.ok("Trade is legal.");
    }

    private ActionResult validateTransferableTile(int ownerIdx, int tileIndex) {
        Object deed = deedsByIndex.get(tileIndex);
        if (deed == null) return ActionResult.fail("Tile " + tileIndex + " is not a tradable deed.");

        PropertyState ps = state.getPropertyState(tileIndex);
        if (ps.getOwnerPlayerIndex() == null || ps.getOwnerPlayerIndex() != ownerIdx) {
            return ActionResult.fail("Tile " + tileIndex + " is not owned by the offering player.");
        }

        // Undeveloped only
        if (deed instanceof StreetDeed) {
            if (ps.getBuildings() > 0) return ActionResult.fail("Tile " + tileIndex + " has buildings and cannot be traded.");
        }

        // Mortgaged is allowed in v2 (handled by mortgage transfer rules)
        return ActionResult.ok("Tile transferable.");
    }

    private int mortgageTransferImmediateCost(java.util.Set<Integer> tilesBeingReceived, boolean goingToB, TradeOffer offer) {
        int total = 0;
        for (int tile : tilesBeingReceived) {
            PropertyState ps = state.getPropertyState(tile);
            if (!ps.isMortgaged()) continue;

            Object deed = deedsByIndex.get(tile);
            int mortgage = getMortgageValue(deed);
            int tenPercent = (mortgage + 9) / 10; // ceil(mortgage*0.10)

            total += tenPercent;

            MortgageTransferChoice choice = goingToB ? offer.choiceForTileGoingToB(tile) : offer.choiceForTileGoingToA(tile);
            if (choice == MortgageTransferChoice.PAY_OFF_NOW) {
                total += mortgage; // repay mortgage now
            }
        }
        return total;
    }

    private List<String> executeTradeWithMortgageRulesAndCards(TradeOffer offer) {
        int aIdx = offer.getFromPlayerIndex();
        int bIdx = offer.getToPlayerIndex();

        Player a = state.getPlayers().get(aIdx);
        Player b = state.getPlayers().get(bIdx);

        List<String> ev = new ArrayList<>();
        ev.add("Trade executed: " + offer);

        // Cash transfer
        if (offer.getCashFromAtoB() > 0) {
            a.subtractCash(offer.getCashFromAtoB());
            b.addCash(offer.getCashFromAtoB());
            ev.add(a.getName() + " pays £" + offer.getCashFromAtoB() + " to " + b.getName() + ".");
        }
        if (offer.getCashFromBtoA() > 0) {
            b.subtractCash(offer.getCashFromBtoA());
            a.addCash(offer.getCashFromBtoA());
            ev.add(b.getName() + " pays £" + offer.getCashFromBtoA() + " to " + a.getName() + ".");
        }

        // Tiles A->B
        for (int tile : offer.getTilesFromAtoB()) {
            handleMortgageTransferOnReceive(tile, b, true, offer);
            state.getPropertyState(tile).setOwnerPlayerIndex(bIdx);
            ev.add("Tile " + tile + " transferred A->B.");
        }

        // Tiles B->A
        for (int tile : offer.getTilesFromBtoA()) {
            handleMortgageTransferOnReceive(tile, a, false, offer);
            state.getPropertyState(tile).setOwnerPlayerIndex(aIdx);
            ev.add("Tile " + tile + " transferred B->A.");
        }

        // GOJF cards
        for (int i = 0; i < offer.getChanceGojfAtoB(); i++) {
            Card c = a.removeOneGetOutOfJailFree(CardType.CHANCE);
            if (c != null) { b.addGetOutOfJailFreeCard(c); ev.add("Chance GOJF transferred A->B."); }
        }
        for (int i = 0; i < offer.getCommunityGojfAtoB(); i++) {
            Card c = a.removeOneGetOutOfJailFree(CardType.COMMUNITY_CHEST);
            if (c != null) { b.addGetOutOfJailFreeCard(c); ev.add("Community GOJF transferred A->B."); }
        }
        for (int i = 0; i < offer.getChanceGojfBtoA(); i++) {
            Card c = b.removeOneGetOutOfJailFree(CardType.CHANCE);
            if (c != null) { a.addGetOutOfJailFreeCard(c); ev.add("Chance GOJF transferred B->A."); }
        }
        for (int i = 0; i < offer.getCommunityGojfBtoA(); i++) {
            Card c = b.removeOneGetOutOfJailFree(CardType.COMMUNITY_CHEST);
            if (c != null) { a.addGetOutOfJailFreeCard(c); ev.add("Community GOJF transferred B->A."); }
        }

        // Safety: debt phase (your engine rules already prohibit ending negative; trades shouldn’t create negatives)
        updateDebtPhaseIfNeeded(a);
        updateDebtPhaseIfNeeded(b);

        ev.add(a.getName() + " cash now £" + a.getCash() + "; " + b.getName() + " cash now £" + b.getCash() + ".");
        return ev;
    }

    private void handleMortgageTransferOnReceive(int tile, Player receiver, boolean goingToB, TradeOffer offer) {
        PropertyState ps = state.getPropertyState(tile);
        if (!ps.isMortgaged()) return;

        Object deed = deedsByIndex.get(tile);
        int mortgage = getMortgageValue(deed);
        int tenPercent = (mortgage + 9) / 10;

        // recipient must pay 10% immediately
        receiver.subtractCash(tenPercent);

        MortgageTransferChoice choice = goingToB ? offer.choiceForTileGoingToB(tile) : offer.choiceForTileGoingToA(tile);

        if (choice == MortgageTransferChoice.PAY_OFF_NOW) {
            receiver.subtractCash(mortgage);
            ps.setMortgaged(false);
        } else {
            ps.setMortgaged(true);
        }
    }



}






