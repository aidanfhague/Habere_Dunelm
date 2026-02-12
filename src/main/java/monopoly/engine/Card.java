package monopoly.engine;

public class Card {
    private final CardType type;
    private final String text;
    private final CardEffect effect;
    private final boolean isGetOutOfJailFree;

    public Card(CardType type, String text, CardEffect effect, boolean isGetOutOfJailFree) {
        this.type = type;
        this.text = text;
        this.effect = effect;
        this.isGetOutOfJailFree = isGetOutOfJailFree;
    }

    public CardType getType() { return type; }
    public String getText() { return text; }
    public CardEffect getEffect() { return effect; }
    public boolean isGetOutOfJailFree() { return isGetOutOfJailFree; }
}
