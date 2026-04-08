package pro.uno;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class GameSnapshot {

    public static final class Opponent {
        public final int id;
        public final String name;
        public final boolean unoCalled;
        public final int cardCount;

        Opponent(int id, String name, boolean unoCalled, int cardCount) {
            this.id = id;
            this.name = name;
            this.unoCalled = unoCalled;
            this.cardCount = cardCount;
        }
    }

    public final boolean started;
    public final int myId;
    public final int currentTurnId;
    public final String topCard;
    public final String currentColor;
    public final String status;
    public final List<String> myHand;
    public final List<Opponent> opponents;

    private GameSnapshot(
            boolean started,
            int myId,
            int currentTurnId,
            String topCard,
            String currentColor,
            String status,
            List<String> myHand,
            List<Opponent> opponents
    ) {
        this.started = started;
        this.myId = myId;
        this.currentTurnId = currentTurnId;
        this.topCard = topCard;
        this.currentColor = currentColor;
        this.status = status;
        this.myHand = myHand;
        this.opponents = opponents;
    }

    public static GameSnapshot fromJson(String json, int fallbackMyId) throws Exception {
        JSONObject root = new JSONObject(json);

        ArrayList<String> hand = new ArrayList<>();
        JSONArray handArray = root.optJSONArray("myHand");
        if (handArray != null) {
            for (int i = 0; i < handArray.length(); i++) {
                String card = CardFormatter.normalize(handArray.optString(i, ""));
                if (!card.isEmpty()) {
                    hand.add(card);
                }
            }
        }

        ArrayList<Opponent> opponents = new ArrayList<>();
        int myId = root.optInt("youId", fallbackMyId);
        JSONArray players = root.optJSONArray("players");
        if (players != null) {
            for (int i = 0; i < players.length(); i++) {
                JSONObject player = players.optJSONObject(i);
                if (player == null) {
                    continue;
                }

                int id = player.optInt("id", -1);
                if (id == myId) {
                    continue;
                }

                opponents.add(new Opponent(
                        id,
                        player.optString("name", "Player " + id),
                        player.optBoolean("uno", false),
                        player.optInt("cards", 0)
                ));
            }
        }

        return new GameSnapshot(
                root.optBoolean("started", false),
                myId,
                root.optInt("currentTurnId", -1),
                CardFormatter.normalize(root.optString("topCard", "")),
                root.optString("currentColor", ""),
                root.optString("status", ""),
                Collections.unmodifiableList(hand),
                Collections.unmodifiableList(opponents)
        );
    }
}
