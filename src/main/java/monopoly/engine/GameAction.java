package monopoly.engine;

import java.util.Objects;

/**
 * Action sent to the GameEngine.
 * Compatible with both record-style accessors (type()) and getter-style (getType()).
 */
public record GameAction(GameActionType type, Integer tileIndex, Integer amount, Object payload) {

    public GameAction {
        Objects.requireNonNull(type, "GameAction.type must not be null");
    }

    // --- Getter-style aliases (so both calling conventions work) ---
    public GameActionType getType() { return type; }
    public Integer getTileIndex() { return tileIndex; }
    public Integer getAmount() { return amount; }

    // --- Convenience factories ---
    public static GameAction simple(GameActionType type) {
        return new GameAction(type, null, null, null);
    }

    public static GameAction onTile(GameActionType type, int tileIndex) {
        return new GameAction(type, tileIndex, null, null);
    }

    public static GameAction bid(int amount) {
        return new GameAction(GameActionType.AUCTION_BID, null, amount, null);
    }

    public static GameAction withPayload(GameActionType type, Object payload) {
        return new GameAction(type, null, null, payload);
    }
}



