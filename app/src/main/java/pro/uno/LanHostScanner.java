package pro.uno;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InterfaceAddress;
import java.net.NetworkInterface;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class LanHostScanner {

    private static final String DISCOVERY_REQUEST = "UNO_DISCOVER_REQUEST_V1";

    public static class DiscoveredHost {
        public final String name;
        public final String ip;
        public final int port;
        public final int currentPlayers;
        public final int targetPlayers;
        public final boolean started;

        public DiscoveredHost(String name, String ip, int port, int currentPlayers, int targetPlayers, boolean started) {
            this.name = name;
            this.ip = ip;
            this.port = port;
            this.currentPlayers = currentPlayers;
            this.targetPlayers = targetPlayers;
            this.started = started;
        }
    }

    public interface ScanListener {
        void onHostFound(DiscoveredHost host);
        void onFinished();
        void onError(String message);
    }

    private volatile boolean scanning = false;
    private Thread scanThread;

    public synchronized void scan(int discoveryPort, int timeoutMs, ScanListener listener) {
        stop();
        scanning = true;

        scanThread = new Thread(() -> {
            DatagramSocket socket = null;
            try {
                socket = new DatagramSocket();
                socket.setBroadcast(true);
                socket.setSoTimeout(350);

                byte[] requestData = DISCOVERY_REQUEST.getBytes(StandardCharsets.UTF_8);
                List<InetAddress> broadcasts = collectBroadcastAddresses();

                sendProbe(socket, requestData, discoveryPort, broadcasts);

                long end = System.currentTimeMillis() + timeoutMs;
                long nextProbeAt = System.currentTimeMillis() + 1200;
                Set<String> seen = new HashSet<>();

                while (scanning && System.currentTimeMillis() < end) {
                    if (System.currentTimeMillis() >= nextProbeAt) {
                        sendProbe(socket, requestData, discoveryPort, broadcasts);
                        nextProbeAt = System.currentTimeMillis() + 1200;
                    }

                    try {
                        byte[] buffer = new byte[512];
                        DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                        socket.receive(packet);

                        String body = new String(packet.getData(), 0, packet.getLength(), StandardCharsets.UTF_8);
                        String[] parts = body.split("\\|");
                        if (parts.length < 6 || !"UNO_HOST_V1".equals(parts[0])) {
                            continue;
                        }

                        String name = parts[1].trim();
                        int port = parseInt(parts[2], 6000);
                        int current = parseInt(parts[3], 0);
                        int target = parseInt(parts[4], 0);
                        boolean started = "1".equals(parts[5]);

                        String ip = packet.getAddress().getHostAddress();
                        String key = ip + ":" + port;
                        if (seen.contains(key)) {
                            continue;
                        }
                        seen.add(key);

                        if (listener != null) {
                            listener.onHostFound(new DiscoveredHost(name, ip, port, current, target, started));
                        }
                    } catch (SocketTimeoutException ignored) {
                    }
                }
            } catch (Exception e) {
                if (listener != null) {
                    listener.onError("Failed to scan LAN hosts");
                }
            } finally {
                scanning = false;
                if (socket != null && !socket.isClosed()) {
                    socket.close();
                }
                if (listener != null) {
                    listener.onFinished();
                }
            }
        }, "uno-lan-scanner");

        scanThread.start();
    }

    public synchronized void stop() {
        scanning = false;
        if (scanThread != null && scanThread.isAlive()) {
            scanThread.interrupt();
        }
    }

    private void sendProbe(DatagramSocket socket, byte[] requestData, int port, List<InetAddress> broadcasts) {
        for (InetAddress address : broadcasts) {
            try {
                DatagramPacket packet = new DatagramPacket(requestData, requestData.length, address, port);
                socket.send(packet);
            } catch (Exception ignored) {
            }
        }
    }

    private List<InetAddress> collectBroadcastAddresses() {
        ArrayList<InetAddress> addresses = new ArrayList<>();
        try {
            addresses.add(InetAddress.getByName("255.255.255.255"));
        } catch (Exception ignored) {
        }

        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface iface = interfaces.nextElement();
                if (!iface.isUp() || iface.isLoopback()) {
                    continue;
                }
                for (InterfaceAddress interfaceAddress : iface.getInterfaceAddresses()) {
                    InetAddress broadcast = interfaceAddress.getBroadcast();
                    if (broadcast != null) {
                        addresses.add(broadcast);
                    }
                }
            }
        } catch (Exception ignored) {
        }

        return addresses;
    }

    private int parseInt(String value, int fallback) {
        try {
            return Integer.parseInt(value);
        } catch (Exception e) {
            return fallback;
        }
    }
}
