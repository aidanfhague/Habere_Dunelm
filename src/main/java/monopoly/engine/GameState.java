package monopoly.engine;

import monopoly.model.Board;
import monopoly.model.Player;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class GameState {
    private final Board board;
    private final List<Player> players;

    private int currentPlayerIndex;

    private TurnPhase phase = TurnPhase.START_TURN;
    private int doublesThisTurn = 0;

    private Integer lastRollTotal = null;
    private Integer landedTileIndex = null;

    private final Map<Integer, PropertyState> propertyStates = new HashMap<>();

    private GameStatus status = GameStatus.RUNNING;
    private Integer winnerIndex = null;

    private int housesRemaining = 32;
    private int hotelsRemaining = 12;

    // Card decks (you already added these)
    private CardDeck<Card> chanceDeck;
    private CardDeck<Card> communityDeck;

    // ------------------ AUCTION STATE ------------------
    private boolean auctionInProgress = false;
    private Integer auctionTileIndex = null;
    private int auctionHighBid = 0;
    private Integer auctionHighBidderIndex = null;
    private boolean[] auctionActive;                 // who is still in
    private int auctionCurrentBidderIndex = 0;       // whose turn to act in auction
    // ---------------------------------------------------

    public GameState(Board board, List<Player> players) {
        if (players == null || players.size() < 2) throw new IllegalArgumentException("Need at least 2 players.");
        this.board = board;
        this.players = players;
        this.currentPlayerIndex = 0;
    }

    public Board getBoard() { return board; }
    public List<Player> getPlayers() { return players; }

    public Player getCurrentPlayer() { return players.get(currentPlayerIndex); }
    public int getCurrentPlayerIndex() { return currentPlayerIndex; }

    public TurnPhase getPhase() { return phase; }
    public void setPhase(TurnPhase phase) { this.phase = phase; }

    public int getDoublesThisTurn() { return doublesThisTurn; }
    public void resetDoublesThisTurn() { this.doublesThisTurn = 0; }
    public void incrementDoublesThisTurn() { this.doublesThisTurn++; }

    public Integer getLastRollTotal() { return lastRollTotal; }
    public void setLastRollTotal(Integer lastRollTotal) { this.lastRollTotal = lastRollTotal; }

    public Integer getLandedTileIndex() { return landedTileIndex; }
    public void setLandedTileIndex(Integer landedTileIndex) { this.landedTileIndex = landedTileIndex; }

    public PropertyState getPropertyState(int tileIndex) {
        return propertyStates.computeIfAbsent(tileIndex, k -> new PropertyState());
    }

    public GameStatus getStatus() { return status; }
    public Integer getWinnerIndex() { return winnerIndex; }

    public void setWinner(int winnerIndex) {
        this.status = GameStatus.FINISHED;
        this.winnerIndex = winnerIndex;
    }

    public int getHousesRemaining() { return housesRemaining; }
    public int getHotelsRemaining() { return hotelsRemaining; }

    public boolean takeHouseFromBank() {
        if (housesRemaining <= 0) return false;
        housesRemaining--;
        return true;
    }

    public void returnHousesToBank(int count) {
        housesRemaining += count;
        if (housesRemaining > 32) housesRemaining = 32;
    }

    public boolean takeHotelFromBank() {
        if (hotelsRemaining <= 0) return false;
        hotelsRemaining--;
        return true;
    }

    public void returnHotelToBank() {
        hotelsRemaining++;
        if (hotelsRemaining > 12) hotelsRemaining = 12;
    }

    public void returnHouseToBank() { housesRemaining++; }
    public void takeHousesFromBank(int n) { housesRemaining -= n; }

    public CardDeck<Card> getChanceDeck() { return chanceDeck; }
    public CardDeck<Card> getCommunityDeck() { return communityDeck; }
    public void setChanceDeck(CardDeck<Card> chanceDeck) { this.chanceDeck = chanceDeck; }
    public void setCommunityDeck(CardDeck<Card> communityDeck) { this.communityDeck = communityDeck; }

    // ------------------ AUCTION METHODS ------------------
    public boolean isAuctionInProgress() { return auctionInProgress; }
    public Integer getAuctionTileIndex() { return auctionTileIndex; }
    public int getAuctionHighBid() { return auctionHighBid; }
    public Integer getAuctionHighBidderIndex() { return auctionHighBidderIndex; }
    public int getAuctionCurrentBidderIndex() { return auctionCurrentBidderIndex; }

    public void startAuction(int tileIndex, int startingBidderIndex) {
        this.auctionInProgress = true;
        this.auctionTileIndex = tileIndex;
        this.auctionHighBid = 0;
        this.auctionHighBidderIndex = null;

        this.auctionActive = new boolean[players.size()];
        for (int i = 0; i < players.size(); i++) {
            auctionActive[i] = !players.get(i).isBankrupt();
        }
        this.auctionCurrentBidderIndex = startingBidderIndex;
    }

    public boolean isAuctionBidderActive(int playerIdx) {
        return auctionActive != null && auctionActive[playerIdx];
    }

    public void auctionPass(int playerIdx) {
        if (auctionActive != null) auctionActive[playerIdx] = false;
    }

    public int auctionActiveCount() {
        int c = 0;
        if (auctionActive == null) return 0;
        for (boolean b : auctionActive) if (b) c++;
        return c;
    }

    /** Advances to next active bidder (returns index). If none active, returns -1. */
    public int advanceToNextActiveBidder() {
        if (auctionActive == null) return -1;
        int n = players.size();
        for (int step = 1; step <= n; step++) {
            int idx = (auctionCurrentBidderIndex + step) % n;
            if (auctionActive[idx]) {
                auctionCurrentBidderIndex = idx;
                return idx;
            }
        }
        return -1;
    }

    public void setAuctionHighBid(int bid, int bidderIdx) {
        this.auctionHighBid = bid;
        this.auctionHighBidderIndex = bidderIdx;
    }

    public void endAuction() {
        this.auctionInProgress = false;
        this.auctionTileIndex = null;
        this.auctionHighBid = 0;
        this.auctionHighBidderIndex = null;
        this.auctionActive = null;
        this.auctionCurrentBidderIndex = 0;
    }
    // ---------------------------------------------------

    public void advanceTurnSkippingBankrupt() {
        int attempts = 0;
        do {
            currentPlayerIndex = (currentPlayerIndex + 1) % players.size();
            attempts++;
            if (attempts > players.size()) break;
        } while (players.get(currentPlayerIndex).isBankrupt());

        phase = TurnPhase.START_TURN;
        doublesThisTurn = 0;
        lastRollTotal = null;
        landedTileIndex = null;
    }

    // Trading Dynamics ---------------
    private monopoly.engine.trade.TradeOffer pendingTrade;
    private Integer tradeReturnPlayerIndex;      // where to return after response
    private TurnPhase phaseBeforeTrade;          // restore phase after trade

    public monopoly.engine.trade.TradeOffer getPendingTrade() { return pendingTrade; }
    public boolean hasPendingTrade() { return pendingTrade != null; }
    public void setPendingTrade(monopoly.engine.trade.TradeOffer offer) { this.pendingTrade = offer; }
    public void clearPendingTrade() { this.pendingTrade = null; }

    public boolean isTradeResponseInProgress() { return tradeReturnPlayerIndex != null; }

    /**
     * Start immediate trade response: control switches to receiver temporarily,
     * phase becomes TRADE_RESPONSE, and we remember the old phase and player.
     */
    public void beginTradeResponse(int proposerIdx, int receiverIdx) {
        this.tradeReturnPlayerIndex = proposerIdx;
        this.phaseBeforeTrade = this.getPhase();
        this.setCurrentPlayerIndex(receiverIdx);     // you must have a setter; if not, add one
        this.setPhase(TurnPhase.TRADE_RESPONSE);
    }

    /**
     * End trade response: restore turn control and phase.
     */
    public void endTradeResponse() {
        if (tradeReturnPlayerIndex == null) return;
        int backTo = tradeReturnPlayerIndex;
        this.tradeReturnPlayerIndex = null;
        TurnPhase restore = phaseBeforeTrade != null ? phaseBeforeTrade : TurnPhase.MANAGEMENT;
        this.phaseBeforeTrade = null;

        this.setCurrentPlayerIndex(backTo);
        this.setPhase(restore);
    }

    public void setCurrentPlayerIndex(int idx) { this.currentPlayerIndex = idx; }



}



