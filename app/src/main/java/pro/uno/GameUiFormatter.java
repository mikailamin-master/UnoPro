package pro.uno;

import android.content.Context;

public final class GameUiFormatter {

    private GameUiFormatter() {
    }

    public static String buildOpponentCardsText(Context context, int cardCount, boolean unoCalled) {
        String unit = context.getString(cardCount == 1 ? R.string.card_count_singular : R.string.card_count_plural);
        return context.getString(
                unoCalled ? R.string.status_player_cards_count_uno : R.string.status_player_cards_count,
                cardCount,
                unit
        );
    }
}
