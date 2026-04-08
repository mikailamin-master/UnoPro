package pro.uno.cards;

public final class CardActionContext {

    private final Card card;
    private final int currentPlayerId;
    private final int playerCount;
    private final String chosenColor;

    private int nextPlayerDrawCount = 0;
    private boolean skipNextPlayer = false;
    private boolean reverseDirection = false;
    private String topCardColorOverride = "";

    public CardActionContext(Card card, int currentPlayerId, int playerCount, String chosenColor) {
        this.card = card;
        this.currentPlayerId = currentPlayerId;
        this.playerCount = playerCount;
        this.chosenColor = chosenColor == null ? "" : chosenColor.trim().toLowerCase();
    }

    public Card getCard() {
        return card;
    }

    public int getCurrentPlayerId() {
        return currentPlayerId;
    }

    public int getPlayerCount() {
        return playerCount;
    }

    public String getChosenColor() {
        return chosenColor;
    }

    public void give_card_to_next_player(int totalCard) {
        giveCardToNextPlayer(totalCard);
    }

    public void giveCardToNextPlayer(int totalCard) {
        if (totalCard > 0) {
            nextPlayerDrawCount += totalCard;
        }
    }

    public void change_the_top_card_color(int color) {
        changeTheTopCardColor(CardColor.fromIndex(color));
    }

    public void change_the_top_card_color(String color) {
        changeTheTopCardColor(color);
    }

    public void changeTheTopCardColor(int color) {
        changeTheTopCardColor(CardColor.fromIndex(color));
    }

    public void changeTheTopCardColor(String color) {
        if (CardColor.isStandardColor(color)) {
            topCardColorOverride = color;
        }
    }

    public void skip_next_player() {
        skipNextPlayer();
    }

    public void skipNextPlayer() {
        skipNextPlayer = true;
    }

    public void reverse_play_direction() {
        reversePlayDirection();
    }

    public void reversePlayDirection() {
        reverseDirection = true;
    }

    public int getNextPlayerDrawCount() {
        return nextPlayerDrawCount;
    }

    public boolean shouldSkipNextPlayer() {
        return skipNextPlayer;
    }

    public boolean shouldReverseDirection() {
        return reverseDirection;
    }

    public String getTopCardColorOverride() {
        return topCardColorOverride;
    }

    public boolean hasColorOverride() {
        return !topCardColorOverride.isEmpty();
    }

    public String resolveTopCardColor(String fallbackColor) {
        return hasColorOverride() ? topCardColorOverride : fallbackColor;
    }
}
