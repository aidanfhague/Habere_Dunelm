package monopoly.model;

import monopoly.engine.Card;
import monopoly.engine.Card;
import java.util.ArrayList;
import java.util.List;


public class Player {
    private final String name;
    private int position; // 0..39
    private int cash;     // in £
    private boolean inJail;
    private int jailTurnsRemaining; // typically 3 max attempts
    private final List<Card> getOutOfJailFreeCards = new ArrayList<>();
    private boolean bankrupt;

    public Player(String name, int startingCash) {
        this.name = name;
        this.cash = startingCash;
        this.position = 0;
    }

    public String getName() {
        return name;
    }

    public int getPosition() {
        return position;
    }

    public int getCash() {
        return cash;
    }

    public void setPosition(int position) {
        this.position = position;
    }

    public void addCash(int amount) {
        this.cash += amount;
    }

    public void subtractCash(int amount) {
        this.cash -= amount;
        // later: handle bankruptcy if cash < 0
    }

    public boolean isInJail() {
        return inJail;
    }

    public void sendToJail(int jailTurns) {
        this.inJail = true;
        this.jailTurnsRemaining = jailTurns;
        this.position = 10; // standard Jail index (you can config later)
    }

    public void releaseFromJail() {
        this.inJail = false;
        this.jailTurnsRemaining = 0;
    }

    public int getJailTurnsRemaining() {
        return jailTurnsRemaining;
    }

    public void decrementJailTurn() {
        if (jailTurnsRemaining > 0) jailTurnsRemaining--;
    }

    public boolean hasGetOutOfJailFreeCard() {
        return !getOutOfJailFreeCards.isEmpty();
    }

    public void addGetOutOfJailFreeCard(Card card) {
        getOutOfJailFreeCards.add(card);
    }


    public Card useGetOutOfJailFreeCard() {
        if (getOutOfJailFreeCards.isEmpty()) return null;
        return getOutOfJailFreeCards.remove(0);
    }

    public int countGetOutOfJailFree(monopoly.engine.CardType type) {
        int c = 0;
        for (var card : getOutOfJailFreeCards) {   // adjust to your actual field name
            if (card.getType() == type) c++;
        }
        return c;
    }

    public monopoly.engine.Card removeOneGetOutOfJailFree(monopoly.engine.CardType type) {
        for (int i = 0; i < getOutOfJailFreeCards.size(); i++) {  // adjust list name
            var card = getOutOfJailFreeCards.get(i);
            if (card.getType() == type) {
                getOutOfJailFreeCards.remove(i);
                return card;
            }
        }
        return null;
    }

    public boolean isBankrupt() { return bankrupt; }
    public void setBankrupt(boolean b) { bankrupt = b; }

}
