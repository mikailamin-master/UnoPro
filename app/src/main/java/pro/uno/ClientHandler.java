package pro.uno;

import java.net.*;
import java.io.*;

public class ClientHandler extends Thread {

    private Socket socket;
    private HostService server;
    private BufferedReader in;
    private PrintWriter out;
    public int player_id;

    public ClientHandler(Socket socket, HostService server, int id) {
        this.socket = socket;
        this.server = server;
        this.player_id = id;
    }

    @Override
    public void run() {
        try {
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            out = new PrintWriter(socket.getOutputStream(), true);

            send("id|" + player_id);

            String msg;
            while ((msg = in.readLine()) != null) {
                server.handle_message(msg, player_id);
            }
        } catch (Exception e) {
        } finally {
            server.remove_client(this);
            try { socket.close(); } catch (Exception ignored) {}
        }
    }

    public void send(String msg) {
        if (out != null) out.println(msg);
    }

    public void close() {
        try {
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
        } catch (Exception ignored) {}
    }
}
