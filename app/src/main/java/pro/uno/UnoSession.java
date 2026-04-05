package pro.uno;

public final class UnoSession {

    private static HostService hostService;

    private UnoSession() {
    }

    public static synchronized void setHostService(HostService service) {
        hostService = service;
    }

    public static synchronized HostService getHostService() {
        return hostService;
    }

    public static synchronized void clearHostService() {
        hostService = null;
    }

    public static synchronized void stopHostService() {
        if (hostService != null) {
            hostService.stop_server();
            hostService = null;
        }
    }
}
