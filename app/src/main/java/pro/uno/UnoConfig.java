package pro.uno;

/**
 * Centralized configuration constants for the UNO game.
 */
public final class UnoConfig {

    private UnoConfig() {
        // Prevent instantiation
    }

    // Networking
    public static final int TCP_PORT = 6000;
    public static final int DISCOVERY_PORT = 6001;
    public static final String DISCOVERY_REQUEST = "UNO_DISCOVER_REQUEST_V1";
    public static final String DISCOVERY_RESPONSE_PREFIX = "UNO_HOST_V1|";

    // Game Rules
    public static final int MIN_PLAYERS = 2;
    public static final int MAX_PLAYERS = 4;
    public static final int STARTING_HAND_SIZE = 7;
    public static final int PENALTY_CARD_COUNT = 2;

    // AI Configuration
    public static final int AI_TURN_DELAY_BASE_MS = 1200;
    public static final int AI_TURN_DELAY_RANDOM_MS = 800;

    // UI & Visuals
    public static final int MAX_DISCARD_STACK_HISTORY = 5;
    public static final int CARD_ANIMATION_DURATION_MS = 120;
    public static final int PLAYABLE_CARD_POPUP_DP = 20;
    public static final int MAX_PLAYER_NAME_LENGTH = 20;

    // Card Scores for AI
    public static final int SCORE_NORMAL_CARD = 10;
    public static final int SCORE_POWER_CARD = 5;
    public static final int SCORE_SUPER_CARD = 1;
}
