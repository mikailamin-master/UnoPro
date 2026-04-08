package pro.uno;

import java.util.List;

import pro.uno.cards.Card;
import pro.uno.cards.CardColor;
import pro.uno.cards.CardRegistry;

public final class CardCatalog {

    public static final String[] STANDARD_COLORS = CardColor.STANDARD_COLORS;

    private CardCatalog() {
    }

    public static void buildStandardDeck(List<String> out) {
        CardRegistry.buildStandardDeck(out);
    }

    public static CardEffect effectFor(UnoCard card) {
        return card == null ? CardEffect.NONE : effectFor(card.toCard());
    }

    public static CardEffect effectFor(Card card) {
        return card == null ? CardEffect.NONE : card.getEffect();
    }

    public static boolean isValid(UnoCard card) {
        return card != null && CardRegistry.isValid(card.toCard());
    }

    public static boolean isStandardColor(String color) {
        return CardColor.isStandardColor(color);
    }
}
