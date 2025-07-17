package bgu.spl.net.srv;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public interface Connections<T> {
    //send
    boolean send(int connectionId, T msg);
    void send(String channel, T msg);

    //פעולות
    void disconnect(int connectionId);
    boolean isSubscribed(String channel, int connectionId);
    void subscribe(String channel, int connectionId, int subscriptionId);
    void unsubscribe(String channel, int connectionId);
    void connectUser(int connectionId, String username);
    
    //add and remove
    void addConnection(int connectionId, ConnectionHandler<T> handler);
    void addUserCredentials(String username, String password);
    void removeFromActiveConnections(int connectionId);
    int getSubscriptionId(int connectionId, String channel);//חדש

    //get
    ConcurrentMap<Integer, ConnectionHandler<T>> getActiveConnections();
    ConcurrentMap<String, Set<Integer>> getChannelSubscriptions();
    ConcurrentHashMap<String, String> getUserCredentials();
    ConcurrentHashMap<Integer, String> getConnectionToUser();  
    ConcurrentMap<Integer, ConcurrentHashMap<String, Integer>> getSubscriptionIds();  
   
    
    int generateMessageId(String channel);


}
