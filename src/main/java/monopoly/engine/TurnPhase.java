package monopoly.engine;

public enum TurnPhase {
    START_TURN,

    IN_JAIL_DECISION,

    MUST_ROLL,
    CAN_ROLL_AGAIN,

    LANDED_DECISION,
    AUCTION_ACTIVE,
    MANAGEMENT,          // optional actions: BUILD_*, MORTGAGE, etc.
    MUST_RESOLVE_DEBT,  // cash < 0 -> must MORTGAGE (and later SELL_HOUSE) until >= 0

    TRADE_RESPONSE,

    TURN_END
}



