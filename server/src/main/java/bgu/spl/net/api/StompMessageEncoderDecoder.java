package bgu.spl.net.api;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

public class StompMessageEncoderDecoder implements MessageEncoderDecoder<String> {
    
    private static final byte TERMINATOR = '\u0000';  // סימן סיום ההודעה בפרוטוקול STOMP
    private byte[] buffer = new byte[1024];     
    private int length = 0;    

    @Override
    public String decodeNextByte(byte nextByte) {
        if (nextByte == TERMINATOR) {
            String message = new String(buffer, 0, length, StandardCharsets.UTF_8);
            length = 0; 
            return message;
        }

        if (length >= buffer.length) {
            buffer = Arrays.copyOf(buffer, length * 2); 
        }
        buffer[length++] = nextByte;
        return null; 
    }

    @Override
    public byte[] encode(String message) {
        return (message + '\u0000').getBytes(StandardCharsets.UTF_8);
    }
}
