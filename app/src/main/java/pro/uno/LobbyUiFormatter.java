package pro.uno;

import android.content.Context;

public final class LobbyUiFormatter {

    private LobbyUiFormatter() {
    }

    public static String buildPlayerLabel(Context context, String name, int playerId, int hostId, boolean ready) {
        String label = name;
        String idLabel = context.getString(R.string.label_player_id, playerId);
        if (playerId == hostId) {
            label = context.getString(R.string.label_host_suffix, label);
        }
        return context.getString(ready ? R.string.label_player_ready : R.string.label_player_waiting, label, idLabel);
    }

    public static String buildStartButtonLabel(Context context, boolean allConnected, boolean allReady) {
        if (!allConnected) {
            return context.getString(R.string.waiting_for_players);
        }
        if (!allReady) {
            return context.getString(R.string.waiting_for_ready);
        }
        return context.getString(R.string.start_game);
    }
}
