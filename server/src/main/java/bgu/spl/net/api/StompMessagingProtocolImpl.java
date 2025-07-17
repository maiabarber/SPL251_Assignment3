package bgu.spl.net.api;
import java.util.Map;

import bgu.spl.net.srv.Connections;


public class StompMessagingProtocolImpl implements StompMessagingProtocol<String> {

    private int connectionId;
    private Connections<String> connections;
    private boolean shouldTerminate = false;

    @Override
    //initialize the protocol with the active connections data structure
    public synchronized void start(int connectionId, Connections<String> connections) {
        this.connectionId = connectionId;
        this.connections = connections;
    }

    @Override
    public synchronized void process(String message) {
        String[] lines = message.split("\n");
        String command = lines[0].trim();

        switch (command) {
            case "CONNECT":
                handleConnect(lines, message);
                break;
            case "SEND":
                handleSend(lines, message);
                break;
            case "SUBSCRIBE":
                handleSubscribe(lines);
                break;
            case "DISCONNECT":
                handleDisconnect(message);
                break;
            case "UNSUBSCRIBE":
                handleUnsubscribe(lines);
                break;
            default:
                connections.send(connectionId, "ERROR\nmessage:\n did not contain a command, \n which is REQUIRED for message propagation\n\n");
                shouldTerminate = true;
                connections.removeFromActiveConnections(connectionId);
            }
    }

    private synchronized void handleConnect(String[] lines, String message) {
        String version = null, username = null, passcode = null, receiptId = null;

        for (String line : lines) {
            if (line.startsWith("accept-version:")) {
                version = line.split(":")[1].trim();
            } else if (line.startsWith("login:")) {
                username = line.split(":")[1].trim();
            } else if (line.startsWith("passcode:")) {
                passcode = line.split(":")[1].trim();
            }else if (line.startsWith("receipt:")) {
                receiptId = line.split(":")[1].trim();
            }
        }
        if (connections == null) {
            if (receiptId != null) {
                connections.send(connectionId, "ERROR\nreceipt-id:" + receiptId + "\n+message:\nConnections is null\n\n");   
            }else{
                connections.send(connectionId, "ERROR\nmessage:\nConnections is null\n\n");
            }
            shouldTerminate = true;
            return;
        }

        if (version == null ) {
            if (receiptId != null) {
                connections.send(connectionId, "ERROR\nreceipt-id:" + receiptId + "\n+message:\nMissing version in CONNECT frame\n\n");
                
            }else{
                connections.send(connectionId, "ERROR\nmessage:\nMissing version in CONNECT frame\n\n");
            }
            shouldTerminate = true;
            connections.removeFromActiveConnections(connectionId);
            return;
        }


        if (username == null ) {
            if (receiptId != null) {
                connections.send(connectionId, "ERROR\nreceipt-id:" + receiptId + "\n+message:\nMissing username in CONNECT frame\n\n");
                
            }else{
                connections.send(connectionId, "ERROR\nmessage:\nMissing username in CONNECT frame\n\n");
            }
            shouldTerminate = true;
            connections.removeFromActiveConnections(connectionId);
            return;   
        }
        if (passcode == null ) {
            if (receiptId != null) {
                connections.send(connectionId, "ERROR\nreceipt-id:" + receiptId + "\n+message:\nMissing passcode in CONNECT frame\n\n");
            }else{
                connections.send(connectionId, "ERROR\nmessage:Missing passcode in CONNECT frame\n\n");}
            shouldTerminate = true;
            connections.removeFromActiveConnections(connectionId);
            return;
        }
        
        if (connections.getConnectionToUser().containsValue(username)&&receiptId==null) {
            connections.send(connectionId, "ERROR\nmessage:User already logged in\n\n");
            shouldTerminate = true;
            connections.removeFromActiveConnections(connectionId);
            return;
        }else if (connections.getConnectionToUser().containsValue(username)&&receiptId!=null) {
            connections.send(connectionId, "ERROR\nreceipt-id:" + receiptId + "\n+message:User already logged in\n\n");
            shouldTerminate = true;
            connections.removeFromActiveConnections(connectionId);
            return;
        }

        // Checks if the user credentials are correct
        if (connections.getUserCredentials().containsKey(username)) {
            //בודק שהססמה נכונה
            //לא נכונה
            if (!connections.getUserCredentials().get(username).equals(passcode)) {
                if (receiptId != null) {
                    connections.send(connectionId, "ERROR\nreceipt-id:" + receiptId + "\n+message:Wrong password\n\n");  
                }else{
                    connections.send(connectionId, "ERROR\nmessage:Wrong password\n");
                }
                shouldTerminate = true;
                connections.removeFromActiveConnections(connectionId);
                return;
                }
            //ססמה נכונה תמשיך
        //משתמש חדש
        } else {
            //הוספה לשמות וססמאות
            connections.addUserCredentials(username, passcode);
            }
        //מוסיף למפה של id ושם משתמש
        connections.connectUser(connectionId, username);
        connections.send(connectionId, "CONNECTED\nversion:1.2\n\n");
    }

    private synchronized void handleSend(String[] lines, String message) {
        String destination= null , receiptId = null,user=null;
        StringBuilder body = new StringBuilder();

        for (String line : lines) {
            if (line.startsWith("destination:")) {
                destination = line.split(":/")[1].trim();
            }else if(line.startsWith("SEND")){
                continue;
            }else if(line.startsWith("user:")){
                user = line.split(":")[1].trim();}
            else {
                body.append(line).append("\n");
            }
        } 
        if(!connections.isSubscribed(destination, connectionId)&&receiptId==null){
            connections.send(connectionId, "ERROR\nmessage:client is not subscribed to this channel\n\n");
            connections.disconnect(connectionId); 
            shouldTerminate = true;
            return;
        }
        else if (receiptId != null&&!connections.isSubscribed(destination, connectionId)) {
            // שליחת אישור לשליחת ההודעה (RECEIPT) עם ה- receipt-id שנשלח על ידי הלקוח
            connections.send(connectionId, "ERROR\nreceipt-id:" + receiptId + "\n+message:client is not subscribed to this channel\n\n");
        }
        if (destination == null || body.toString().isEmpty()&&receiptId==null) {
            connections.send(connectionId, "ERROR\nmessage:Invalid SEND frame\n\n");
            return;
        }else if (destination == null || body.toString().isEmpty()&&receiptId!=null) {
            connections.send(connectionId, "ERROR\nreceipt-id:" + receiptId + "\n+message:Invalid SEND frame\n\n");
            return;
        }

        int messageId = connections.generateMessageId(destination);
        int subscriptionId = connections.getSubscriptionId(connectionId, destination);
        if (subscriptionId == -1&&receiptId==null) {
            connections.send(connectionId, "ERROR\nmessage:Client is not subscribed to the channel\n\n");///לבדוק הדפסה
            return;
            
        }else if (subscriptionId == -1&&receiptId!=null) {
            connections.send(connectionId, "ERROR\nreceipt-id:" + receiptId + "\n+message:Client is not subscribed to the channel\n\n");///לבדוק הדפסה
            return;
        }
        String messageFrame = "MESSAGE\n" +
                   "subscription:" + subscriptionId + "\n" +
                   "message-id:" + messageId + "\n" +
                   "destination:" + destination + "\n\n" +
                   body.toString() + "\n\0"; 
        connections.send(destination, messageFrame);
    }
    
    private void handleSubscribe(String[] lines) {
        String channel = null;
        Integer id = null;//subscription id
        String receiptId = null;

        for (String line : lines) {
            if (line.startsWith("destination:")) {
                channel = line.split(":/ ")[1].trim();
            } else if (line.startsWith("id:")) {
                id = Integer.parseInt(line.split(":")[1].trim());
            } else if (line.startsWith("receipt:")) {
                receiptId = line.split(":")[1].trim();
            }
        }

        if (channel == null || id == null && receiptId == null) {
            connections.send(connectionId, "ERROR\nmessage:Missing headers in SUBSCRIBE frame\n\n");
            shouldTerminate = true;
            connections.disconnect(connectionId);
            return;
        }else if (channel == null || id == null && receiptId != null) {
            connections.send(connectionId, "ERROR\nreceipt-id:" + receiptId + "\n+message:Missing headers in SUBSCRIBE frame\n\n");
            shouldTerminate = true;
            connections.disconnect(connectionId);
            return;
        }

        if (connections.getChannelSubscriptions().containsKey(channel) &&
            connections.getChannelSubscriptions().get(channel).contains(connectionId)&&receiptId==null) {
            connections.send(connectionId, "ERROR\nmessage:Already subscribed to the channel\n\n");
            shouldTerminate = true;
            connections.disconnect(connectionId);
            return;
        }else if (connections.getChannelSubscriptions().containsKey(channel) &&connections.getChannelSubscriptions().get(channel).contains(connectionId)&&receiptId!=null){
            connections.send(connectionId, "ERROR\nreceipt-id:" + receiptId + "\n+message:Already subscribed to the channel\n\n");
            shouldTerminate = true;
            connections.disconnect(connectionId);
            return;
        }

        connections.subscribe(channel, connectionId, id);
        if (receiptId != null) {
            connections.send(connectionId, "RECEIPT\nreceipt-id:" + receiptId + "\n\n");
        }else{connections.send(connectionId, "RECEIPT\n\n");}
    }

    private void handleUnsubscribe(String[] lines) {
        Integer subscriptionId = null; 
        String receiptId = null;
    
        for (String line : lines) {
            if (line.startsWith("id:")) {
                subscriptionId = Integer.parseInt(line.split(":")[1].trim());
            } else if (line.startsWith("receipt:")) {
                receiptId = line.split(":")[1].trim();
            }
        }
    
        if (subscriptionId == null) {
            if (receiptId != null) {
                connections.send(connectionId, "ERROR\nreceipt-id:" + receiptId + "\n+message:Missing headers in UNSUBSCRIBE frame\n\n");
                
            }else{
                connections.send(connectionId, "ERROR\nmessage:Missing headers in UNSUBSCRIBE frame\n\n");
            }
            shouldTerminate = true;
            connections.disconnect(connectionId);
            return;
        }
    
        String channelToRemove = null;
        if (connections.getSubscriptionIds().containsKey(connectionId)) {
            for (Map.Entry<String, Integer> entry : connections.getSubscriptionIds().get(connectionId).entrySet()) {
                if (entry.getValue().equals(subscriptionId)) {
                    channelToRemove = entry.getKey();
                    break;
                }
            }
        }
        else {
            connections.send(connectionId, "ERROR\nmessage:the user is not subscribed to the channel\n\n"); 
        }

        
        if (channelToRemove == null) {
            if (receiptId != null) {
                connections.send(connectionId, "ERROR\nreceipt-id:" + receiptId + "\n+message:Subscription ID not found\n\n");
            }else{
                connections.send(connectionId, "ERROR\nmessage:Subscription ID not found\n\n");
            }
            shouldTerminate = true;
            connections.disconnect(connectionId);
            return;
        }
    
        connections.unsubscribe(channelToRemove, connectionId);
        if (receiptId != null) {
            connections.send(connectionId, "RECEIPT\nreceipt-id:" + receiptId + "\n\n");            
        }else{connections.send(connectionId, "RECEIPT\n\n");}
    }
    
    private void handleDisconnect(String message) {
        String[] lines = message.split("\n");
        String receiptId = null;
        
        for (String line : lines) {
            if (line.startsWith("receipt:")) {
                receiptId = line.substring("receipt:".length()).trim();
                break;
            }
        }
        if(receiptId!=null){
            connections.send(connectionId, "RECEIPT\nreceipt-id:" + receiptId + "\n\n");
        } else {
            connections.send(connectionId, "ERROR\nmessage:Missing receipt in DISCONNECT frame\n\n");
        }
        shouldTerminate = true;
        connections.disconnect(connectionId);
    }

    @Override
    public boolean shouldTerminate() {
        return shouldTerminate;
    }
}