package monopoly.engine;

import java.util.List;

@FunctionalInterface
public interface CardEffect {
    /** Apply effect and return list of event strings for logging */
    List<String> apply(GameEngine engine);
}

