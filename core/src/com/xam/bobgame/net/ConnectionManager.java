package com.xam.bobgame.net;

import com.badlogic.gdx.math.MathUtils;
import com.esotericsoftware.kryonet.Connection;
import com.esotericsoftware.minlog.Log;
import com.xam.bobgame.utils.Bits2;
import com.xam.bobgame.utils.SequenceNumChecker;

public class ConnectionManager {

    private final NetDriver netDriver;
    private final ConnectionSlot[] connectionSlots = new ConnectionSlot[NetDriver.MAX_CLIENTS];
    private final Bits2 activeConnectionsMask = new Bits2(NetDriver.MAX_CLIENTS);

    public ConnectionManager(NetDriver netDriver) {
        this.netDriver = netDriver;
    }

    public boolean hasConnections() {
        for (ConnectionSlot connectionSlot : connectionSlots) {
            if (connectionSlot != null) return true;
        }
        return false;
    }

    public void update(float deltaTime) {
        synchronized (connectionSlots) {
            for (int i = 0; i < NetDriver.MAX_CLIENTS; ++i) {
                ConnectionSlot connectionSlot = connectionSlots[i];
                if (connectionSlot != null) {
                    connectionSlot.state.update(connectionSlot, deltaTime);
                }
            }
        }
    }

    public void update2() {
        synchronized (connectionSlots) {
            for (int i = 0; i < NetDriver.MAX_CLIENTS; ++i) {
                if (connectionSlots[i] != null) {
                    connectionSlots[i].state.update2(connectionSlots[i]);
                }
            }
        }
    }

    public void clear() {
        for (int i = 0; i < NetDriver.MAX_CLIENTS; ++i) {
            ConnectionSlot slot = connectionSlots[i];
            if (slot != null) {
//                slot.reset();
                connectionSlots[i] = null;
            }
        }
    }

    public int addConnection(Connection connection, boolean isServer) {
        synchronized (connectionSlots) {
            for (int i = 0; i < NetDriver.MAX_CLIENTS; ++i) {
                if (connectionSlots[i] == null || connectionSlots[i].state == ConnectionState.ClientEmpty || connectionSlots[i].state == ConnectionState.ServerEmpty) {
//                    ConnectionManager.ConnectionSlot connectionSlot = Pools.obtain(ConnectionManager.ConnectionSlot.class);
                    ConnectionManager.ConnectionSlot connectionSlot = new ConnectionSlot();
                    connectionSlot.initialize(netDriver);
                    connectionSlot.messageBuffer.setFrameDelay(isServer ? 0 : NetDriver.JITTER_BUFFER_SIZE);
                    connectionSlot.clientId = i;
                    connectionSlot.connection = connection;
                    try {
                        connection.getRemoteAddressUDP();
                        connectionSlot.hasUDP = true;
                        Log.debug("ConnectionManager", "Connecting to client " + i + " by UDP");
                    } catch (NullPointerException e) {
                        Log.warn("ConnectionManager", "Connecting to client " + i + " by TCP");
                    }
                    connectionSlot.hostAddress = connection.getRemoteAddressTCP().getAddress().getHostAddress();
                    connectionSlot.state = isServer ? ConnectionState.ServerEmpty : ConnectionState.ClientEmpty;
                    connectionSlots[i] = connectionSlot;
                    activeConnectionsMask.set(i);

                    netDriver.transport.addTransportConnection(i);

                    return i;
                }
            }
        }
        Log.info("Reached maximum number of connected clients");
        return -1;
    }

    public void initiateHandshake(int clientId) {
        ConnectionSlot connectionSlot = connectionSlots[clientId];
        if (connectionSlot.state == ConnectionState.ClientEmpty) {
            connectionSlot.transitionState(ConnectionState.ClientPending1);
        }
    }

    public void initiateReconnect(int clientId, int salt) {
        ConnectionSlot connectionSlot = connectionSlots[clientId];
        if (connectionSlot.state == ConnectionState.ClientEmpty) {
            connectionSlot.state = ConnectionState.ClientPending2;
            connectionSlot.salt = salt;
            connectionSlot.sendPacket.type = Packet.PacketType.Reconnect;
            connectionSlot.sendTransportPacket(connectionSlot.sendPacket);
            connectionSlot.sendPacket.clear();
        }
    }

    public ConnectionSlot findSalt(int salt) {
        for (ConnectionSlot connectionSlot : connectionSlots) {
            if (connectionSlot == null) continue;
            if ((connectionSlot.state == ConnectionState.ServerConnected || connectionSlot.state == ConnectionState.ServerTimeoutPending) && connectionSlot.salt == salt)
                return connectionSlot;
        }
        return null;
    }

    public void sendDisconnect(int clientId) {
        ConnectionSlot slot = connectionSlots[clientId];
        slot.sendPacket.type = Packet.PacketType.Disconnect;
        int count = 10;
        while (count-- > 0) {
            slot.sendTransportPacket(slot.sendPacket);
        }
        slot.sendPacket.clear();
        Log.info("Disconnecting from " + connectionSlots[clientId].getAddress() + " (slot " + clientId + ")");
        removeConnection(clientId);
    }

    public void removeConnection(int clientId) {
//        Pools.free(connectionSlots[clientId]);
        connectionSlots[clientId] = null;
        activeConnectionsMask.unset(clientId);
        netDriver.transport.removeTransportConnection(clientId);
        synchronized (netDriver.clientEvents) {
            for (int i = 0; i < netDriver.clientEvents.size; ++i) {
                NetDriver.ClientEvent clientEvent = netDriver.clientEvents.get(i);
                clientEvent.clientMask.unset(clientId);
                if (!clientEvent.clientMask.anySet()) {
                    netDriver.clientEvents.removeIndex(i);
                    i--;
                }
            }
        }
    }

    public void setNeedsSnapshots() {
        for (ConnectionSlot slot : connectionSlots) {
            if (slot != null) {
                slot.needsSnapshot = true;
            }
        }
    }

    public void resetLastSnapshotFrames() {
        for (ConnectionSlot slot : connectionSlots) {
            if (slot != null) {
                slot.lastSnapshotFrame = -1;
            }
        }
    }

    public ConnectionManager.ConnectionSlot getConnectionSlot(int clientId) {
        return connectionSlots[clientId];
    }

    public ConnectionManager.ConnectionSlot getConnectionSlot(Connection connection) {
        int clientId = getClientId(connection);
        if (clientId == -1) return null;
        return connectionSlots[clientId];
    }

    public int getPlayerClientId(int playerId) {
        for (int i = 0; i < connectionSlots.length; ++i) {
            if (connectionSlots[i] != null && connectionSlots[i].playerId == playerId) return i;
        }
        return -1;
    }

    public void resetPlayerIds() {
        for (ConnectionSlot slot : connectionSlots) {
            if (slot != null) slot.playerId = -1;
        }
    }

    public int getClientId(Connection connection) {
        for (int i = 0; i < NetDriver.MAX_CLIENTS; ++i) {
            if (connectionSlots[i] != null && connectionSlots[i].connection == connection) return i;
        }
        return -1;
    }

    public Bits2 getActiveConnectionsMask() {
        return activeConnectionsMask;
    }

    public static class ConnectionSlot {
        NetDriver netDriver = null;
        Connection connection = null;
        String hostAddress = null;
        String originalHostAddress = null;
        int clientId = -1;
        int playerId = -1;

        boolean hasUDP = false;

        ConnectionState state = null;
        float accumulator = 0;
        int salt = 0;
        float lastReconnect = -1;

        Packet sendPacket = new Packet(NetDriver.DATA_MAX_SIZE);
        Packet syncPacket = new Packet(NetDriver.DATA_MAX_SIZE);

        PacketBuffer packetBuffer;
        MessageBuffer messageBuffer;

        public boolean needsSnapshot = true;
        int lastSnapshotFrame = -1;
        Message message = new Message(NetDriver.DATA_MAX_SIZE);

        final SequenceNumChecker messageNumChecker = new SequenceNumChecker(256);

        public void initialize(NetDriver netDriver) {
            this.netDriver = netDriver;
            packetBuffer = new PacketBuffer(netDriver, 16, NetDriver.BUFFER_TIME_LIMIT);
            messageBuffer = new MessageBuffer(16);
        }

        public boolean isConnected() {
            return state == ConnectionState.ServerConnected || state == ConnectionState.ClientConnected;
        }

        public Connection getConnection() {
            return connection;
        }

        public int getPlayerId() {
            return playerId;
        }

        public void setPlayerId(int playerId) {
            this.playerId = playerId;
        }

        public String getAddress() {
            return originalHostAddress != null ? originalHostAddress : hostAddress;
        }

        public String getHostAddress() {
            return hostAddress;
        }

        public int getSalt() {
            return salt;
        }

        public void transitionState(ConnectionState newState) {
            Log.debug("Connection " + clientId + " transition from " + state + " to " + newState);
            this.state = newState;
            accumulator = 0;
            newState.start(this);
        }

        public void sendDataPacket(Packet packet) {
            packet.type = Packet.PacketType.Data;
//            Log.info("Sending packet " + packet);
            if (hasUDP) {
                connection.sendUDP(packet);
            }
            else {
                connection.sendTCP(packet);
            }
        }

        public void sendTransportPacket(Packet packet) {
            if (hasUDP) {
                connection.sendUDP(packet);
            }
            else {
                connection.sendTCP(packet);
            }
        }

        int generateSalt() {
            return salt = (salt << 15) | MathUtils.random(1, 1 << 15);
        }

        synchronized public boolean checkMessageNum(int messageId) {
            return messageNumChecker.getAndSet(messageId);
        }

//        @Override
//        public void reset() {
//            netDriver = null;
//            connection = null;
//            hasUDP = false;
//            clientId = -1;
//            playerId = -1;
//            state = null;
//            t = 0;
//            salt = 0;
//            needsSnapshot = true;
//            timeSinceLastSnapshot = NetDriver.SNAPSHOT_INTERVAL;
//            packetBuffer.reset();
//            messageNumChecker.clear();
//            hostAddress = null;
//            originalHostAddress = null;
//        }
    }

}
