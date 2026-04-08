package pro.uno.cards;

import java.util.List;

public final class CardRegistry {

    private CardRegistry() {
    }

    public static Card create(String rawCard) {
        String cardId = normalize(rawCard);
        String[] parts = cardId.split("_");
        if (parts.length < 3) {
            return new NormalCard("", "");
        }

        String family = parts[0];
        String color = parts[1];
        String value = parts[2];

        if ("card".equals(family)) {
            return new NormalCard(color, value);
        }

        if ("power".equals(family)) {
            if ("reverse".equals(value)) {
                return new CardReverse(color);
            }
            if ("block".equals(value)) {
                return new CardBlock(color);
            }
            if ("plus".equals(value)) {
                return new CardPlusTwo(color);
            }
        }

        if ("super".equals(family)) {
            if ("color".equals(value)) {
                return new CardColorChange();
            }
            if ("plus".equals(value)) {
                return new CardPlusFour();
            }
        }

        return new NormalCard("", "");
    }

    public static String normalize(String card) {
        if (card == null) {
            return "";
        }

        String trimmed = card.trim().toLowerCase();
        String[] parts = trimmed.split("_");
        if (parts.length < 3) {
            return trimmed;
        }

        String family = parts[0];
        String color = parts[1];
        String value = parts[2];

        if ("super".equals(family)) {
            return "super_black_" + value;
        }

        if (("card".equals(family) || "power".equals(family)) && "black".equals(color)) {
            return "";
        }

        return family + "_" + color + "_" + value;
    }

    public static void buildStandardDeck(List<String> out) {
        for (String color : CardColor.STANDARD_COLORS) {
            out.add(new NormalCard(color, "0").id());
            for (int number = 1; number <= 9; number++) {
                addCopies(out, new NormalCard(color, String.valueOf(number)).id(), 2);
            }

            addCopies(out, new CardReverse(color).id(), 2);
            addCopies(out, new CardBlock(color).id(), 2);
            addCopies(out, new CardPlusTwo(color).id(), 2);
        }

        addCopies(out, new CardColorChange().id(), 4);
        addCopies(out, new CardPlusFour().id(), 4);
    }

    public static boolean isValid(Card card) {
        if (card == null) {
            return false;
        }

        if (card.isWild()) {
            return "black".equals(card.getColor())
                    && ("plus".equals(card.getValue()) || "color".equals(card.getValue()));
        }

        if ("power".equals(card.getFamily())) {
            return CardColor.isStandardColor(card.getColor())
                    && ("plus".equals(card.getValue())
                    || "reverse".equals(card.getValue())
                    || "block".equals(card.getValue()));
        }

        if (!"card".equals(card.getFamily()) || !CardColor.isStandardColor(card.getColor())) {
            return false;
        }

        if ("0".equals(card.getValue())) {
            return true;
        }

        try {
            int number = Integer.parseInt(card.getValue());
            return number >= 1 && number <= 9;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    public static boolean isPlayable(String candidateCardId, String topCardId, String activeColor) {
        Card candidate = create(candidateCardId);
        if (!isValid(candidate)) {
            return false;
        }

        if (topCardId == null || topCardId.isEmpty()) {
            return true;
        }

        Card topCard = create(topCardId);
        if (!isValid(topCard)) {
            return false;
        }

        return candidate.canPlayOn(topCard, activeColor);
    }

    private static void addCopies(List<String> out, String cardId, int copies) {
        for (int i = 0; i < copies; i++) {
            out.add(cardId);
        }
    }
}
