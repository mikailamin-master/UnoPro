package pro.uno;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.ArrayList;
import java.util.Enumeration;

public class LobbyActivity extends Activity {

    private EditText nameInput;
    private RadioGroup modeGroup;
    private RadioButton hostMode;
    private TextView lobbyTitleTxt;
    private TextView lobbyStatusTxt;
    private TextView connectionTxt;
    private TextView[] playerTxt;
    private Button hostBtn;
    private Button joinBtn;
    private Button readyBtn;
    private Button startBtn;

    private ClientService client;
    private LanHostScanner hostScanner;

    private String myName = "";
    private int myId = -1;
    private int desiredPlayers = 2;
    private int hostId = -1;
    private boolean allConnected = false;
    private boolean allReady = false;
    private boolean amReady = false;
    private boolean launchingGame = false;
    private String lastSnapshotJson = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_lobby);

        bindViews();
        setupButtons();
        updateLobbyUi(null);
    }

    private void bindViews() {
        nameInput = findViewById(R.id.name_input);
        modeGroup = findViewById(R.id.mode_group);
        hostMode = findViewById(R.id.host_mode);
        lobbyTitleTxt = findViewById(R.id.lobby_title_txt);
        lobbyStatusTxt = findViewById(R.id.lobby_status_txt);
        connectionTxt = findViewById(R.id.connection_txt);
        playerTxt = new TextView[]{
                findViewById(R.id.player_1_txt),
                findViewById(R.id.player_2_txt),
                findViewById(R.id.player_3_txt),
                findViewById(R.id.player_4_txt)
        };
        hostBtn = findViewById(R.id.host_btn);
        joinBtn = findViewById(R.id.join_btn);
        readyBtn = findViewById(R.id.ready_btn);
        startBtn = findViewById(R.id.start_btn);

        nameInput.setText("Player" + ((System.currentTimeMillis() / 1000) % 100));
    }

    private void setupButtons() {
        hostBtn.setOnClickListener(v -> {
            if (!prepareName()) {
                return;
            }
            showHostPlayerCountDialog();
        });

        joinBtn.setOnClickListener(v -> {
            if (!prepareName()) {
                return;
            }
            scanAndJoinHost();
        });

        readyBtn.setOnClickListener(v -> {
            if (client == null || !client.isConnected()) {
                showToast("Join the lobby first.");
                return;
            }
            client.send("ready|" + (!amReady ? 1 : 0));
        });

        startBtn.setOnClickListener(v -> {
            if (client == null || !client.isConnected()) {
                showToast("Join the lobby first.");
                return;
            }
            client.send("start");
        });

        modeGroup.setOnCheckedChangeListener((group, checkedId) -> updateModeButtons());
        updateModeButtons();
    }

    private boolean prepareName() {
        myName = sanitizeName(nameInput.getText().toString());
        if (myName.isEmpty()) {
            showToast("Enter your name.");
            return false;
        }
        return true;
    }

    private void updateModeButtons() {
        boolean hostSelected = hostMode.isChecked();
        hostBtn.setVisibility(hostSelected ? View.VISIBLE : View.GONE);
        joinBtn.setVisibility(hostSelected ? View.GONE : View.VISIBLE);
    }

    private void showHostPlayerCountDialog() {
        String[] items = new String[]{"2 Players", "3 Players", "4 Players"};

        new AlertDialog.Builder(this)
                .setTitle("How many players?")
                .setItems(items, (dialog, which) -> {
                    desiredPlayers = which + 2;
                    startHosting();
                })
                .setCancelable(false)
                .show();
    }

    private void showJoinIpDialog() {
        final EditText ipInput = new EditText(this);
        ipInput.setInputType(InputType.TYPE_CLASS_TEXT);
        ipInput.setHint("Host LAN IP, ex: 192.168.0.10");

        new AlertDialog.Builder(this)
                .setTitle("Join by IP")
                .setView(ipInput)
                .setCancelable(false)
                .setPositiveButton("Join", (d, which) -> {
                    String ip = ipInput.getText().toString().trim();
                    if (ip.isEmpty()) {
                        showToast("Please enter host IP.");
                        showJoinIpDialog();
                        return;
                    }
                    connectAsClient(ip, 0);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void scanAndJoinHost() {
        lobbyStatusTxt.setText("Scanning LAN hosts...");
        if (hostScanner == null) {
            hostScanner = new LanHostScanner();
        }

        final ArrayList<LanHostScanner.DiscoveredHost> discovered = new ArrayList<>();
        hostScanner.scan(6001, 4500, new LanHostScanner.ScanListener() {
            @Override
            public void onHostFound(LanHostScanner.DiscoveredHost host) {
                runOnUiThread(() -> discovered.add(host));
            }

            @Override
            public void onFinished() {
                runOnUiThread(() -> {
                    if (isFinishing()) {
                        return;
                    }

                    if (discovered.size() == 1) {
                        LanHostScanner.DiscoveredHost host = discovered.get(0);
                        showToast("Found host " + host.name + " (" + host.ip + ")");
                        connectAsClient(host.ip, 0);
                        return;
                    }

                    if (discovered.isEmpty()) {
                        showToast("No host found on LAN.");
                        showJoinIpDialog();
                        return;
                    }

                    showHostSelectionDialog(discovered);
                });
            }

            @Override
            public void onError(String message) {
                runOnUiThread(() -> {
                    showToast(message);
                    showJoinIpDialog();
                });
            }
        });
    }

    private void showHostSelectionDialog(ArrayList<LanHostScanner.DiscoveredHost> discovered) {
        String[] items = new String[discovered.size()];
        for (int i = 0; i < discovered.size(); i++) {
            LanHostScanner.DiscoveredHost host = discovered.get(i);
            String state = host.started ? "running" : "lobby";
            items[i] = host.name + "  (" + host.ip + ")  " + host.currentPlayers + "/" + host.targetPlayers + "  " + state;
        }

        new AlertDialog.Builder(this)
                .setTitle("Select Host")
                .setItems(items, (dialog, which) -> connectAsClient(discovered.get(which).ip, 0))
                .setNegativeButton("Manual IP", (dialog, which) -> showJoinIpDialog())
                .setPositiveButton("Rescan", (dialog, which) -> scanAndJoinHost())
                .show();
    }

    private void startHosting() {
        HostService hostService = new HostService();
        UnoSession.setHostService(hostService);
        hostService.start_server(6000, desiredPlayers, myName);

        connectionTxt.setText("Hosting on " + getLocalIpv4Address() + ":6000");
        lobbyStatusTxt.setText("Waiting for players to connect...");
        connectionTxt.postDelayed(() -> connectAsClient("127.0.0.1", desiredPlayers), 250);
    }

    private void connectAsClient(String ip, int requestedPlayers) {
        client = ClientService.getInstance();
        client.setClientListener(new ClientService.ClientListener() {
            @Override
            public void onBroadcast(String msg) {
                runOnUiThread(() -> handleServerMessage(msg));
            }

            @Override
            public void onConnected() {
                runOnUiThread(() -> {
                    connectionTxt.setText("Connected to " + ip + ":6000");
                    lobbyStatusTxt.setText("Connected. Waiting for lobby state...");
                    hostBtn.setEnabled(false);
                    joinBtn.setEnabled(false);
                    nameInput.setEnabled(false);
                    hostMode.setEnabled(false);
                    RadioButton joinRadio = findViewById(R.id.join_mode);
                    joinRadio.setEnabled(false);
                });
            }

            @Override
            public void onDisconnected() {
                runOnUiThread(() -> {
                    if (launchingGame) {
                        return;
                    }
                    myId = -1;
                    hostId = -1;
                    allConnected = false;
                    allReady = false;
                    amReady = false;
                    lastSnapshotJson = "";
                    lobbyStatusTxt.setText("Disconnected from server.");
                    connectionTxt.setText("Not connected");
                    nameInput.setEnabled(true);
                    hostMode.setEnabled(true);
                    RadioButton joinRadio = findViewById(R.id.join_mode);
                    joinRadio.setEnabled(true);
                    hostBtn.setEnabled(true);
                    joinBtn.setEnabled(true);
                    updateLobbyUi(null);
                });
            }

            @Override
            public void onAssignedId(int id) {
                myId = id;
                client.send("join|" + myName + "|" + requestedPlayers);
            }
        });

        client.connect(ip, 6000);
    }

    private void handleServerMessage(String msg) {
        if (msg == null) {
            return;
        }

        if (msg.startsWith("snapshot|")) {
            String json = msg.substring(9);
            lastSnapshotJson = json;
            parseSnapshot(json);
            return;
        }

        if (msg.startsWith("error|")) {
            String error = msg.substring(6);
            showToast(error);
            lobbyStatusTxt.setText(error);
        }
    }

    private void parseSnapshot(String json) {
        try {
            JSONObject root = new JSONObject(json);
            boolean started = root.optBoolean("started", false);
            myId = root.optInt("youId", myId);
            hostId = root.optInt("hostId", -1);
            desiredPlayers = root.optInt("desiredPlayers", desiredPlayers);
            allConnected = root.optBoolean("allConnected", false);
            allReady = root.optBoolean("allReady", false);

            String status = root.optString("status", "");
            if (!status.isEmpty()) {
                lobbyStatusTxt.setText(status);
            }

            JSONArray players = root.optJSONArray("players");
            amReady = false;
            updateLobbyUi(players);

            if (started && !launchingGame) {
                launchingGame = true;
                Intent intent = new Intent(this, GameActivity.class);
                intent.putExtra(GameActivity.EXTRA_SNAPSHOT, json);
                startActivity(intent);
                finish();
            }
        } catch (Exception e) {
            showToast("Failed to parse lobby state.");
        }
    }

    private void updateLobbyUi(JSONArray players) {
        lobbyTitleTxt.setText("Lobby " + currentLobbyCount(players) + "/" + desiredPlayers);

        for (int i = 0; i < playerTxt.length; i++) {
            if (players != null && i < players.length()) {
                JSONObject player = players.optJSONObject(i);
                if (player == null) {
                    playerTxt[i].setText("Waiting for player...");
                    continue;
                }

                int id = player.optInt("id", -1);
                boolean ready = player.optBoolean("ready", false);
                if (id == myId) {
                    amReady = ready;
                }

                String label = player.optString("name", "Player " + (i + 1));
                if (id == hostId) {
                    label += " (Host)";
                }
                label += ready ? "  - Ready" : "  - Waiting";
                playerTxt[i].setText(label);
            } else {
                playerTxt[i].setText("Waiting for player...");
            }
        }

        readyBtn.setEnabled(client != null && client.isConnected() && !launchingGame);
        readyBtn.setText(amReady ? "Not Ready" : "Ready");

        boolean isHost = myId > 0 && myId == hostId;
        startBtn.setVisibility(isHost ? View.VISIBLE : View.GONE);
        startBtn.setEnabled(isHost && allConnected && allReady);

        if (isHost) {
            if (!allConnected) {
                startBtn.setText("Waiting for Players");
            } else if (!allReady) {
                startBtn.setText("Waiting for Ready");
            } else {
                startBtn.setText("Start Game");
            }
        }
    }

    private int currentLobbyCount(JSONArray players) {
        return players == null ? 0 : players.length();
    }

    private String sanitizeName(String name) {
        String cleaned = name == null ? "" : name.replace("|", "").trim();
        if (cleaned.length() > 20) {
            return cleaned.substring(0, 20);
        }
        return cleaned;
    }

    private void showToast(String msg) {
        Toast.makeText(getApplicationContext(), msg, Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        if (hostScanner != null) {
            hostScanner.stop();
        }

        if (isFinishing() && !launchingGame) {
            if (client != null) {
                client.disconnect();
            }
            UnoSession.stopHostService();
        }
    }

    private String getLocalIpv4Address() {
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface network = interfaces.nextElement();
                if (!network.isUp() || network.isLoopback()) {
                    continue;
                }

                Enumeration<InetAddress> addresses = network.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress address = addresses.nextElement();
                    if (address instanceof Inet4Address && !address.isLoopbackAddress()) {
                        return address.getHostAddress();
                    }
                }
            }
        } catch (Exception ignored) {
        }

        return "LAN IP unknown";
    }
}
