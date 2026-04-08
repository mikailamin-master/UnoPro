package pro.uno;

import android.content.Context;

import pro.uno.cards.Card;
import pro.uno.cards.CardRegistry;

public final class CardFormatter {

    private CardFormatter() {
    }

    public static String normalize(String card) {
        return CardRegistry.normalize(card);
    }

    public static String humanize(Context context, String card) {
        String normalized = normalize(card);
        if (normalized.isEmpty()) {
            return context.getString(R.string.top_none);
        }

        if ("super_black_plus".equals(normalized)) {
            return context.getString(R.string.card_super_plus);
        }

        if ("super_black_color".equals(normalized)) {
            return context.getString(R.string.card_super_color);
        }

        Card parsedCard = CardRegistry.create(normalized);
        return parsedCard.getColor().isEmpty()
                ? normalized.replace("_", " ")
                : parsedCard.getColor() + " " + parsedCard.getValue();
    }

    public static boolean isWild(String card) {
        return card != null && card.startsWith("super_");
    }

    public static String buildTopCardStatusText(Context context, String topCard, String currentColor) {
        String label = humanize(context, topCard);
        if (isWild(topCard)) {
            return context.getString(R.string.status_top_card_active_color, label, currentColor);
        }
        return context.getString(R.string.status_top_card, label, currentColor);
    }

    public static String buildTurnStatusText(Context context, boolean started, int currentTurnId, int myId, String status) {
        String suffix = status == null ? "" : status.trim();
        if (!started) {
            return suffix;
        }

        if (myId > 0 && currentTurnId == myId) {
            String prefix = context.getString(R.string.status_turn_you);
            return suffix.isEmpty() ? prefix : prefix + " " + suffix;
        }

        if (currentTurnId > 0) {
            String prefix = context.getString(R.string.status_turn_player, currentTurnId);
            return suffix.isEmpty() ? prefix : prefix + " " + suffix;
        }

        return suffix;
    }
}
