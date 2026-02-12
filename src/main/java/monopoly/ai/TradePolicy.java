package monopoly.ai;

import monopoly.engine.GameState;
import monopoly.engine.GameAction;
import monopoly.engine.trade.TradeOffer;

public interface TradePolicy {
    // For the current player: should we propose a trade now?
    TradeOffer maybePropose(GameState state);

    // For the responder: accept/reject/counter
    GameAction respond(GameState state, TradeOffer pending);
}

