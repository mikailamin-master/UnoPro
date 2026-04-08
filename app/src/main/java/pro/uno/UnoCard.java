package pro.uno;

import pro.uno.cards.Card;
import pro.uno.cards.CardRegistry;

public final class UnoCard {

    public final String family;
    public final String color;
    public final String value;
    private final Card card;

    private UnoCard(Card card) {
        this.card = card;
        this.family = card.getFamily();
        this.color = card.getColor();
        this.value = card.getValue();
    }

    public static UnoCard from(String rawCard) {
        return new UnoCard(CardRegistry.create(rawCard));
    }

    public boolean isWild() {
        return card.isWild();
    }

    public String id() {
        return card.id();
    }

    public Card toCard() {
        return card;
    }
}
