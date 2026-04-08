package pro.uno.cards;

import pro.uno.CardEffect;

public final class CardPlusTwo extends Card {

    public CardPlusTwo(String color) {
        super("power", color, "plus");
    }

    @Override
    public CardEffect getEffect() {
        return CardEffect.DRAW_TWO;
    }

    @Override
    public void onCardPlayed(CardActionContext context) {
        context.give_card_to_next_player(2);
        context.skip_next_player();
    }
}
