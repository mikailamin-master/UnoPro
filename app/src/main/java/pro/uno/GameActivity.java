package pro.uno;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import pro.uno.ui.card.CardViewFactory;

public class GameActivity extends BaseMaterialActivity {

    public static final String EXTRA_SNAPSHOT = "snapshot";

    private FrameLayout topCardPreview;
    private LinearLayout cardContainer;
    private MaterialButton drawCardBtn;
    private MaterialButton takeCardBtn;
    private MaterialButton unoCallBtn;

    private LinearLayout[] playerRows;
    private TextView[] playerNameTxt;
    private TextView[] playerCardsTxt;
    private MaterialButton[] penaltyBtns;

    private TextView statusTxt;
    private TextView topCardTxt;

    private View lastMarkedCard = null;
    private int markedCardId = -1;

    private final int[] opponentPlayerIds = new int[]{-1, -1, -1};
    private final boolean[] opponentUno = new boolean[]{false, false, false};
    private final int[] opponentCardCounts = new int[]{0, 0, 0};

    private final ArrayList<String> receivedCardList = new ArrayList<>();
    private final ArrayList<String> discardHistory = new ArrayList<>();
    private static final int MAX_DISCARD_HISTORY = 5;

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
            statusTxt.setText(R.string.waiting_for_game_state);
            topCardTxt.setText(R.string.top_card_fallback);
        }
    }

    @Override
    protected int getRootViewId() {
        return R.id.game_root;
    }

    private void bindViews() {
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

        penaltyBtns = new MaterialButton[]{
                findViewById(R.id.penalty_p1),
                findViewById(R.id.penalty_p2),
                findViewById(R.id.penalty_p3)
        };

        statusTxt = findViewById(R.id.status_txt);
        topCardTxt = findViewById(R.id.top_card_status_txt);
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
                    showToast(getString(R.string.toast_connection_closed));
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
                showToast(getString(R.string.toast_wait_turn));
                return;
            }
            sendToServer("draw");
        });

        unoCallBtn.setOnClickListener(v -> {
            if (receivedCardList.size() == 1) {
                sendToServer("uno");
            } else {
                showToast(getString(R.string.toast_uno_only_one_card));
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
            GameSnapshot snapshot = GameSnapshot.fromJson(json, myId);
            started = snapshot.started;
            myId = snapshot.myId;
            currentTurnId = snapshot.currentTurnId;
            
            String newTopCard = CardFormatter.normalize(snapshot.topCard);
            if (!topCard.equals(newTopCard)) {
                if (!topCard.isEmpty()) {
                    discardHistory.add(0, topCard);
                    if (discardHistory.size() > MAX_DISCARD_HISTORY) {
                        discardHistory.remove(discardHistory.size() - 1);
                    }
                }
                topCard = newTopCard;
            }
            
            currentColor = snapshot.currentColor;

            showStatus(snapshot.status);
            receivedCardList.clear();
            receivedCardList.addAll(snapshot.myHand);

            for (int i = 0; i < 3; i++) {
                opponentPlayerIds[i] = -1;
                opponentUno[i] = false;
                opponentCardCounts[i] = 0;
            }

            int opponentSlot = 0;
            for (GameSnapshot.Opponent opponent : snapshot.opponents) {
                if (opponentSlot < 3) {
                    opponentPlayerIds[opponentSlot] = opponent.id;
                    opponentUno[opponentSlot] = opponent.unoCalled;
                    opponentCardCounts[opponentSlot] = opponent.cardCount;

                    playerNameTxt[opponentSlot].setText(opponent.name);
                    playerCardsTxt[opponentSlot].setText(
                            GameUiFormatter.buildOpponentCardsText(this, opponent.cardCount, opponent.unoCalled)
                    );
                    playerRows[opponentSlot].setVisibility(View.VISIBLE);
                    opponentSlot++;
                }
            }

            for (int i = opponentSlot; i < 3; i++) {
                playerRows[i].setVisibility(View.GONE);
            }

            topCardTxt.setText(CardFormatter.buildTopCardStatusText(this, topCard, currentColor));
            renderTopCardPreview();
            arrangeCards(receivedCardList);
            updateActionButtons();
        } catch (Exception e) {
            showToast(getString(R.string.toast_failed_parse_game));
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
            showToast(getString(R.string.toast_penalty_invalid));
        }
    }

    private void onPlaySelectedCard() {
        if (!isMyTurn()) {
            showToast(getString(R.string.toast_wait_turn));
            return;
        }

        if (markedCardId < 0 || markedCardId >= receivedCardList.size()) {
            showToast(getString(R.string.toast_select_card_first));
            return;
        }

        String card = CardFormatter.normalize(receivedCardList.get(markedCardId));
        if (card.startsWith("super_")) {
            showColorPicker(card);
        } else {
            sendToServer("play|" + card + "|");
        }
    }

    private void showColorPicker(String card) {
        String[] colors = getResources().getStringArray(R.array.wild_color_options);

        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.dialog_choose_color)
                .setItems(colors, (dialog, which) -> sendToServer("play|" + card + "|" + colors[which]))
                .show();
    }

    private boolean isMyTurn() {
        return started && myId > 0 && currentTurnId == myId;
    }

    private void updateActionButtons() {
        boolean active = isMyTurn();
        drawCardBtn.setEnabled(active);
        takeCardBtn.setEnabled(active);
        drawCardBtn.setAlpha(active ? 1f : 0.7f);
        takeCardBtn.setAlpha(active ? 1f : 0.7f);
    }

    private void arrangeCards(ArrayList<String> cardList) {
        cardContainer.removeAllViews();
        cardContainer.setClipChildren(false);
        cardContainer.setClipToPadding(false);

        int totalCards = cardList.size();
        if (totalCards == 0) {
            markedCardId = -1;
            lastMarkedCard = null;
            return;
        }

        int cardWidth = dpToPx(70);
        int containerPadding = dpToPx(56); // space_md (12) * 2 + parent padding
        int screenWidth = getResources().getDisplayMetrics().widthPixels - containerPadding;
        int totalWidth = cardWidth * totalCards;
        int overlap = 0;

        if (totalWidth > screenWidth) {
	        overlap = (totalWidth - screenWidth) / (totalCards - 1);
        }
        
        overlap = Math.min(overlap, (int)(cardWidth * 0.8)); // Limit overlap to 80%

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
                params.leftMargin = -overlap;
            }

            card.setLayoutParams(params);
            card.setElevation(i);

            int cardIndex = i;
            card.setOnClickListener(v -> {
                if (markedCardId == cardIndex) {
                    if (lastMarkedCard != null) {
                        resetCardSelection(lastMarkedCard, cardIndex);
                    }
                    markedCardId = -1;
                    lastMarkedCard = null;
                    return;
                }

                if (lastMarkedCard != null) {
                    resetCardSelection(lastMarkedCard, markedCardId);
                }

                markedCardId = cardIndex;
                lastMarkedCard = card;
                card.animate().translationY(-dpToPx(20)).setDuration(120).start();
            });

            cardContainer.addView(card);
        }

        cardContainer.requestLayout();
    }

    private void renderTopCardPreview() {
        topCardPreview.removeAllViews();
        if (topCard == null || topCard.isEmpty()) {
            return;
        }

        // Render previous cards
        for (int i = discardHistory.size() - 1; i >= 0; i--) {
            String historyCard = discardHistory.get(i);
            View view = createCardView(historyCard, topCardPreview, true);
            
            FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            );
            params.gravity = android.view.Gravity.CENTER;
            view.setLayoutParams(params);
            
            // Random rotation between -15 and 15 degrees
            float rotation = (float) (Math.random() * 30 - 15);
            view.setRotation(rotation);
            
            // Fading effect for previous cards
            float alpha = 0.2f + (0.5f * (float)(discardHistory.size() - i) / (float)MAX_DISCARD_HISTORY);
            view.setAlpha(Math.min(alpha, 0.7f));
            
            topCardPreview.addView(view);
        }

        // Render the actual top card
        View topView = createCardView(topCard, topCardPreview, true);
        FrameLayout.LayoutParams topParams = new FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        );
        topParams.gravity = android.view.Gravity.CENTER;
        topView.setLayoutParams(topParams);
        topView.setElevation(10); // Bring to front
        topCardPreview.addView(topView);
    }

    private View createCardView(String data, ViewGroup parent, boolean large) {
        return cardViewFactory.createCardView(CardFormatter.normalize(data), parent, large);
    }

    private void sendToServer(String msg) {
        if (client != null && client.isConnected()) {
            client.send(msg);
        } else {
            showToast(getString(R.string.toast_not_connected));
        }
    }

    private void showStatus(String text) {
        statusTxt.setText(CardFormatter.buildTurnStatusText(this, started, currentTurnId, myId, text));
        topCardTxt.setText(CardFormatter.buildTopCardStatusText(this, topCard, currentColor));
    }

    private void showToast(String msg) {
        Toast.makeText(getApplicationContext(), msg, Toast.LENGTH_SHORT).show();
    }

    private int dpToPx(int dp) {
        return (int) (dp * getResources().getDisplayMetrics().density);
    }

    private void resetCardSelection(View card, int cardIndex) {
        if (card == null) {
            return;
        }
        card.animate().translationY(0).setDuration(120).start();
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
