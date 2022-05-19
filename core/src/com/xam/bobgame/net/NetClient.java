package com.xam.bobgame.net;

import com.badlogic.gdx.utils.Pools;
import com.esotericsoftware.kryonet.Client;
import com.esotericsoftware.kryonet.Connection;
import com.esotericsoftware.kryonet.Listener;
import com.esotericsoftware.kryonet.Serialization;
import com.esotericsoftware.minlog.Log;
import com.xam.bobgame.BoBGame;
import com.xam.bobgame.GameEngine;
import com.xam.bobgame.events.classes.DisconnectEvent;
import com.xam.bobgame.events.EventsSystem;
import com.xam.bobgame.GameProfile;

import java.io.IOException;

public class NetClient extends Client {

    private final NetDriver netDriver;
    int hostId = -1;

    String connectHost = null;
    int reconnectSalt = 0;

    private boolean connecting = false;

    public NetClient(NetDriver netDriver, Serialization serialization) {
        super(8192, 2048, serialization);
        this.netDriver = netDriver;
        addListener(listener);
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
            stop();
            e.printStackTrace();
        }
        ((GameEngine) netDriver.getEngine()).setMode(GameEngine.Mode.None);
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

        DisconnectEvent event = Pools.obtain(DisconnectEvent.class);
        netDriver.getEngine().getSystem(EventsSystem.class).queueEvent(event);

        reconnectSalt = 0;
        GameProfile.clientSalt = 0;
        hostId = -1;

        stop();
    }

    @Override
    public void stop() {
        if (!isConnected()) return;
        super.stop();
    }

    public void requestSnapshot() {
        ConnectionManager.ConnectionSlot slot = netDriver.connectionManager.getConnectionSlot(hostId);
        slot.needsSnapshot = true;
    }

    private Listener listener = new Listener() {
        @Override
        public void connected(Connection connection) {
            hostId = netDriver.connectionManager.addConnection(connection, false);
            connecting = false;
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
    };
}
