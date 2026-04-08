package pro.uno;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class LobbySnapshot {

    public static final class Player {
        public final int id;
        public final String name;
        public final boolean ready;

        Player(int id, String name, boolean ready) {
            this.id = id;
            this.name = name;
            this.ready = ready;
        }
    }

    public final boolean started;
    public final int myId;
    public final int hostId;
    public final int desiredPlayers;
    public final boolean allConnected;
    public final boolean allReady;
    public final String status;
    public final List<Player> players;

    private LobbySnapshot(
            boolean started,
            int myId,
            int hostId,
            int desiredPlayers,
            boolean allConnected,
            boolean allReady,
            String status,
            List<Player> players
    ) {
        this.started = started;
        this.myId = myId;
        this.hostId = hostId;
        this.desiredPlayers = desiredPlayers;
        this.allConnected = allConnected;
        this.allReady = allReady;
        this.status = status;
        this.players = players;
    }

    public static LobbySnapshot fromJson(String json, int fallbackMyId, int fallbackDesiredPlayers) throws Exception {
        JSONObject root = new JSONObject(json);
        JSONArray playerArray = root.optJSONArray("players");
        ArrayList<Player> players = new ArrayList<>();

        if (playerArray != null) {
            for (int i = 0; i < playerArray.length(); i++) {
                JSONObject player = playerArray.optJSONObject(i);
                if (player == null) {
                    continue;
                }
                players.add(new Player(
                        player.optInt("id", -1),
                        player.optString("name", "Player " + (i + 1)),
                        player.optBoolean("ready", false)
                ));
            }
        }

        return new LobbySnapshot(
                root.optBoolean("started", false),
                root.optInt("youId", fallbackMyId),
                root.optInt("hostId", -1),
                root.optInt("desiredPlayers", fallbackDesiredPlayers),
                root.optBoolean("allConnected", false),
                root.optBoolean("allReady", false),
                root.optString("status", ""),
                Collections.unmodifiableList(players)
        );
    }
}
