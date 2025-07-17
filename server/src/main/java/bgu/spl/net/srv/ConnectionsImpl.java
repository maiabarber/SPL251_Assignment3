package bgu.spl.net.srv;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.Set;

public class ConnectionsImpl<T> implements Connections<T> {
    private ConcurrentHashMap<String, String> userCredentials = new ConcurrentHashMap<>();//username, password
    private ConcurrentHashMap<Integer, String> connectionToUser = new ConcurrentHashMap<>();//connectionId, username
    private ConcurrentMap<Integer, ConnectionHandler<T>> activeConnections = new ConcurrentHashMap<>();//connectionId, handler
    private ConcurrentMap<String, Set<Integer>> channelSubscriptions = new ConcurrentHashMap<>();//channel, subscribers
    private final ConcurrentMap<String, AtomicInteger> messageIdCounters = new ConcurrentHashMap<>();
    private final ConcurrentMap<Integer, ConcurrentHashMap<String, Integer>> subscriptionIds = new ConcurrentHashMap<>();//connectionId, channel, subscriptionId

    public ConcurrentMap<Integer, ConnectionHandler<T>> getActiveConnections(){
        return activeConnections;
    }

    public ConcurrentMap<String, Set<Integer>> getChannelSubscriptions(){
        return channelSubscriptions;
    }

    public ConcurrentHashMap<String, String> getUserCredentials(){
        return userCredentials;
    }

    public ConcurrentHashMap<Integer, String> getConnectionToUser(){
        return connectionToUser;
    }

    public void addUserCredentials(String username, String password){
        userCredentials.put(username, password);
    }

    public void removeFromActiveConnections(int connectionId){
        activeConnections.remove(connectionId);
    }

    public boolean addUser(String username, String password) {
        return userCredentials.putIfAbsent(username, password) == null;
    }

    public boolean authenticateUser(String username, String password) {
        return userCredentials.containsKey(username) && userCredentials.get(username).equals(password);
    }

    public void connectUser(int connectionId, String username) {
        connectionToUser.put(connectionId, username);
    }

    public String getUserByConnectionId(int connectionId) {
        return connectionToUser.get(connectionId);
    }

    public boolean isUserConnected(String username) {
        return connectionToUser.containsValue(username);
    }

    public boolean isSubscribed(String channel, int connectionId) {
        return channelSubscriptions.containsKey(channel) &&
           channelSubscriptions.get(channel).contains(connectionId);
    }

    @Override
    public synchronized boolean send(int connectionId, T msg) {
        ConnectionHandler<T> handler = activeConnections.get(connectionId);
        if (handler != null) {
            handler.send(msg);
            return true;
        }
        return false;
    }

    @Override
    public void send(String channel, T msg) {
        synchronized (this) {
            Set<Integer> subscribers = channelSubscriptions.get(channel);
        if (subscribers != null) {
            for (Integer subscriberId : subscribers) {
                ConnectionHandler<T> handler = activeConnections.get(subscriberId);
                if (handler != null) {
                    handler.send(msg);
                }
            }
        }
        }
    }

    @Override
    public synchronized void disconnect(int connectionId) {
        activeConnections.remove(connectionId);
        String username = connectionToUser.remove(connectionId);
        if (username != null) {
            channelSubscriptions.values().forEach(subscribers -> subscribers.remove(connectionId));
        }
        subscriptionIds.remove(connectionId); 
    }

    public synchronized void addConnection(int connectionId, ConnectionHandler<T> handler) {
        activeConnections.put(connectionId, handler);
    }

    public void subscribe(String channel, int connectionId, int subscriptionId) {
        channelSubscriptions.computeIfAbsent(channel, k -> new CopyOnWriteArraySet<>()).add(connectionId);
        subscriptionIds.computeIfAbsent(connectionId, k -> new ConcurrentHashMap<>()).put(channel, subscriptionId);
    }

    public void unsubscribe(String channel, int connectionId) {
        Set<Integer> subscribers = channelSubscriptions.get(channel);
        if (subscribers != null) {
            subscribers.remove(connectionId);
            if (subscribers.isEmpty()) {
                channelSubscriptions.remove(channel);
            }
            else {
                channelSubscriptions.put(channel, subscribers);
            }
        }
        else {
            throw new IllegalArgumentException("this channel does not exist");
        }
        ConcurrentHashMap<String, Integer> userSubscriptions = subscriptionIds.get(connectionId);
        if (userSubscriptions != null) {
            userSubscriptions.remove(channel);
            if (userSubscriptions.isEmpty()) {
                subscriptionIds.remove(connectionId);
            }
        }
        
}
    public synchronized int generateMessageId(String channel) {
        messageIdCounters.putIfAbsent(channel, new AtomicInteger(1));
        return messageIdCounters.get(channel).getAndIncrement();
    }

    public synchronized int getSubscriptionId(int connectionId, String channel) {
        return subscriptionIds.getOrDefault(connectionId, new ConcurrentHashMap<>()).getOrDefault(channel, -1);
    }

    public synchronized ConcurrentMap<Integer, ConcurrentHashMap<String, Integer>> getSubscriptionIds() {
        return subscriptionIds;
    }


}
