package com.xam.bobgame.net;

import com.badlogic.gdx.utils.Pool;
import com.badlogic.gdx.utils.Pools;
import com.esotericsoftware.kryonet.Connection;
import com.esotericsoftware.minlog.Log;

public class ConnectionManager {
    public static final int MAX_CLIENTS = 32;

    private NetDriver netDriver;

    private ConnectionSlot[] connectionSlots = new ConnectionSlot[MAX_CLIENTS];

    private int hostClientId = -1;

    public ConnectionManager(NetDriver netDriver) {
        this.netDriver = netDriver;
    }

    public int addConnection(Connection connection) {
        for (int i = 0; i < MAX_CLIENTS; ++i) {
            if (connectionSlots[i] == null) {
                ConnectionManager.ConnectionSlot connectionSlot = Pools.obtain(ConnectionManager.ConnectionSlot.class);
                connectionSlot.connection = connection;
                connectionSlot.state = ConnectionManager.ConnectionState.Pending;
                connectionSlots[i] = connectionSlot;

                netDriver.getTransport().addTransportConnection(i);

                return i;
            }
        }
        Log.info("Reached maximum number of connected clients");
        return -1;
    }

    public void removeConnection(int clientId) {
        // TODO: clean disconnect
        Pools.free(connectionSlots[clientId]);
    }

    public void acceptConnection(int clientId) {
        connectionSlots[clientId].state = ConnectionState.Connected;
    }

    public void acceptHostConnection(int clientId) {
        acceptConnection(clientId);
        hostClientId = clientId;
    }

    public int getHostClientId() {
        return hostClientId;
    }

    public ConnectionSlot getHostConnectionSlot() {
        return connectionSlots[hostClientId];
    }

    public ConnectionManager.ConnectionSlot getConnectionSlot(int clientId) {
        return connectionSlots[clientId];
    }

    public int getClientId(Connection connection) {
        for (int i = 0; i < MAX_CLIENTS; ++i) {
            if (connectionSlots[i].connection == connection) return i;
        }
        return -1;
    }


    public static class ConnectionSlot implements Pool.Poolable {
        Connection connection = null;
        int playerId = -1;
        ConnectionState state = ConnectionState.Disconnected;

        int messageSeqCounter = 0;

        public boolean needsSnapshot = true;

        public Connection getConnection() {
            return connection;
        }

        public int getPlayerId() {
            return playerId;
        }

        @Override
        public void reset() {
            connection = null;
            playerId = -1;
            state = ConnectionState.Disconnected;
            needsSnapshot = true;
            messageSeqCounter = 0;
        }
    }

    public enum ConnectionState {
        Pending, Connected, Disconnected,
    }
}
