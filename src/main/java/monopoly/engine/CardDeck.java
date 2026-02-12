package monopoly.engine;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

public class CardDeck<T> {
    private final ArrayDeque<T> queue = new ArrayDeque<>();
    private final List<T> allCards;
    private final Random random;

    public CardDeck(List<T> cards, Random random) {
        this.allCards = new ArrayList<>(cards);
        this.random = random;
        reshuffle();
    }

    public void reshuffle() {
        List<T> temp = new ArrayList<>(allCards);
        Collections.shuffle(temp, random);
        queue.clear();
        queue.addAll(temp);
    }

    public T drawTop() {
        if (queue.isEmpty()) reshuffle();
        T card = queue.removeFirst();
        queue.addLast(card); // put on bottom
        return card;
    }

    /** Removes a specific card from circulation (e.g., GOJF is held by a player). */
    public boolean removeFromDeck(T card) {
        boolean removed = allCards.remove(card);
        if (removed) queue.remove(card);
        return removed;
    }

    /** Returns a card to the deck (e.g., GOJF used). */
    public void returnToBottom(T card) {
        if (!allCards.contains(card)) {
            allCards.add(card);
        }
        queue.remove(card);
        queue.addLast(card);
    }
}

