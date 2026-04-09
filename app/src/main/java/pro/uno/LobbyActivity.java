package pro.uno;

import android.content.Intent;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.ArrayList;
import java.util.Enumeration;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

public class LobbyActivity extends BaseMaterialActivity {

    private EditText nameInput;
    private RadioGroup modeGroup;
    private RadioButton hostMode;
    private TextView lobbyTitleTxt;
    private TextView lobbyStatusTxt;
    private TextView connectionTxt;
    private TextView[] playerTxt;
    private MaterialButton hostBtn;
    private MaterialButton singlePlayerBtn;
    private MaterialButton joinBtn;
    private MaterialButton readyBtn;
    private MaterialButton startBtn;

    private ClientService client;
    private LanHostScanner hostScanner;

    private String myName = "";
    private int myId = -1;
    private int desiredPlayers = 2;
    private int hostId = -1;
    private boolean isSinglePlayer = false;
    private boolean isScanning = false;
    private boolean allConnected = false;
    private boolean allReady = false;
    private boolean amReady = false;
    private boolean launchingGame = false;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_lobby);

        bindViews();
        setupButtons();
        updateLobbyUi(null);
    }

    @Override
    protected int getRootViewId() {
        return R.id.lobby_root;
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
        singlePlayerBtn = findViewById(R.id.single_player_btn);
        joinBtn = findViewById(R.id.join_btn);
        readyBtn = findViewById(R.id.ready_btn);
        startBtn = findViewById(R.id.start_btn);

        nameInput.setText(getString(R.string.default_player_name, ((System.currentTimeMillis() / 1000) % 100)));
    }
private void setupButtons() {
    hostBtn.setOnClickListener(v -> {
        if (client != null && client.isConnected()) {
            leaveLobby();
            return;
        }
        if (!prepareName()) {
            return;
        }
        showHostPlayerCountDialog();
    });

    singlePlayerBtn.setOnClickListener(v -> {
        if (!prepareName()) {
            return;
        }
        showSinglePlayerAiCountDialog();
    });

    joinBtn.setOnClickListener(v -> {
        if (client != null && client.isConnected()) {
            leaveLobby();
            return;
        }
        if (!prepareName()) {
            return;
        }
        scanAndJoinHost();
    });

    readyBtn.setOnClickListener(v -> {
        if (client == null || !client.isConnected()) {
            showToast(getString(R.string.toast_join_lobby_first));
            return;
        }
        client.send("ready|" + (!amReady ? 1 : 0));
    });

    startBtn.setOnClickListener(v -> {
        if (isSinglePlayer) {
            // In singleplayer mode, we are the host, send start directly
            if (client != null && client.isConnected()) {
                client.send("start");
            } else {
                showToast(getString(R.string.toast_not_connected));
            }
            return;
        }

        if (client == null || !client.isConnected()) {
            showToast(getString(R.string.toast_join_lobby_first));
            return;
        }
        client.send("start");
    });        modeGroup.setOnCheckedChangeListener((group, checkedId) -> updateModeButtons());
        updateModeButtons();
    }

    private boolean prepareName() {
        myName = sanitizeName(nameInput.getText().toString());
        if (myName.isEmpty()) {
            showToast(getString(R.string.toast_enter_name));
            return false;
        }
        return true;
    }

    private void updateModeButtons() {
        boolean hostSelected = hostMode.isChecked();
        boolean connected = client != null && client.isConnected();

        // Host section
        if (hostSelected) {
            hostBtn.setVisibility(View.VISIBLE);
            singlePlayerBtn.setVisibility(connected || isSinglePlayer ? View.GONE : View.VISIBLE);
            joinBtn.setVisibility(View.GONE);
            hostBtn.setText(connected ? R.string.leave_lobby : R.string.create_lobby);
        } else {
            // Join section
            hostBtn.setVisibility(View.GONE);
            singlePlayerBtn.setVisibility(View.GONE);
            joinBtn.setVisibility(isScanning ? View.GONE : View.VISIBLE);
            joinBtn.setText(connected ? R.string.leave_lobby : R.string.join_lobby);
        }
    }

    private void showHostPlayerCountDialog() {
        String[] items = getResources().getStringArray(R.array.player_count_options);

        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.dialog_how_many_players)
                .setItems(items, (dialog, which) -> {
                    desiredPlayers = which + 2;
                    startHosting(false, 0);
                })
                .setCancelable(false)
                .show();
    }

    private void showSinglePlayerAiCountDialog() {
        String[] items = {
                getString(R.string.ai_opponent_1),
                getString(R.string.ai_opponent_2),
                getString(R.string.ai_opponent_3)
        };

        final String[] botNames = {"AutoBOT", "MicroBOT", "Android"};

        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.dialog_ai_opponents_title)
                .setItems(items, (dialog, which) -> {
                    int aiCount = which + 1;
                    desiredPlayers = aiCount + 1;
                    startHosting(true, aiCount, botNames);
                })
                .setCancelable(true)
                .show();
    }

    private void showJoinIpDialog() {
        final EditText ipInput = new EditText(this);
        ipInput.setInputType(InputType.TYPE_CLASS_TEXT);
        ipInput.setHint(R.string.host_lan_ip_hint);

        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.dialog_join_by_ip)
                .setView(ipInput)
                .setCancelable(false)
                .setPositiveButton(R.string.join, (d, which) -> {
                    String ip = ipInput.getText().toString().trim();
                    if (ip.isEmpty()) {
                        showToast(getString(R.string.toast_enter_host_ip));
                        showJoinIpDialog();
                        return;
                    }
                    connectAsClient(ip, 0);
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void scanAndJoinHost() {
        isScanning = true;
        updateModeButtons();
        lobbyStatusTxt.setText(R.string.status_scanning_hosts);
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
                    isScanning = false;
                    updateModeButtons();
                    if (isFinishing()) {
                        return;
                    }

                    if (discovered.size() == 1) {
                        LanHostScanner.DiscoveredHost host = discovered.get(0);
                        showToast(getString(R.string.toast_found_host, host.name, host.ip));
                        connectAsClient(host.ip, 0);
                        return;
                    }

                    if (discovered.isEmpty()) {
                        showToast(getString(R.string.toast_no_host_found));
                        showJoinIpDialog();
                        return;
                    }

                    showHostSelectionDialog(discovered);
                });
            }

            @Override
            public void onError(String message) {
                runOnUiThread(() -> {
                    isScanning = false;
                    updateModeButtons();
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
            String state = getString(host.started ? R.string.label_host_state_running : R.string.label_host_state_lobby);
            items[i] = getString(
                    R.string.label_host_selection,
                    host.name,
                    host.ip,
                    host.currentPlayers,
                    host.targetPlayers,
                    state
            );
        }

        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.dialog_select_host)
                .setItems(items, (dialog, which) -> connectAsClient(discovered.get(which).ip, 0))
                .setNegativeButton(R.string.manual_ip, (dialog, which) -> showJoinIpDialog())
                .setPositiveButton(R.string.rescan, (dialog, which) -> scanAndJoinHost())
                .show();
    }

    private void startHosting(boolean withAI, int aiCount) {
        startHosting(withAI, aiCount, null);
    }

    private void startHosting(boolean withAI, int aiCount, String[] botNames) {
        isSinglePlayer = withAI;
        HostService hostService = new HostService();
        UnoSession.setHostService(hostService);
        hostService.start_server(UnoConfig.TCP_PORT, desiredPlayers, myName);

        connectionTxt.setText(getString(R.string.status_hosting_on, getLocalIpv4Address()));
        lobbyStatusTxt.setText(R.string.status_waiting_players_connect);
        updateModeButtons();

        // Single delayed block to avoid race conditions
        connectionTxt.postDelayed(() -> {
            connectAsClient("127.0.0.1", desiredPlayers);

            new Thread(() -> {
                // Add bots after connecting the user, in background
                try {
                    Thread.sleep(300);
                    if (withAI && botNames != null) {
                        for (int i = 0; i < aiCount; i++) {
                            hostService.addAIPlayer(botNames[i]);
                        }
                    }
                } catch (InterruptedException ignored) {}
            }).start();
        }, 250);    }
    
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
                    connectionTxt.setText(getString(R.string.status_connected_to, ip));
                    lobbyStatusTxt.setText(R.string.status_connected_waiting_lobby);
                    hostBtn.setEnabled(true);
                    joinBtn.setEnabled(true);
                    nameInput.setEnabled(false);
                    hostMode.setEnabled(false);
                    RadioButton joinRadio = findViewById(R.id.join_mode);
                    joinRadio.setEnabled(false);
                    updateModeButtons();
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
                    isSinglePlayer = false;
                    allConnected = false;
                    allReady = false;
                    amReady = false;
                    lobbyStatusTxt.setText(R.string.status_disconnected_server);
                    connectionTxt.setText(R.string.connection_not_connected);
                    nameInput.setEnabled(true);
                    hostMode.setEnabled(true);
                    RadioButton joinRadio = findViewById(R.id.join_mode);
                    joinRadio.setEnabled(true);
                    hostBtn.setEnabled(true);
                    joinBtn.setEnabled(true);
                    updateModeButtons();
                    updateLobbyUi(null);
                });
            }

            @Override
            public void onAssignedId(int id) {
                myId = id;
                client.send("join|" + myName + "|" + requestedPlayers);
            }
        });

        client.connect(ip, UnoConfig.TCP_PORT);
    }

    private void leaveLobby() {
        if (client != null) {
            client.disconnect();
        }
        UnoSession.stopHostService();
        isSinglePlayer = false;
        updateModeButtons();
    }

    private void handleServerMessage(String msg) {
        if (msg == null) {
            return;
        }

        if (msg.startsWith("snapshot|")) {
            String json = msg.substring(9);
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
            LobbySnapshot snapshot = LobbySnapshot.fromJson(json, myId, desiredPlayers);
            myId = snapshot.myId;
            hostId = snapshot.hostId;
            desiredPlayers = snapshot.desiredPlayers;
            allConnected = snapshot.allConnected;
            allReady = snapshot.allReady;

            if (!snapshot.status.isEmpty()) {
                lobbyStatusTxt.setText(snapshot.status);
            }

            amReady = false;
            updateLobbyUi(snapshot);

            if (snapshot.started && !launchingGame) {
                launchingGame = true;
                Intent intent = new Intent(this, GameActivity.class);
                intent.putExtra(GameActivity.EXTRA_SNAPSHOT, json);
                startActivity(intent);
                finish();
            }
        } catch (Exception e) {
            showToast(getString(R.string.toast_failed_parse_lobby));
        }
    }

    private void updateLobbyUi(LobbySnapshot snapshot) {
        int lobbyCount = snapshot == null ? 0 : snapshot.players.size();
        lobbyTitleTxt.setText(getString(R.string.lobby_title_format, lobbyCount, desiredPlayers));

        for (int i = 0; i < playerTxt.length; i++) {
            if (snapshot != null && i < snapshot.players.size()) {
                LobbySnapshot.Player player = snapshot.players.get(i);
                int id = player.id;
                boolean ready = player.ready;
                if (id == myId) {
                    amReady = ready;
                }

                playerTxt[i].setText(LobbyUiFormatter.buildPlayerLabel(this, player.name, id, hostId, ready));
            } else {
                playerTxt[i].setText(R.string.waiting_for_player);
            }
        }

        readyBtn.setEnabled(client != null && client.isConnected() && !launchingGame);
        readyBtn.setText(amReady ? R.string.not_ready : R.string.ready);
        readyBtn.setVisibility(isSinglePlayer ? View.GONE : View.VISIBLE);

        boolean isHost = myId > 0 && myId == hostId;
        boolean canStart = isHost && allConnected && allReady;
        
        startBtn.setVisibility(isHost || isSinglePlayer ? View.VISIBLE : View.GONE);
        startBtn.setEnabled(isSinglePlayer || canStart);

        if (isHost) {
            startBtn.setText(isSinglePlayer ? getString(R.string.start_game) : LobbyUiFormatter.buildStartButtonLabel(this, allConnected, allReady));
        }
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

        return getString(R.string.lan_ip_unknown);
    }
}
