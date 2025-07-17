package bgu.spl.net.srv;

class ConnectionHandlerImpl<T> implements ConnectionHandler<T> {
    private final int connectionId;
    private final Connections<T> connections;

    public ConnectionHandlerImpl(int connectionId, Connections<T> connections) {
        this.connectionId = connectionId;
        this.connections = connections;
    }

    @Override
    public void send(T msg) {
        connections.send(connectionId, msg);
    }

    @Override
    public void close() {
        connections.disconnect(connectionId);
    }
}