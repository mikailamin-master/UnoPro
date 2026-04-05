package pro.uno;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;

import pro.uno.ui.card.CardViewFactory;

public class GameActivity extends Activity {

    public static final String EXTRA_SNAPSHOT = "snapshot";

    private LinearLayout top;
    private LinearLayout topCardPreview;
    private LinearLayout cardContainer;
    private LinearLayout drawCardBtn;
    private LinearLayout takeCardBtn;
    private LinearLayout unoCallBtn;

    private LinearLayout[] playerRows;
    private TextView[] playerNameTxt;
    private TextView[] playerCardsTxt;
    private LinearLayout[] penaltyBtns;

    private TextView statusTxt;
    private TextView topCardTxt;

    private View lastMarkedCard = null;
    private int markedCardId = -1;

    private final int[] opponentPlayerIds = new int[]{-1, -1, -1};
    private final boolean[] opponentUno = new boolean[]{false, false, false};
    private final int[] opponentCardCounts = new int[]{0, 0, 0};

    private final ArrayList<String> receivedCardList = new ArrayList<>();

    private ClientService client;
    private CardViewFactory cardViewFactory;

    private int myId = -1;
    private int currentTurnId = -1;
    private boolean started = false;
    private String topCard = "";
    private String currentColor = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.game_activity);
        cardViewFactory = new CardViewFactory(this);

        bindViews();
        setupButtons();
        bindClientListener();

        String snapshot = getIntent().getStringExtra(EXTRA_SNAPSHOT);
        if (snapshot != null && !snapshot.isEmpty()) {
            parseSnapshot(snapshot);
        } else {
            statusTxt.setText("Waiting for game state...");
            topCardTxt.setText("Top: none | Color: -");
        }
    }

    private void bindViews() {
        top = findViewById(R.id.top);
        topCardPreview = findViewById(R.id.top_card_preview);
        cardContainer = findViewById(R.id.card_container);
        drawCardBtn = findViewById(R.id.draw_card_btn);
        takeCardBtn = findViewById(R.id.take_card_btn);
        unoCallBtn = findViewById(R.id.uno_call_btn);

        playerRows = new LinearLayout[]{
                findViewById(R.id.player_1_container),
                findViewById(R.id.player_2_container),
                findViewById(R.id.player_3_container)
        };

        playerNameTxt = new TextView[]{
                findViewById(R.id.p1_name_txt),
                findViewById(R.id.p2_name_txt),
                findViewById(R.id.p3_name_txt)
        };

        playerCardsTxt = new TextView[]{
                findViewById(R.id.p1_cards_txt),
                findViewById(R.id.p2_cards_txt),
                findViewById(R.id.p3_cards_txt)
        };

        penaltyBtns = new LinearLayout[]{
                findViewById(R.id.penalty_p1),
                findViewById(R.id.penalty_p2),
                findViewById(R.id.penalty_p3)
        };

        statusTxt = new TextView(this);
        statusTxt.setTextSize(15f);
        statusTxt.setTextColor(getResources().getColor(android.R.color.white));
        statusTxt.setPadding(dpToPx(8), dpToPx(4), dpToPx(8), dpToPx(2));

        topCardTxt = new TextView(this);
        topCardTxt.setTextSize(13f);
        topCardTxt.setTextColor(getResources().getColor(android.R.color.white));
        topCardTxt.setPadding(dpToPx(8), dpToPx(2), dpToPx(8), dpToPx(4));

        top.addView(statusTxt);
        top.addView(topCardTxt);
    }

    private void bindClientListener() {
        client = ClientService.getInstance();
        if (!client.isConnected()) {
            goBackToLobby();
            return;
        }

        client.setClientListener(new ClientService.ClientListener() {
            @Override
            public void onBroadcast(String msg) {
                runOnUiThread(() -> handleServerMessage(msg));
            }

            @Override
            public void onConnected() {
            }

            @Override
            public void onDisconnected() {
                runOnUiThread(() -> {
                    started = false;
                    currentTurnId = -1;
                    updateActionButtons();
                    showStatus("Disconnected from server.");
                    showToast("Connection closed.");
                });
            }

            @Override
            public void onAssignedId(int id) {
                myId = id;
            }
        });
    }

    private void setupButtons() {
        drawCardBtn.setOnClickListener(v -> onPlaySelectedCard());

        takeCardBtn.setOnClickListener(v -> {
            if (!isMyTurn()) {
                showToast("Wait for your turn.");
                return;
            }
            sendToServer("draw");
        });

        unoCallBtn.setOnClickListener(v -> {
            if (receivedCardList.size() == 1) {
                sendToServer("uno");
            } else {
                showToast("UNO can be called only with one card.");
            }
        });

        for (int i = 0; i < penaltyBtns.length; i++) {
            final int slot = i;
            penaltyBtns[i].setOnClickListener(v -> onPenaltyClicked(slot));
        }

        updateActionButtons();
    }

    private void handleServerMessage(String msg) {
        if (msg == null) {
            return;
        }

        if (msg.startsWith("snapshot|")) {
            parseSnapshot(msg.substring(9));
            return;
        }

        if (msg.startsWith("error|")) {
            String error = msg.substring(6);
            showToast(error);
            showStatus(error);
            return;
        }

        if (msg.startsWith("end|")) {
            started = false;
            updateActionButtons();
            String end = msg.substring(4);
            showStatus(end);
            showToast(end);
        }
    }

    private void parseSnapshot(String json) {
        try {
            JSONObject root = new JSONObject(json);

            started = root.optBoolean("started", false);
            myId = root.optInt("youId", myId);
            currentTurnId = root.optInt("currentTurnId", -1);
            topCard = normalizeCard(root.optString("topCard", ""));
            currentColor = root.optString("currentColor", "");

            String status = root.optString("status", "");
            if (!status.isEmpty()) {
                showStatus(status);
            }

            receivedCardList.clear();
            JSONArray hand = root.optJSONArray("myHand");
            if (hand != null) {
                for (int i = 0; i < hand.length(); i++) {
                    String card = normalizeCard(hand.optString(i, ""));
                    if (!card.isEmpty()) {
                        receivedCardList.add(card);
                    }
                }
            }

            for (int i = 0; i < 3; i++) {
                opponentPlayerIds[i] = -1;
                opponentUno[i] = false;
                opponentCardCounts[i] = 0;
            }

            int opponentSlot = 0;
            JSONArray players = root.optJSONArray("players");
            if (players != null) {
                for (int i = 0; i < players.length(); i++) {
                    JSONObject p = players.optJSONObject(i);
                    if (p == null) {
                        continue;
                    }

                    int id = p.optInt("id", -1);
                    if (id == myId) {
                        continue;
                    }

                    if (opponentSlot < 3) {
                        opponentPlayerIds[opponentSlot] = id;
                        opponentUno[opponentSlot] = p.optBoolean("uno", false);
                        opponentCardCounts[opponentSlot] = p.optInt("cards", 0);

                        String name = p.optString("name", "Player " + id);
                        String cardsText = "has " + opponentCardCounts[opponentSlot] + " cards";
                        if (opponentUno[opponentSlot]) {
                            cardsText += " (UNO)";
                        }

                        playerNameTxt[opponentSlot].setText(name);
                        playerCardsTxt[opponentSlot].setText(cardsText);
                        playerRows[opponentSlot].setVisibility(View.VISIBLE);
                        opponentSlot++;
                    }
                }
            }

            for (int i = opponentSlot; i < 3; i++) {
                playerRows[i].setVisibility(View.GONE);
            }

            topCardTxt.setText(buildTopCardStatusText());
            renderTopCardPreview();
            arrangeCards(receivedCardList);
            updateActionButtons();
        } catch (Exception e) {
            showToast("Failed to parse game state.");
        }
    }

    private void onPenaltyClicked(int slot) {
        int pid = opponentPlayerIds[slot];
        if (pid < 0) {
            return;
        }

        if (opponentCardCounts[slot] == 1 && !opponentUno[slot]) {
            sendToServer("penalty|" + pid);
        } else {
            showToast("Penalty not valid now.");
        }
    }

    private void onPlaySelectedCard() {
        if (!isMyTurn()) {
            showToast("Wait for your turn.");
            return;
        }

        if (markedCardId < 0 || markedCardId >= receivedCardList.size()) {
            showToast("Select a card first.");
            return;
        }

        String card = normalizeCard(receivedCardList.get(markedCardId));
        if (card.startsWith("super_")) {
            showColorPicker(card);
        } else {
            sendToServer("play|" + card + "|");
        }
    }

    private void showColorPicker(String card) {
        String[] colors = new String[]{"red", "blue", "green", "yellow"};

        new AlertDialog.Builder(this)
                .setTitle("Choose color")
                .setItems(colors, (dialog, which) -> sendToServer("play|" + card + "|" + colors[which]))
                .show();
    }

    private boolean isMyTurn() {
        return started && myId > 0 && currentTurnId == myId;
    }

    private void updateActionButtons() {
        boolean active = isMyTurn();
        drawCardBtn.setAlpha(active ? 1f : 0.6f);
        takeCardBtn.setAlpha(active ? 1f : 0.6f);
    }

    private void arrangeCards(ArrayList<String> cardList) {
        cardContainer.removeAllViews();

        int totalCards = cardList.size();
        if (totalCards == 0) {
            markedCardId = -1;
            lastMarkedCard = null;
            return;
        }

        int cardWidth = dpToPx(70);
        int screenWidth = getResources().getDisplayMetrics().widthPixels - dpToPx(40);
        int totalWidth = cardWidth * totalCards;
        int overlap = 0;

        if (totalCards > 1 && totalWidth > screenWidth) {
            overlap = (totalWidth - screenWidth) / (totalCards - 1);
            overlap = Math.min(overlap, cardWidth);
        }

        markedCardId = -1;
        lastMarkedCard = null;

        for (int i = 0; i < totalCards; i++) {
            String data = cardList.get(i);
            View card = createCardView(data, cardContainer, false);

            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
            );

            if (i != 0) {
                params.setMargins(-overlap, 0, 0, 0);
            }

            card.setLayoutParams(params);
            card.setElevation(i);

            int cardIndex = i;
            card.setOnClickListener(v -> {
                if (markedCardId == cardIndex) {
                    if (lastMarkedCard != null) {
                        lastMarkedCard.animate().translationY(0).setDuration(120);
                    }
                    markedCardId = -1;
                    lastMarkedCard = null;
                    return;
                }

                markedCardId = cardIndex;
                if (lastMarkedCard != null) {
                    lastMarkedCard.animate().translationY(0).setDuration(120);
                }

                lastMarkedCard = card;
                card.animate().translationY(-dpToPx(20)).setDuration(120);
            });

            cardContainer.addView(card);
        }
    }

    private void renderTopCardPreview() {
        topCardPreview.removeAllViews();
        if (topCard == null || topCard.isEmpty()) {
            return;
        }

        View preview = createCardView(topCard, topCardPreview, true);
        topCardPreview.addView(preview);
    }

    private View createCardView(String data, ViewGroup parent, boolean large) {
        return cardViewFactory.createCardView(normalizeCard(data), parent, large);
    }

    private String normalizeCard(String card) {
        if (card == null) {
            return "";
        }

        String[] parts = card.trim().split("_");
        if (parts.length < 3) {
            return card.trim();
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

    private String buildTopCardStatusText() {
        String label = "Top: " + humanCard(topCard);
        if (topCard != null && topCard.startsWith("super_")) {
            return label + " | Active color: " + currentColor;
        }
        return label + " | Color: " + currentColor;
    }

    private void sendToServer(String msg) {
        if (client != null && client.isConnected()) {
            client.send(msg);
        } else {
            showToast("Not connected.");
        }
    }

    private String humanCard(String card) {
        if (card == null || card.isEmpty()) {
            return "none";
        }

        if ("super_black_plus".equals(card)) {
            return "super plus";
        }

        if ("super_black_color".equals(card)) {
            return "super color";
        }

        return card.replace("_", " ");
    }

    private void showStatus(String text) {
        if (started && isMyTurn()) {
            statusTxt.setText("Your turn. " + text);
        } else if (started && currentTurnId > 0) {
            statusTxt.setText("Player " + currentTurnId + " turn. " + text);
        } else {
            statusTxt.setText(text);
        }
    }

    private void showToast(String msg) {
        Toast.makeText(getApplicationContext(), msg, Toast.LENGTH_SHORT).show();
    }

    private int dpToPx(int dp) {
        return (int) (dp * getResources().getDisplayMetrics().density);
    }

    private void goBackToLobby() {
        startActivity(new Intent(this, LobbyActivity.class));
        finish();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        if (isFinishing()) {
            if (client != null) {
                client.disconnect();
            }
            UnoSession.stopHostService();
        }
    }
}
