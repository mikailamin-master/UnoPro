package pro.uno.cards;

import pro.uno.CardEffect;

public final class CardBlock extends Card {

    public CardBlock(String color) {
        super("power", color, "block");
    }

    @Override
    public CardEffect getEffect() {
        return CardEffect.SKIP;
    }

    @Override
    public void onCardPlayed(CardActionContext context) {
        context.skip_next_player();
    }
}
