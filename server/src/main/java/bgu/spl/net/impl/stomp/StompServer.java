package bgu.spl.net.impl.stomp;

import bgu.spl.net.api.StompMessagingProtocolImpl;
import bgu.spl.net.srv.Server;
import bgu.spl.net.api.StompMessageEncoderDecoder;

public class StompServer {

    public static void main(String[] args) {
        if((args[1]).equals("tpc")){
            Server.threadPerClient(
                    7777,
                    () -> new StompMessagingProtocolImpl(),
                    StompMessageEncoderDecoder::new 
            ).serve();}

        else if((args[1]).equals("reactor")){
            Server.reactor(
                     Runtime.getRuntime().availableProcessors(),
                     7777,
                     () -> new StompMessagingProtocolImpl(),
                     StompMessageEncoderDecoder::new //message encoder decoder factory
            ).serve();}
        else {
            System.out.println("Invalid server type. Use 'tpc' or 'reactor'");
        }
    }
}

