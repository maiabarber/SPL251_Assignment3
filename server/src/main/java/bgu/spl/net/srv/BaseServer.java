package bgu.spl.net.srv;

import bgu.spl.net.api.MessageEncoderDecoder;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import bgu.spl.net.api.StompMessagingProtocol;

public abstract class BaseServer<T> implements Server<T> {

    private final int port;
    private final Supplier<StompMessagingProtocol<T>> protocolFactory;
    private final Supplier<MessageEncoderDecoder<T>> encdecFactory;
    private ServerSocket sock;
    private final Connections<T> connections;
    private final AtomicInteger connectionIdCounter = new AtomicInteger(0);

    public BaseServer(
            int port,
            Supplier<StompMessagingProtocol<T>> protocolFactory,
            Supplier<MessageEncoderDecoder<T>> encdecFactory) {

        this.port = port;
        this.protocolFactory = protocolFactory;
        this.encdecFactory = encdecFactory;
		this.sock = null;
        this.connections = new ConnectionsImpl<>();  
    }

    @Override
    public void serve() {
        try (ServerSocket serverSock = new ServerSocket(port)) {
			System.out.println("Server started");

            this.sock = serverSock; 

            while (!Thread.currentThread().isInterrupted()) {
                Socket clientSock = serverSock.accept();
                MessageEncoderDecoder<T> encoderDecoder = encdecFactory.get();
                StompMessagingProtocol<T> protocol = protocolFactory.get();
                int connectionId = connectionIdCounter.incrementAndGet();

                BlockingConnectionHandler<T> handler = new BlockingConnectionHandler<>(clientSock,encoderDecoder,protocol,connectionId,connections);
                connections.addConnection(connectionId, handler);
                protocol.start(connectionId, connections);
                execute(handler);}
        } catch (IOException ex) {
        }
        System.out.println("server closed!!!");
    }

    @Override
    public void close() throws IOException {
		if (sock != null && !sock.isClosed()) {
            sock.close();
            System.out.println("Server socket closed.");
        }
    }

    protected abstract void execute(BlockingConnectionHandler<T>  handler);
}
