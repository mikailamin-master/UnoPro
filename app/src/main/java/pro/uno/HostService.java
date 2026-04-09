package pro.uno;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import pro.uno.cards.Card;
import pro.uno.cards.CardActionContext;
import pro.uno.cards.CardRegistry;

public class HostService {

    private ServerSocket server;
    private DatagramSocket discoverySocket;
    private final ArrayList<ClientHandler> clients = new ArrayList<>();
    private final LinkedHashMap<Integer, PlayerState> players = new LinkedHashMap<>();
    private final ArrayList<String> deck = new ArrayList<>();
    private final ArrayList<String> discard = new ArrayList<>();
    private final ScheduledExecutorService aiExecutor = Executors.newSingleThreadScheduledExecutor();

    private int nextId = 1;
    private int desiredPlayers = UnoConfig.MIN_PLAYERS;
    private boolean running = false;
    private boolean started = false;
    private String hostDisplayName = "UNO Host";

    private int turnIndex = 0;
    private int direction = 1;
    private int winnerId = -1;
    private String currentColor = "red";

    private final Random random = new Random();

    public interface HostListener {
        void onClientConnected(int clientId);
        void onClientMsg(int clientId, String msg);
        void onClientDisconnected(int clientId);
    }

    private HostListener listener;

    private static class PlayerState {
        int id;
        String name;
        boolean ready;
        boolean unoCalled;
        boolean isAI;
        final ArrayList<String> hand = new ArrayList<>();

        PlayerState(int id) {
            this(id, false);
        }

        PlayerState(int id, boolean isAI) {
            this.id = id;
            this.name = isAI ? "AI " + id : "Player " + id;
            this.ready = false;
            this.unoCalled = false;
            this.isAI = isAI;
        }
    }

    public synchronized void setHostListener(HostListener listener) {
        this.listener = listener;
    }

    public synchronized void start_server(int port, int targetPlayers) {
        start_server(port, targetPlayers, "UNO Host");
    }

    public synchronized void start_server(int port, int targetPlayers, String hostName) {
        if (running) {
            return;
        }

        desiredPlayers = Math.max(UnoConfig.MIN_PLAYERS, Math.min(UnoConfig.MAX_PLAYERS, targetPlayers));
        hostDisplayName = sanitizeHostName(hostName);
        running = true;
        startDiscoveryResponder(port);

        Thread acceptThread = new Thread(() -> {
            try {
                server = new ServerSocket(port);
                while (running) {
                    Socket socket = server.accept();
                    handleNewClient(socket);
                }
            } catch (IOException ignored) {
            } finally {
                running = false;
                closeServerSocket();
            }
        }, "uno-host-accept");
        acceptThread.start();
    }

    public synchronized void stop_server() {
        running = false;
        started = false;
        for (ClientHandler c : new ArrayList<>(clients)) {
            c.close();
        }
        clients.clear();
        players.clear();
        deck.clear();
        discard.clear();
        closeServerSocket();
        closeDiscoverySocket();
    }

    public synchronized void addAIPlayer(String name) {
        if (started || players.size() >= desiredPlayers || players.size() >= UnoConfig.MAX_PLAYERS) {
            return;
        }

        int id = nextId++;
        PlayerState ai = new PlayerState(id, true);
        if (name != null && !name.trim().isEmpty()) {
            ai.name = name;
        }
        ai.ready = true;
        players.put(id, ai);

        sendLobbySnapshot(ai.name + " joined the lobby.");
    }
    private synchronized void handleNewClient(Socket socket) {
        if (started || players.size() >= desiredPlayers || players.size() >= UnoConfig.MAX_PLAYERS) {
            try {
                socket.getOutputStream().write("error|Lobby is full or game already started.\n".getBytes(StandardCharsets.UTF_8));
                socket.close();
            } catch (IOException ignored) {
            }
            return;
        }

        int id = nextId++;
        ClientHandler client = new ClientHandler(socket, this, id);
        clients.add(client);
        players.put(id, new PlayerState(id));
        client.start();

        if (listener != null) {
            listener.onClientConnected(id);
        }

        sendLobbySnapshot("Player connected. Waiting for names...");
    }

    private synchronized void closeServerSocket() {
        if (server != null && !server.isClosed()) {
            try {
                server.close();
            } catch (IOException ignored) {
            }
        }
    }

    private synchronized void startDiscoveryResponder(int gamePort) {
        Thread discoveryThread = new Thread(() -> {
            try {
                discoverySocket = new DatagramSocket(UnoConfig.DISCOVERY_PORT);
                byte[] buffer = new byte[512];

                while (running) {
                    DatagramPacket request = new DatagramPacket(buffer, buffer.length);
                    discoverySocket.receive(request);

                    String body = new String(request.getData(), 0, request.getLength(), StandardCharsets.UTF_8);
                    if (!UnoConfig.DISCOVERY_REQUEST.equals(body)) {
                        continue;
                    }

                    String response = buildDiscoveryResponse(gamePort);
                    byte[] out = response.getBytes(StandardCharsets.UTF_8);
                    DatagramPacket reply = new DatagramPacket(out, out.length, request.getAddress(), request.getPort());
                    discoverySocket.send(reply);
                }
            } catch (Exception ignored) {
            } finally {
                closeDiscoverySocket();
            }
        }, "uno-host-discovery");
        discoveryThread.start();
    }

    private synchronized void closeDiscoverySocket() {
        if (discoverySocket != null && !discoverySocket.isClosed()) {
            discoverySocket.close();
        }
    }

    private synchronized String buildDiscoveryResponse(int gamePort) {
        return UnoConfig.DISCOVERY_RESPONSE_PREFIX
                + hostDisplayName + "|"
                + gamePort + "|"
                + players.size() + "|"
                + desiredPlayers + "|"
                + (started ? 1 : 0);
    }

    public synchronized void remove_client(ClientHandler client) {
        clients.remove(client);
        players.remove(client.player_id);
        refreshHostDisplayName();

        if (listener != null) {
            listener.onClientDisconnected(client.player_id);
        }

        if (started) {
            started = false;
            broadcast("end|Game ended: a player disconnected.");
        } else {
            sendLobbySnapshot("Player disconnected.");
        }
    }

    private synchronized void broadcast(String msg) {
        for (ClientHandler c : clients) {
            c.send(msg);
        }
    }

    private synchronized void send_to(int playerId, String msg) {
        for (ClientHandler c : clients) {
            if (c.player_id == playerId) {
                c.send(msg);
                return;
            }
        }
    }

    public synchronized void handle_message(String msg, int senderId) {
        if (listener != null) {
            listener.onClientMsg(senderId, msg);
        }

        String[] parts = msg.split("\\|", 4);
        if (parts.length == 0) {
            return;
        }

        String type = parts[0];
        switch (type) {
            case "join":
                handleJoin(parts, senderId);
                break;
            case "play":
                handlePlay(parts, senderId);
                break;
            case "draw":
                handleDraw(senderId);
                break;
            case "uno":
                handleUno(senderId);
                break;
            case "penalty":
                handlePenalty(parts, senderId);
                break;
            case "ready":
                handleReady(parts, senderId);
                break;
            case "start":
                handleStart(senderId);
                break;
            default:
                send_to(senderId, "error|Unknown command.");
                break;
        }
    }

    private void handleJoin(String[] parts, int senderId) {
        PlayerState player = players.get(senderId);
        if (player == null) {
            return;
        }

        if (parts.length >= 2) {
            String proposedName = parts[1].trim();
            if (!proposedName.isEmpty()) {
                player.name = proposedName.length() > UnoConfig.MAX_PLAYER_NAME_LENGTH ? proposedName.substring(0, UnoConfig.MAX_PLAYER_NAME_LENGTH) : proposedName;
            }
        }

        if (parts.length >= 3) {
            try {
                int requested = Integer.parseInt(parts[2]);
                if (!started && requested >= UnoConfig.MIN_PLAYERS && requested <= UnoConfig.MAX_PLAYERS) {
                    desiredPlayers = requested;
                }
            } catch (NumberFormatException ignored) {
            }
        }

        sendLobbySnapshot(player.name + " joined the lobby.");
        refreshHostDisplayName();
    }

    private void startGame() {
        if (started || !canStartGame()) {
            return;
        }

        started = true;
        winnerId = -1;
        direction = 1;
        turnIndex = 0;

        deck.clear();
        discard.clear();

        buildDeck(deck);
        Collections.shuffle(deck, random);

        for (PlayerState p : players.values()) {
            p.hand.clear();
            p.unoCalled = false;
            for (int i = 0; i < UnoConfig.STARTING_HAND_SIZE; i++) {
                giveCardsToPlayer(p, 1);
            }
        }

        String first = drawFromDeck();
        while (CardRegistry.create(first).isWild() && !deck.isEmpty()) {
            deck.add(first);
            Collections.shuffle(deck, random);
            first = drawFromDeck();
        }

        discard.add(first);
        currentColor = CardRegistry.create(first).getColor();

        sendSnapshotsToAll("Game started.");
        checkAITurn();
    }

    private synchronized void checkAITurn() {
        if (!started) return;
        int currentId = getCurrentTurnPlayerId();
        PlayerState current = players.get(currentId);
        if (current != null && current.isAI) {
            scheduleAITurn(currentId);
        }
    }

    private void scheduleAITurn(final int aiId) {
        aiExecutor.schedule(() -> {
            synchronized (HostService.this) {
                performAITurn(aiId);
            }
        }, UnoConfig.AI_TURN_DELAY_BASE_MS + random.nextInt(UnoConfig.AI_TURN_DELAY_RANDOM_MS), TimeUnit.MILLISECONDS);
    }

    private void performAITurn(int aiId) {
        PlayerState ai = players.get(aiId);
        if (ai == null || !started || getCurrentTurnPlayerId() != aiId) return;

        String topCard = getTopCard();
        List<String> playable = new ArrayList<>();
        for (String card : ai.hand) {
            if (CardRegistry.isPlayable(card, topCard, currentColor)) {
                playable.add(card);
            }
        }

        if (playable.isEmpty()) {
            handle_message("draw", aiId);
            return;
        }

        // Pro AI Logic: Pick best card
        String bestCard = pickBestAICard(playable, ai.hand);
        String chosenColor = "";
        if (CardRegistry.create(bestCard).requiresColorChoice()) {
            chosenColor = pickBestAIColor(ai.hand);
        }

        // Call UNO if needed
        if (ai.hand.size() == 2 && !ai.unoCalled) {
            handle_message("uno", aiId);
        }

        handle_message("play|" + bestCard + "|" + chosenColor, aiId);
    }

    String pickBestAICard(List<String> playable, List<String> hand) {
        // Simple strategy:
        // 1. Prefer matching number/color (NormalCard)
        // 2. Then power cards (Skip, Reverse, PlusTwo)
        // 3. Then wild cards (ColorChange, PlusFour)

        String best = playable.get(0);
        int bestScore = -1;

        for (String cId : playable) {
            Card c = CardRegistry.create(cId);
            int score = 0;
            if ("card".equals(c.getFamily())) score = UnoConfig.SCORE_NORMAL_CARD;
            else if ("power".equals(c.getFamily())) score = UnoConfig.SCORE_POWER_CARD;
            else if ("super".equals(c.getFamily())) score = UnoConfig.SCORE_SUPER_CARD;

            if (score > bestScore) {
                bestScore = score;
                best = cId;
            }
        }
        return best;
    }

    String pickBestAIColor(List<String> hand) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        counts.put("red", 0);
        counts.put("blue", 0);
        counts.put("green", 0);
        counts.put("yellow", 0);

        for (String cId : hand) {
            Card c = CardRegistry.create(cId);
            String color = c.getColor();
            if (counts.containsKey(color)) {
                counts.put(color, counts.get(color) + 1);
            }
        }

        String bestColor = "red";
        int max = -1;
        for (Map.Entry<String, Integer> entry : counts.entrySet()) {
            if (entry.getValue() > max) {
                max = entry.getValue();
                bestColor = entry.getKey();
            }
        }
        return bestColor;
    }

    private void handleReady(String[] parts, int senderId) {
        if (started) {
            send_to(senderId, "error|Game already started.");
            return;
        }

        PlayerState player = players.get(senderId);
        if (player == null) {
            return;
        }

        boolean ready = true;
        if (parts.length >= 2) {
            ready = "1".equals(parts[1]) || "true".equalsIgnoreCase(parts[1]) || "ready".equalsIgnoreCase(parts[1]);
        }

        player.ready = ready;
        sendLobbySnapshot(player.name + (ready ? " is ready." : " is not ready."));
    }

    private void handleStart(int senderId) {
        if (started) {
            send_to(senderId, "error|Game already started.");
            return;
        }

        if (!isHostPlayerId(senderId)) {
            send_to(senderId, "error|Only the host can start the game.");
            return;
        }

        if (!canStartGame() && players.size() > 1) {
            send_to(senderId, "error|All players must be connected and ready.");
            sendLobbySnapshot("Waiting for all players to get ready.");
            return;
        }

        startGame();
    }

    private void handlePlay(String[] parts, int senderId) {
        if (!started) {
            send_to(senderId, "error|Game has not started.");
            return;
        }

        if (parts.length < 2) {
            send_to(senderId, "error|Invalid play.");
            return;
        }

        if (getCurrentTurnPlayerId() != senderId) {
            send_to(senderId, "error|Not your turn.");
            return;
        }

        PlayerState player = players.get(senderId);
        if (player == null) {
            return;
        }

        String card = normalizeCard(parts[1]);
        Card playedCard = CardRegistry.create(card);
        String chosenColor = parts.length >= 3 ? parts[2] : "";

        if (!player.hand.contains(card)) {
            send_to(senderId, "error|Card not in your hand.");
            return;
        }

        if (!CardRegistry.isValid(playedCard)) {
            send_to(senderId, "error|Invalid card.");
            return;
        }

        String topCard = getTopCard();
        if (!CardRegistry.isPlayable(card, topCard, currentColor)) {
            send_to(senderId, "error|That card cannot be played now.");
            return;
        }

        player.hand.remove(card);
        discard.add(card);

        CardActionContext actionContext = new CardActionContext(playedCard, senderId, players.size(), chosenColor);
        playedCard.onCardPlayed(actionContext);
        if (playedCard.requiresColorChoice() && !actionContext.hasColorOverride()) {
            send_to(senderId, "error|Choose a valid color for wild card.");
            discard.remove(discard.size() - 1);
            player.hand.add(card);
            return;
        }
        currentColor = actionContext.resolveTopCardColor(playedCard.getColor());

        if (player.hand.size() != 1) {
            player.unoCalled = false;
        }

        if (player.hand.isEmpty()) {
            winnerId = player.id;
            started = false;
            sendSnapshotsToAll(player.name + " wins the match.");
            broadcast("end|Winner: " + player.name);
            return;
        }

        applyCardEffect(playedCard, actionContext);
    }

    private void applyCardEffect(Card card, CardActionContext actionContext) {
        int step = 1;

        if (actionContext.shouldReverseDirection()) {
            direction *= -1;
        }

        if (actionContext.getNextPlayerDrawCount() > 0) {
            PlayerState next = players.get(getRelativePlayerId(1));
            if (next != null) {
                giveCardsToPlayer(next, actionContext.getNextPlayerDrawCount());
                next.unoCalled = false;
            }
        }

        if (actionContext.shouldSkipNextPlayer()) {
            step = 2;
        }

        advanceTurn(step);
        sendSnapshotsToAll("Card played: " + card.id());
    }

    private void handleDraw(int senderId) {
        if (!started) {
            send_to(senderId, "error|Game has not started.");
            return;
        }

        if (getCurrentTurnPlayerId() != senderId) {
            send_to(senderId, "error|Not your turn.");
            return;
        }

        PlayerState player = players.get(senderId);
        if (player == null) {
            return;
        }

        giveCardsToPlayer(player, 1);
        player.unoCalled = false;
        advanceTurn(1);
        sendSnapshotsToAll(player.name + " drew a card.");
    }

    private void handleUno(int senderId) {
        PlayerState player = players.get(senderId);
        if (player == null) {
            return;
        }

        if (player.hand.size() == 1) {
            player.unoCalled = true;
            sendSnapshotsToAll(player.name + " called UNO.");
        } else {
            send_to(senderId, "error|UNO is only valid when you have one card.");
        }
    }

    private void handlePenalty(String[] parts, int senderId) {
        if (parts.length < 2) {
            send_to(senderId, "error|Invalid penalty.");
            return;
        }

        int targetId;
        try {
            targetId = Integer.parseInt(parts[1]);
        } catch (NumberFormatException e) {
            send_to(senderId, "error|Invalid penalty target.");
            return;
        }

        PlayerState target = players.get(targetId);
        if (target == null) {
            send_to(senderId, "error|Player not found.");
            return;
        }

        if (target.hand.size() == 1 && !target.unoCalled) {
            giveCardsToPlayer(target, UnoConfig.PENALTY_CARD_COUNT);
            target.unoCalled = false;
            sendSnapshotsToAll("Penalty! " + target.name + " drew " + UnoConfig.PENALTY_CARD_COUNT + " cards.");
        } else {
            send_to(senderId, "error|Penalty is not valid now.");
        }
    }

    private void sendLobbySnapshot(String status) {
        sendSnapshotsToAll(status);
    }

    private void sendSnapshotsToAll(String status) {
        for (ClientHandler client : clients) {
            PlayerState me = players.get(client.player_id);
            if (me == null) {
                continue;
            }
            send_to(client.player_id, "snapshot|" + buildSnapshot(me, status));
        }
    }

    private String buildSnapshot(PlayerState me, String status) {
        try {
            JSONObject root = new JSONObject();
            root.put("started", started);
            root.put("youId", me.id);
            root.put("desiredPlayers", desiredPlayers);
            root.put("currentTurnId", started ? getCurrentTurnPlayerId() : -1);
            root.put("direction", direction);
            root.put("topCard", getTopCard());
            root.put("currentColor", currentColor);
            root.put("winnerId", winnerId);
            root.put("status", status == null ? "" : status);

            JSONArray allPlayers = new JSONArray();
            for (PlayerState p : players.values()) {
                JSONObject entry = new JSONObject();
                entry.put("id", p.id);
                entry.put("name", p.name);
                entry.put("ready", p.ready);
                entry.put("cards", p.hand.size());
                entry.put("uno", p.unoCalled);
                allPlayers.put(entry);
            }
            root.put("players", allPlayers);
            root.put("hostId", getHostPlayerId());
            root.put("allConnected", players.size() == desiredPlayers);
            root.put("allReady", areAllPlayersReady());
            root.put("canStart", canStartGame());

            JSONArray hand = new JSONArray();
            for (String c : me.hand) {
                String normalized = normalizeCard(c);
                if (!normalized.isEmpty()) {
                    hand.put(normalized);
                }
            }
            root.put("myHand", hand);

            return root.toString();
        } catch (Exception e) {
            return "{}";
        }
    }

    private String drawFromDeck() {
        if (deck.isEmpty()) {
            refillDeck();
        }
        if (deck.isEmpty()) {
            return "card_red_0";
        }
        return deck.remove(deck.size() - 1);
    }

    private void refillDeck() {
        if (discard.size() <= 1) {
            return;
        }

        String top = discard.remove(discard.size() - 1);
        deck.addAll(discard);
        discard.clear();
        discard.add(top);
        Collections.shuffle(deck, random);
    }

    private void buildDeck(List<String> out) {
        CardRegistry.buildStandardDeck(out);
    }

    private String getTopCard() {
        if (discard.isEmpty()) {
            return "";
        }
        String top = normalizeCard(discard.get(discard.size() - 1));
        discard.set(discard.size() - 1, top);
        return top;
    }

    private int getCurrentTurnPlayerId() {
        ArrayList<Integer> ids = new ArrayList<>(players.keySet());
        if (ids.isEmpty()) {
            return -1;
        }
        turnIndex = normalizeIndex(turnIndex, ids.size());
        return ids.get(turnIndex);
    }

    private int getRelativePlayerId(int offset) {
        ArrayList<Integer> ids = new ArrayList<>(players.keySet());
        if (ids.isEmpty()) {
            return -1;
        }

        int index = normalizeIndex(turnIndex + (offset * direction), ids.size());
        return ids.get(index);
    }

    private void advanceTurn(int step) {
        ArrayList<Integer> ids = new ArrayList<>(players.keySet());
        if (ids.isEmpty()) {
            turnIndex = 0;
            return;
        }
        turnIndex = normalizeIndex(turnIndex + (step * direction), ids.size());
        checkAITurn();
    }

    private int normalizeIndex(int value, int size) {
        int result = value % size;
        if (result < 0) {
            result += size;
        }
        return result;
    }

    private boolean isPlayable(String card, String topCard, String activeColor) {
        return CardRegistry.isPlayable(card, topCard, activeColor);
    }

    private String normalizeCard(String card) {
        return CardFormatter.normalize(card);
    }

    private boolean isColorValid(String color) {
        return isStandardColor(color);
    }

    private boolean isStandardColor(String color) {
        return CardCatalog.isStandardColor(color);
    }

    private String sanitizeHostName(String hostName) {
        if (hostName == null) {
            return "UNO Host";
        }
        String cleaned = hostName.replace("|", "").trim();
        if (cleaned.isEmpty()) {
            return "UNO Host";
        }
        if (cleaned.length() > UnoConfig.MAX_PLAYER_NAME_LENGTH) {
            return cleaned.substring(0, UnoConfig.MAX_PLAYER_NAME_LENGTH);
        }
        return cleaned;
    }

    private void refreshHostDisplayName() {
        ArrayList<PlayerState> ps = new ArrayList<>(players.values());
        if (!ps.isEmpty()) {
            hostDisplayName = sanitizeHostName(ps.get(0).name);
        }
    }

    private int getHostPlayerId() {
        if (players.isEmpty()) return -1;
        
        int minId = Integer.MAX_VALUE;
        for (Integer id : players.keySet()) {
            if (id < minId) {
                minId = id;
            }
        }
        return minId;
    }

    private boolean isHostPlayerId(int playerId) {
        return playerId > 0 && playerId == getHostPlayerId();
    }

    private boolean areAllPlayersReady() {
        if (players.size() != desiredPlayers) {
            return false;
        }

        for (PlayerState player : players.values()) {
            if (!player.ready) {
                return false;
            }
        }
        return !players.isEmpty();
    }

    private boolean canStartGame() {
        return players.size() == desiredPlayers && areAllPlayersReady();
    }

    private void giveCardsToPlayer(PlayerState player, int totalCards) {
        if (player == null || totalCards <= 0) {
            return;
        }

        for (int i = 0; i < totalCards; i++) {
            String cardId = drawFromDeck();
            player.hand.add(cardId);

            Card drawnCard = CardRegistry.create(cardId);
            CardActionContext context = new CardActionContext(drawnCard, player.id, players.size(), "");
            drawnCard.onCardDrawedToPlayer(context);
            drawnCard.onPlayerTakeTheCard(context);
        }
    }
}
