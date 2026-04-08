package pro.uno.cards;

import pro.uno.CardEffect;

public final class CardReverse extends Card {

    public CardReverse(String color) {
        super("power", color, "reverse");
    }

    @Override
    public CardEffect getEffect() {
        return CardEffect.REVERSE;
    }

    @Override
    public void onCardPlayed(CardActionContext context) {
        if (context.getPlayerCount() <= 2) {
            context.skip_next_player();
            return;
        }
        context.reverse_play_direction();
    }
}
