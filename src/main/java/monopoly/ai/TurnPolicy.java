package monopoly.ai;

import monopoly.engine.*;

public interface TurnPolicy {
    GameAction chooseAction(GameState state);
}

