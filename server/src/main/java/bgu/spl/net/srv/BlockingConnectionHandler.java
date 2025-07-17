package bgu.spl.net.srv;

import bgu.spl.net.api.MessageEncoderDecoder;
import bgu.spl.net.api.StompMessagingProtocol;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.net.Socket;

public class BlockingConnectionHandler<T> implements Runnable, ConnectionHandler<T> {

    private final StompMessagingProtocol<T> protocol;
    private final MessageEncoderDecoder<T> encdec;
    private final Socket sock;
    private BufferedInputStream in;
    private BufferedOutputStream out;
    private volatile boolean connected = true;
    private int connectionId; 
    private Connections<T> connections;

    public BlockingConnectionHandler(Socket sock, MessageEncoderDecoder<T> reader, StompMessagingProtocol<T> protocol, int connectionId, Connections<T> connections) {
        this.sock = sock;
        this.encdec = reader;
        this.protocol = protocol;
        this.connectionId = connectionId;
        this.connections = connections;

        try {
            this.in = new BufferedInputStream(sock.getInputStream());
            this.out = new BufferedOutputStream(sock.getOutputStream());
        } catch (IOException e) {
            throw new RuntimeException("Failed to initialize streams", e);
        }
    }

    @Override
    public void run() {
        try (Socket sock = this.sock) { 
            int read;

            while (!protocol.shouldTerminate() && connected && (read = in.read()) >= 0) {
                T nextMessage = encdec.decodeNextByte((byte) read);
                if (nextMessage != null) {
                    protocol.process(nextMessage);
                }
            }
        } catch (IOException ex) {
            System.err.println("IOException in run method: " + ex.getMessage());
        } finally {
            connections.disconnect(connectionId);
            connected = false;
        }

    }

    @Override
    public synchronized void close() throws IOException {
        connected = false;
        sock.close();
    }


    @Override
    public synchronized void send(T msg) {
        if (msg == null || !connected) {
            System.out.println("Message is null or connection is closed. Cannot send.");
            return;
        }
        
        try {
            byte[] encodedMsg = encdec.encode(msg);  
            if (encodedMsg != null) {
                out.write(encodedMsg);
                out.flush();
            }
        } catch (IOException e) {
            System.err.println("Failed to send message due to IO Exception: " + e.getMessage());
            connected = false;  
            try {
                close();
            } catch (IOException ex) {
                System.err.println("Error closing socket after send failure: " + ex.getMessage());
            }
        }
    }
}