package pro.uno.cards;

import pro.uno.CardEffect;

public abstract class Card {

    private final String family;
    private final String color;
    private final String value;

    protected Card(String family, String color, String value) {
        this.family = family == null ? "" : family;
        this.color = color == null ? "" : color;
        this.value = value == null ? "" : value;
    }

    public final String getFamily() {
        return family;
    }

    public final String getColor() {
        return color;
    }

    public final String getValue() {
        return value;
    }

    public final String id() {
        return family + "_" + color + "_" + value;
    }

    public boolean isWild() {
        return "super".equals(family);
    }

    public boolean requiresColorChoice() {
        return isWild();
    }

    public CardEffect getEffect() {
        return CardEffect.NONE;
    }

    public boolean canPlayOn(Card topCard, String activeColor) {
        if (isWild()) {
            return true;
        }

        if (topCard == null || !CardRegistry.isValid(topCard)) {
            return true;
        }

        String topColor = CardColor.isStandardColor(activeColor) ? activeColor : topCard.getColor();
        return color.equals(topColor) || value.equals(topCard.getValue());
    }

    public void onCardPlayed(CardActionContext context) {
    }

    public void onCardDrawedToPlayer(CardActionContext context) {
    }

    public void onPlayerTakeTheCard(CardActionContext context) {
    }
}
