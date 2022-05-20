package com.xam.bobgame.net;

import com.badlogic.gdx.utils.ObjectMap;
import com.badlogic.gdx.utils.Pools;
import com.esotericsoftware.kryonet.Client;
import com.esotericsoftware.kryonet.Connection;
import com.esotericsoftware.kryonet.Listener;
import com.esotericsoftware.kryonet.Serialization;
import com.esotericsoftware.minlog.Log;
import com.xam.bobgame.BoBGame;
import com.xam.bobgame.GameEngine;
import com.xam.bobgame.events.EventListenerAdapter;
import com.xam.bobgame.events.GameEvent;
import com.xam.bobgame.events.GameEventListener;
import com.xam.bobgame.events.classes.ClientConnectedEvent;
import com.xam.bobgame.events.classes.ConnectionStateRefreshEvent;
import com.xam.bobgame.events.EventsSystem;
import com.xam.bobgame.GameProfile;

import java.io.IOException;

public class NetClient extends Client {

    private final NetDriver netDriver;
    ObjectMap<Class<? extends GameEvent>, GameEventListener> listeners = new ObjectMap<>();
    int hostId = -1;

    String connectHost = null;
    int reconnectSalt = 0;

    private boolean connecting = false;

    public NetClient(NetDriver netDriver, Serialization serialization) {
        super(8192, 8192, serialization);
        this.netDriver = netDriver;
        addListener(listener);
        listeners.put(ClientConnectedEvent.class, new EventListenerAdapter<ClientConnectedEvent>() {
            @Override
            public void handleEvent(ClientConnectedEvent event) {
                connecting = false;
            }
        });
    }

    public void setup() {
        reconnectSalt = GameProfile.clientSalt;
    }

    public boolean canReconnect() {
        return reconnectSalt != 0;
    }

    public boolean connect(String host) {
        if (isConnected()) return false;
        ((GameEngine) netDriver.getEngine()).setMode(GameEngine.Mode.Client);
        start();
        String[] add = host.split(":");
        connecting = true;
        connectHost = host;
        ConnectionStateRefreshEvent event = Pools.obtain(ConnectionStateRefreshEvent.class);
        netDriver.getEngine().getSystem(EventsSystem.class).triggerEvent(event);
        try {
            if (add.length > 1) {
                connect(5000, add[0], Integer.parseInt(add[1]));
            }
            else if (BoBGame.isNoUDP()) {
                connect(5000, host, NetDriver.PORT_TCP);
            }

            else {
                connect(5000, host, NetDriver.PORT_TCP, NetDriver.PORT_UDP);
            }
            return true;
        } catch (IOException e) {
            Log.warn("NetClient", "" + e.getClass() + " : " + e.getMessage());
            stop();
        }

        GameEngine engine = ((GameEngine) netDriver.getEngine());
        engine.setMode(GameEngine.Mode.None);
        engine.stop();
        connecting = false;
        return false;
    }

    public boolean isConnecting() {
        return connecting;
    }

    public int getHostId() {
        return hostId;
    }

    public void disconnect() {
        if (isConnected()) {
            netDriver.connectionManager.sendDisconnect(hostId);
        }

        ((GameEngine) netDriver.getEngine()).stop();

        reconnectSalt = 0;
        GameProfile.clientSalt = 0;

        stop();
    }

    @Override
    public void stop() {
        connecting = false;
        if (isConnected()) {
            super.stop();
            netDriver.connectionManager.removeConnection(hostId);
            hostId = -1;
        }
    }

    public void requestSnapshot() {
        ConnectionManager.ConnectionSlot slot = netDriver.connectionManager.getConnectionSlot(hostId);
        slot.needsSnapshot = true;
    }

    private Listener listener = new Listener() {
        @Override
        public void connected(Connection connection) {
            hostId = netDriver.connectionManager.addConnection(connection, false);
            netDriver.connectionManager.getConnectionSlot(hostId).originalHostAddress = connectHost;
            if (reconnectSalt != 0) {
                netDriver.connectionManager.initiateReconnect(hostId, reconnectSalt);
            }
            else {
                netDriver.connectionManager.initiateHandshake(hostId);
            }
//            netDriver.connectionManager.getConnectionSlot(clientId).packetBuffer.setSimulationDelay(NetDriver.BUFFER_TIME_LIMIT);
        }

        // TODO: Handle unsuccessful connection

        @Override
        public void received(Connection connection, Object o) {
            if (!(o instanceof Packet)) return;
            Packet packet = (Packet) o;
            ConnectionManager.ConnectionSlot connectionSlot = netDriver.connectionManager.getConnectionSlot(connection);
            if (connectionSlot == null) {
                Log.warn("No connection slot for connection with id " + connection.getID());
                return;
            }
            connectionSlot.packetBuffer.receive(packet);
        }

        @Override
        public void disconnected(Connection connection) {
            ConnectionStateRefreshEvent event = Pools.obtain(ConnectionStateRefreshEvent.class);
            netDriver.getEngine().getSystem(EventsSystem.class).queueEvent(event);
            stop();
        }
    };
}
