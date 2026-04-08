package pro.uno.cards;

import pro.uno.CardEffect;

public final class CardColorChange extends Card {

    public CardColorChange() {
        super("super", "black", "color");
    }

    @Override
    public CardEffect getEffect() {
        return CardEffect.WILD_COLOR;
    }

    @Override
    public void onCardPlayed(CardActionContext context) {
        context.change_the_top_card_color(context.getChosenColor());
    }
}
