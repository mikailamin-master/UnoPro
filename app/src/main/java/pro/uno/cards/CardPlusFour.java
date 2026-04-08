package pro.uno.cards;

import pro.uno.CardEffect;

public final class CardPlusFour extends Card {

    public CardPlusFour() {
        super("super", "black", "plus");
    }

    @Override
    public CardEffect getEffect() {
        return CardEffect.WILD_DRAW_FOUR;
    }

    @Override
    public void onCardPlayed(CardActionContext context) {
        context.change_the_top_card_color(context.getChosenColor());
        context.give_card_to_next_player(4);
        context.skip_next_player();
    }
}
