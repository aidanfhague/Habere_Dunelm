package monopoly;

import monopoly.engine.*;
import monopoly.model.Player;
import monopoly.setup.StandardBoardFactory;
import monopoly.ai.TurnPolicy;
import monopoly.ai.TradePolicy;
import monopoly.ai.BuildAdvisor;

import java.util.List;

public class Main {
    // --- Policies -----
    private static TurnPolicy[] TURN_POLICIES;
    private static TradePolicy[] TRADE_POLICIES;

    private static TurnPolicy turnPolicyFor(int playerIndex) {
        return TURN_POLICIES[playerIndex];
    }

    private static TradePolicy tradePolicyFor(int playerIndex) {
        return TRADE_POLICIES[playerIndex];
    }

    private static final class SimpleTurnPolicy implements monopoly.ai.TurnPolicy {
        private final monopoly.ai.BuildAdvisor buildAdvisor = new monopoly.ai.BuildAdvisor();

        @Override
        public GameAction chooseAction(GameState state) {
            TurnPhase ph = state.getPhase();

            // Always roll if needed
            if (ph == TurnPhase.MUST_ROLL || ph == TurnPhase.CAN_ROLL_AGAIN) {
                return GameAction.simple(GameActionType.ROLL_DICE);
            }

            // Landed decision: buy (engine enforces legality); you can make this smarter later
            if (ph == TurnPhase.LANDED_DECISION) {
                return GameAction.simple(GameActionType.BUY_PROPERTY);
            }

            // Build step (this is what you were missing)
            if (ph == TurnPhase.MANAGEMENT || ph == TurnPhase.TURN_END) {
                // NOTE: we need the engine to evaluate and perform the build,
                // but policies only get state. So we do the build suggestion in Main loop (recommended),
                // OR we give the policy access to engine.
                return GameAction.simple(GameActionType.END_TURN);
            }

            return GameAction.simple(GameActionType.END_TURN);
        }
    }

    private static GameAction maybeRandomPanicBuild(GameState state) {
        Player p = state.getCurrentPlayer();
        int me = state.getCurrentPlayerIndex();

        if (!(state.getPhase() == TurnPhase.MANAGEMENT || state.getPhase() == TurnPhase.TURN_END)) return null;

        // Find all legal BUILD_HOUSE targets (very simple filtering).
        java.util.List<Integer> candidates = new java.util.ArrayList<>();

        for (int idx = 0; idx < 40; idx++) {
            PropertyState ps = state.getPropertyState(idx);
            if (ps.getOwnerPlayerIndex() == null || ps.getOwnerPlayerIndex() != me) continue;
            if (ps.isMortgaged()) continue;
            if (ps.hasHotel()) continue;
            if (ps.getHouses() >= 4) continue;

            // We do not check full colour set / even-building here;
            // engine will enforce and reject. But we’ll keep only plausible candidates:
            candidates.add(idx);
        }

        if (candidates.isEmpty()) return null;

        // Choose a random candidate and attempt to build.
        int pick = candidates.get(new java.util.Random().nextInt(candidates.size()));
        return GameAction.onTile(GameActionType.BUILD_HOUSE, pick);
    }

    public static void main(String[] args) {
        GameConfig config = GameConfig.ukDefaults();
        var board = StandardBoardFactory.createBasic40TileBoard();

        List<Player> players = List.of(
                new Player("Alice", config.getStartingCash()),
                new Player("Bob", config.getStartingCash())
        );

        TURN_POLICIES = new TurnPolicy[] {
                new SimpleTurnPolicy(), // Alice
                new SimpleTurnPolicy()  // Bob
        };

        TRADE_POLICIES = new TradePolicy[] {
                new SimpleTradePolicy(), // Alice
                new SimpleTradePolicy()  // Bob
        };

        GameState state = new GameState(board, players);
        java.util.Random rng = new java.util.Random();
        state.setChanceDeck(new CardDeck<>(monopoly.setup.CardFactory.chanceCards(), rng));
        state.setCommunityDeck(new CardDeck<>(monopoly.setup.CardFactory.communityChestCards(), rng));
        GameEngine engine = new GameEngine(config, new Dice(), state);

        for (int turn = 1; turn <= 60 && state.getStatus() == GameStatus.RUNNING; turn++) {
            System.out.println("========== TURN " + turn + " ==========");
            printSnapshot(state);

            print(engine.startTurnIfNeeded());
            printSnapshot(state);

            // Drive the ENTIRE turn until the engine advances to next player
            int startingPlayer = state.getCurrentPlayerIndex();

            BuildAdvisor buildAdvisor = new BuildAdvisor();

            while (state.getStatus() == GameStatus.RUNNING && state.getCurrentPlayerIndex() == startingPlayer) {

                // 0) Always resolve debt first (critical!)
                if (state.getPhase() == TurnPhase.MUST_RESOLVE_DEBT) {
                    boolean mortgagedSomething = false;

                    while (state.getPhase() == TurnPhase.MUST_RESOLVE_DEBT && state.getStatus() == GameStatus.RUNNING) {
                        Integer mortgageIdx = findFirstMortgageCandidate(state);
                        if (mortgageIdx == null) break;

                        mortgagedSomething = true;
                        print(engine.apply(GameAction.onTile(GameActionType.MORTGAGE, mortgageIdx)));
                        printSnapshot(state);
                    }

                    // If still in debt after mortgaging everything possible:
                    // try END_TURN once; engine will either (a) reject with "mortgage more" or
                    // (b) bankrupt if it can't raise cash.
                    if (state.getPhase() == TurnPhase.MUST_RESOLVE_DEBT) {
                        print(engine.apply(GameAction.simple(GameActionType.END_TURN)));
                        printSnapshot(state);

                        // If it rejected and nothing was mortgaged, we're stuck unless engine bankrupts.
                        // Break to avoid infinite loop.
                        if (!mortgagedSomething && state.getPhase() == TurnPhase.MUST_RESOLVE_DEBT) {
                            System.out.println("Debt unresolved and no mortgage candidates. Stopping turn to avoid infinite loop.");
                            break;
                        }
                    }

                    continue; // re-check phase
                }

                // 1) Jail decision
                if (state.getPhase() == TurnPhase.IN_JAIL_DECISION) {
                    GameAction chosen = chooseJailAction(state, turn);
                    print(engine.apply(chosen));
                    printSnapshot(state);
                    continue;
                }

                // 2) Must roll (including doubles sequence)
                if (state.getPhase() == TurnPhase.MUST_ROLL || state.getPhase() == TurnPhase.CAN_ROLL_AGAIN) {
                    print(engine.apply(GameAction.simple(GameActionType.ROLL_DICE)));
                    printSnapshot(state);
                    continue;
                }

                // 3) Landed decision: BUY or AUCTION
                if (state.getPhase() == TurnPhase.LANDED_DECISION) {

                    // Try buy first; if engine rejects (insufficient cash / illegal), auction instead
                    ActionResult buyRes = engine.apply(GameAction.simple(GameActionType.BUY_PROPERTY));
                    print(buyRes);
                    printSnapshot(state);

                    if (!buyRes.isOk() && state.getPhase() == TurnPhase.LANDED_DECISION) {
                        print(engine.apply(GameAction.simple(GameActionType.START_AUCTION)));
                        printSnapshot(state);
                    }

                    continue;
                }

                // 4) Auction driver
                if (state.getPhase() == TurnPhase.AUCTION_ACTIVE) {
                    int bidderIdx = state.getAuctionCurrentBidderIndex();
                    int tileIdx = state.getAuctionTileIndex();
                    int currentHigh = state.getAuctionHighBid();

                    int maxBid = engine.estimateMaxBidHeuristic(bidderIdx, tileIdx);
                    int nextBid = currentHigh + 10;

                    if (nextBid <= maxBid) {
                        print(engine.apply(GameAction.bid(nextBid)));
                    } else {
                        print(engine.apply(GameAction.simple(GameActionType.AUCTION_PASS)));
                    }
                    printSnapshot(state);
                    continue;
                }

                // 5) Trade response (responder acts immediately)
                if (state.getPhase() == TurnPhase.TRADE_RESPONSE && state.hasPendingTrade()) {
                    var pending = state.getPendingTrade();
                    GameAction response = tradePolicyFor(state.getCurrentPlayerIndex()).respond(state, pending);
                    print(engine.apply(response));
                    printSnapshot(state);
                    continue;
                }

                // 6) Optional trade proposal during turn (proposer)
                var offer = tradePolicyFor(state.getCurrentPlayerIndex()).maybePropose(state);
                if (offer != null && !state.hasPendingTrade()) {
                    print(engine.apply(GameAction.withPayload(GameActionType.PROPOSE_TRADE, offer)));
                    printSnapshot(state);
                    continue;
                }

                // 7) Build step (try repeatedly, but only if in a sensible phase)
                if (state.getPhase() == TurnPhase.MANAGEMENT || state.getPhase() == TurnPhase.TURN_END) {
                    GameAction build = buildAdvisor.maybeBuild(state, engine);
                    if (build != null) {
                        print(engine.apply(build));
                        printSnapshot(state);
                        continue;
                    }
                }

                // --- Panic build rule: if a build would leave you < £200, attempt a random build ---
                if (state.getStatus() == GameStatus.RUNNING
                        && (state.getPhase() == TurnPhase.MANAGEMENT || state.getPhase() == TurnPhase.TURN_END)) {

                    GameAction buildTry = maybeRandomPanicBuild(state);
                    if (buildTry != null) {
                        ActionResult br = engine.apply(buildTry);

                        // Only keep it if it succeeded AND it left cash under £200 as you specified.
                        if (br.isOk() && state.getCurrentPlayer().getCash() < 200) {
                            print(br);
                            printSnapshot(state);
                        }
                    }
                }

                // 8) Otherwise end turn
                print(engine.apply(GameAction.simple(GameActionType.END_TURN)));
                printSnapshot(state);
            }

            System.out.println();
        }
    }

    private static final class SimpleTradePolicy implements TradePolicy {
        @Override
        public monopoly.engine.trade.TradeOffer maybePropose(GameState state) {
            return null; // no trade proposals in this minimal policy
        }

        @Override
        public GameAction respond(GameState state, monopoly.engine.trade.TradeOffer pending) {
            // Minimal: always reject
            return GameAction.simple(GameActionType.REJECT_TRADE);
        }
    }

    private static GameAction chooseJailAction(GameState state, int turnNumber) {
        Player p = state.getCurrentPlayer();

        if (p.hasGetOutOfJailFreeCard()
                && p.getCash() < 100
                && turnNumber < 25) {
            return GameAction.simple(GameActionType.USE_GET_OUT_OF_JAIL_FREE);
        }

        return GameAction.simple(GameActionType.ROLL_DICE);
    }



    private static Integer findFirstMortgageCandidate(GameState state) {
        int owner = state.getCurrentPlayerIndex();
        // naive scan of all 0..39: mortgage first owned unmortgaged tile
        for (int i = 0; i < 40; i++) {
            PropertyState ps = state.getPropertyState(i);
            if (ps.getOwnerPlayerIndex() != null && ps.getOwnerPlayerIndex() == owner && !ps.isMortgaged() && ps.getBuildings() == 0) {
                return i;
            }
        }
        return null;
    }

    private static void print(ActionResult r) {
        for (String e : r.getEvents()) System.out.println(e);
        if (!r.isOk()) System.out.println("(action rejected)");
    }

    private static void printSnapshot(GameState state) {
        System.out.println("Bank supply: houses=" + state.getHousesRemaining() + ", hotels=" + state.getHotelsRemaining());
        System.out.println("-- Snapshot -- phase=" + state.getPhase() + ", landed=" + state.getLandedTileIndex());
        for (Player p : state.getPlayers()) {
            String jail = p.isInJail() ? "JAIL(" + p.getJailTurnsRemaining() + ")" : "free";
            String bank = p.isBankrupt() ? "BANKRUPT" : "OK";
            System.out.println("  " + p.getName() + " | £" + p.getCash() + " | pos " + p.getPosition() + " | " + jail + " | " + bank);
        }
        System.out.println("-------------");
    }
}




