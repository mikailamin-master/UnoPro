package pro.uno;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;

public class ClientService {

    private static ClientService instance;

    public static synchronized ClientService getInstance() {
        if (instance == null) {
            instance = new ClientService();
        }
        return instance;
    }

    private Socket socket;
    private BufferedReader in;
    private PrintWriter out;
    public int my_id = -1;
    private boolean connected = false;

    public interface ClientListener {
        void onBroadcast(String msg);
        void onConnected();
        void onDisconnected();
        void onAssignedId(int id);
    }

    private ClientListener listener;

    public synchronized void setClientListener(ClientListener listener) {
        this.listener = listener;
    }

    public synchronized boolean isConnected() {
        return connected;
    }

    public void connect(String ip, int port) {
        disconnect();
        Thread connectThread = new Thread(() -> {
            try {
                socket = new Socket(ip, port);
                in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                out = new PrintWriter(socket.getOutputStream(), true);
                connected = true;

                if (listener != null) {
                    listener.onConnected();
                }

                listenLoop();
            } catch (Exception e) {
                connected = false;
                if (listener != null) {
                    listener.onDisconnected();
                }
            }
        }, "uno-client-connect");
        connectThread.start();
    }

    public void send(String msg) {
        Thread sendThread = new Thread(() -> {
            if (out != null && connected) {
                out.println(msg);
            }
        }, "uno-client-send");
        sendThread.start();
    }

    public synchronized void disconnect() {
        connected = false;
        try {
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
        } catch (Exception ignored) {
        }
        socket = null;
        in = null;
        out = null;
        my_id = -1;
    }

    private void listenLoop() {
        Thread listenThread = new Thread(() -> {
            try {
                String msg;
                while (connected && in != null && (msg = in.readLine()) != null) {
                    handle_message(msg);
                }
            } catch (Exception ignored) {
            } finally {
                connected = false;
                if (listener != null) {
                    listener.onDisconnected();
                }
            }
        }, "uno-client-listen");
        listenThread.start();
    }

    private void handle_message(String msg) {
        String[] parts = msg.split("\\|", 2);
        if (parts.length > 0 && "id".equals(parts[0]) && parts.length >= 2) {
            try {
                my_id = Integer.parseInt(parts[1]);
                if (listener != null) {
                    listener.onAssignedId(my_id);
                }
            } catch (NumberFormatException ignored) {
            }
        }

        if (listener != null) {
            listener.onBroadcast(msg);
        }
    }
}
