package com.xam.bobgame.net;

import com.badlogic.gdx.utils.Pools;
import com.esotericsoftware.kryonet.Client;
import com.esotericsoftware.kryonet.Connection;
import com.esotericsoftware.kryonet.Listener;
import com.esotericsoftware.kryonet.Serialization;
import com.esotericsoftware.minlog.Log;
import com.xam.bobgame.events.DisconnectEvent;
import com.xam.bobgame.events.EventsSystem;
import com.xam.bobgame.utils.GameProfile;

import java.io.IOException;

public class NetClient extends Client {

    private final NetDriver netDriver;
    int hostId = -1;

    public NetClient(NetDriver netDriver, Serialization serialization) {
        super(8192, 2048, serialization);
        this.netDriver = netDriver;
        addListener(listener);
    }

    public boolean connect(String host) {
        if (isConnected()) return false;
        start();
        try {
            connect(5000, host, NetDriver.PORT_TCP, NetDriver.PORT_UDP);
            GameProfile.lastConnectedServerAddress = host;
            netDriver.setMode(NetDriver.Mode.Client);
            return true;
        } catch (IOException e) {
            stop();
            e.printStackTrace();
        }
        return false;
    }

    void setHostId(int hostId) {
        this.hostId = hostId;
    }

    public void disconnect() {
        netDriver.connectionManager.sendDisconnect(hostId);

        DisconnectEvent event = Pools.obtain(DisconnectEvent.class);
        netDriver.getEngine().getSystem(EventsSystem.class).queueEvent(event);

        stop();
    }

    @Override
    public void stop() {
        if (!isConnected()) return;
        super.stop();
    }

    private Listener listener = new Listener() {
        @Override
        public void connected(Connection connection) {
            int clientId = netDriver.connectionManager.addConnection(connection);
            netDriver.connectionManager.initiateHandshake(clientId);
            netDriver.connectionManager.getConnectionSlot(clientId).packetBuffer.setFrameDelay(NetDriver.JITTER_BUFFER_SIZE);
            netDriver.connectionManager.getConnectionSlot(clientId).packetBuffer.setSimulationDelay(NetDriver.BUFFER_TIME_LIMIT);
        }

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
