package com.xam.bobgame.net;

import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.utils.Pool;
import com.badlogic.gdx.utils.Pools;
import com.esotericsoftware.kryonet.Connection;
import com.esotericsoftware.minlog.Log;
import com.xam.bobgame.GameEngine;
import com.xam.bobgame.events.ClientConnectedEvent;
import com.xam.bobgame.events.ClientDisconnectedEvent;
import com.xam.bobgame.events.EventsSystem;
import com.xam.bobgame.events.PlayerControlEvent;
import com.xam.bobgame.game.RefereeSystem;
import com.xam.bobgame.utils.Bits2;
import com.xam.bobgame.utils.SequenceNumChecker;

public class ConnectionManager {

    private NetDriver netDriver;

    private final ConnectionSlot[] connectionSlots = new ConnectionSlot[NetDriver.MAX_CLIENTS];

    private Bits2 activeConnectionsMask = new Bits2(NetDriver.MAX_CLIENTS);

    public ConnectionManager(NetDriver netDriver) {
        this.netDriver = netDriver;
    }

    public boolean hasConnections() {
        for (int i = 0; i < connectionSlots.length; ++i) {
            if (connectionSlots[i] != null) return true;
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
                slot.reset();
                connectionSlots[i] = null;
            }
        }
    }

    public int addConnection(Connection connection, boolean isServer) {
        synchronized (connectionSlots) {
            for (int i = 0; i < NetDriver.MAX_CLIENTS; ++i) {
                if (connectionSlots[i] == null) {
                    ConnectionManager.ConnectionSlot connectionSlot = Pools.obtain(ConnectionManager.ConnectionSlot.class);
                    connectionSlot.initialize(netDriver);
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
            connectionSlot.state.start(connectionSlot);
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
        for (int i = 0; i < connectionSlots.length; ++i) {
            ConnectionSlot connectionSlot = connectionSlots[i];
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
        Pools.free(connectionSlots[clientId]);
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

    public void acceptConnection(int clientId) {
        connectionSlots[clientId].state = ConnectionState.ServerConnected;
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

    public int getClientId(Connection connection) {
        for (int i = 0; i < NetDriver.MAX_CLIENTS; ++i) {
            if (connectionSlots[i] != null && connectionSlots[i].connection == connection) return i;
        }
        return -1;
    }

    public Bits2 getActiveConnectionsMask() {
        return activeConnectionsMask;
    }

    public static class ConnectionSlot implements Pool.Poolable {
        NetDriver netDriver = null;
        Connection connection = null;
        String hostAddress = null;
        String originalHostAddress = null;
        int clientId = -1;
        int playerId = -1;

        boolean hasUDP = false;

        ConnectionState state = null;
        float t = 0;
        int salt = 0;

        Packet sendPacket = new Packet(NetDriver.DATA_MAX_SIZE);
        Packet syncPacket = new Packet(NetDriver.DATA_MAX_SIZE);

        PacketBuffer packetBuffer;
        MessageBuffer messageBuffer;

        public boolean needsSnapshot = true;
        public float timeSinceLastSnapshot = NetDriver.SNAPSHOT_INTERVAL;
        Message message = new Message(NetDriver.DATA_MAX_SIZE);

        final SequenceNumChecker messageNumChecker = new SequenceNumChecker(256);

        public void initialize(NetDriver netDriver) {
            this.netDriver = netDriver;
            packetBuffer = new PacketBuffer(netDriver, 16);
            messageBuffer = new MessageBuffer(16);
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
            t = 0;
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

        @Override
        public void reset() {
            netDriver = null;
            connection = null;
            hasUDP = false;
            clientId = -1;
            playerId = -1;
            state = null;
            t = 0;
            salt = 0;
            needsSnapshot = true;
            timeSinceLastSnapshot = NetDriver.SNAPSHOT_INTERVAL;
            packetBuffer.reset();
            messageNumChecker.clear();
            hostAddress = null;
            originalHostAddress = null;
        }
    }

    public enum ConnectionState {
        ServerEmpty(-1, false) {
            @Override
            int read(ConnectionSlot slot, Packet in) {
                if (in.type == Packet.PacketType.ConnectionRequest) {
                    slot.transitionState(ServerPending);
                    slot.sendPacket.type = Packet.PacketType.ConnectionChallenge;
                    slot.generateSalt();
                    slot.sendTransportPacket(slot.sendPacket);
                    slot.sendPacket.clear();
                }
                else if (in.type == Packet.PacketType.Reconnect) {
                    ConnectionSlot oldSlot = slot.netDriver.getConnectionManager().findSalt(in.salt);
                    if (oldSlot == null) {
                        Log.debug("Invalid reconnect request was ignored");
                        return 0;
                    }
                    else {
                        oldSlot.connection = slot.connection;
                        if (!oldSlot.hostAddress.equals(slot.hostAddress)) {
                            Log.warn("Client " + slot.clientId + " reconnecting with a new host address");
                        }
                        oldSlot.messageNumChecker.set(slot.messageNumChecker);
                        PacketBuffer pb = oldSlot.packetBuffer;
                        oldSlot.packetBuffer = slot.packetBuffer;
                        slot.packetBuffer = pb;
                        slot.netDriver.transport.reconnect(oldSlot.clientId, slot.clientId);
                        slot.netDriver.getConnectionManager().removeConnection(slot.clientId);
                        oldSlot.state.read(oldSlot, in);
                    }
                }
                return 0;
            }

            @Override
            int timeout(ConnectionSlot slot) {
                return -1;
            }
        },
        ServerPending(5, false) {
            @Override
            int read(ConnectionSlot slot, Packet in) {
                if (in.type == Packet.PacketType.ConnectionChallengeResponse) {
                    if (in.salt >> 15 == slot.salt) {
                        slot.salt = in.salt;
                        Log.info("Client " + slot.clientId  + " (" + slot.getAddress() + ") connected");
                        ClientConnectedEvent event = Pools.obtain(ClientConnectedEvent.class);
                        event.clientId = slot.clientId;
                        slot.netDriver.getEngine().getSystem(EventsSystem.class).queueEvent(event);
                        slot.needsSnapshot = true;
                        slot.transitionState(ServerConnected);
                    }
                    else {
                        Log.debug("Client " + slot.clientId + " sent incorrect challenge response");
                        slot.netDriver.getConnectionManager().removeConnection(slot.clientId);
                    }
                }
                return 0;
            }

            @Override
            int timeout(ConnectionSlot slot) {
                slot.transitionState(ServerEmpty);
                return -1;
            }
        },
        ServerConnected(5, true) {
            @Override
            int readMessage(ConnectionSlot slot, Message message) {
                slot.netDriver.messageReader.deserialize(message, slot.netDriver.getEngine(), slot.clientId);
                return 0;
            }

            @Override
            int read(ConnectionSlot slot, Packet in) {
                if (in.type == Packet.PacketType.Disconnect) {
                    Log.info("Client " + slot.clientId + " (" + slot.getAddress() + ") disconnected");
                    ClientDisconnectedEvent event = Pools.obtain(ClientDisconnectedEvent.class);
                    event.clientId = slot.clientId;
                    event.playerId = slot.playerId;
                    event.cleanDisconnect = true;
                    slot.netDriver.getEngine().getSystem(EventsSystem.class).queueEvent(event);
                    slot.netDriver.connectionManager.removeConnection(slot.clientId);
                }
                else if (in.type == Packet.PacketType.Reconnect) {
                    Log.info("Client " + slot.clientId + " (" + slot.getAddress() + ") reconnected");
                    slot.needsSnapshot = true;
                    slot.netDriver.getEngine().getSystem(RefereeSystem.class).assignPlayer(slot.clientId, slot.playerId);
                }
                return -1;
            }

            @Override
            int timeout(ConnectionSlot slot) {
                Log.info("Client " + slot.clientId + " didn't respond for " + this.timeoutThreshold + " seconds");
                slot.transitionState(ServerTimeoutPending);
                return -1;
            }

            @Override
            int update(ConnectionSlot slot, float t) {
                slot.timeSinceLastSnapshot += t;
                return super.update(slot, t);
            }

            @Override
            int update2(ConnectionSlot slot) {
                slot.netDriver.server.syncClient(slot);
                return 0;
            }
        },
        ServerTimeoutPending(NetDriver.INACTIVITY_DISCONNECT_TIMEOUT, true) {
            @Override
            int readMessage(ConnectionSlot slot, Message message) {
                Log.info("Client " + slot.clientId + " is active");
                slot.transitionState(ServerConnected);
                ServerConnected.readMessage(slot, message);
                return 0;
            }

            @Override
            int read(ConnectionSlot slot, Packet in) {
                Log.info("Client " + slot.clientId + " is active");
                slot.transitionState(ServerConnected);
                slot.state.read(slot, in);
                return 0;
            }

            @Override
            int timeout(ConnectionSlot slot) {
                Log.info("Client " + slot.clientId + " disconnected due to inactivity");
                ClientDisconnectedEvent event = Pools.obtain(ClientDisconnectedEvent.class);
                event.clientId = slot.clientId;
                event.playerId = slot.playerId;
                event.cleanDisconnect = false;
                slot.netDriver.getEngine().getSystem(EventsSystem.class).queueEvent(event);
                slot.netDriver.connectionManager.removeConnection(slot.clientId);
                return -1;
            }
        },
        ClientEmpty(-1, false) {
            @Override
            int start(ConnectionSlot slot) {
                slot.transitionState(ClientPending1);
                slot.sendPacket.type = Packet.PacketType.ConnectionRequest;
                slot.sendTransportPacket(slot.sendPacket);
                slot.sendPacket.clear();
                return 0;
            }

            @Override
            int read(ConnectionSlot slot, Packet in) {
                return 0;
            }

            @Override
            int timeout(ConnectionSlot slot) {
                return -1;
            }
        },
        ClientPending1(5, false) {
            @Override
            int read(ConnectionSlot slot, Packet in) {
                if (in.type == Packet.PacketType.ConnectionChallenge) {
                    slot.transitionState(ClientPending2);
                    slot.salt = in.salt;
                    slot.generateSalt();
                    slot.sendPacket.type = Packet.PacketType.ConnectionChallengeResponse;
                    slot.sendTransportPacket(slot.sendPacket);
                    slot.sendPacket.clear();
                }
                return 0;
            }

            @Override
            int timeout(ConnectionSlot slot) {
                slot.transitionState(ClientEmpty);
                return -1;
            }
        },
        ClientPending2(5, false) {
            @Override
            int readMessage(ConnectionSlot slot, Message message) {
                slot.transitionState(ClientConnected);
                Log.info("Connected to " + slot.getAddress());
                slot.netDriver.client.reconnectSalt = slot.salt;
                ClientConnected.readMessage(slot, message);
                slot.messageBuffer.syncFrameNum = message.frameNum - NetDriver.JITTER_BUFFER_SIZE;
                ClientConnectedEvent event = Pools.obtain(ClientConnectedEvent.class);
                event.clientId = slot.clientId;
                slot.netDriver.getEngine().getSystem(EventsSystem.class).queueEvent(event);
//                slot.packetBuffer.frameOffset = message.frameNum - ((GameEngine) slot.netDriver.getEngine()).getCurrentFrame() - NetDriver.JITTER_BUFFER_SIZE;
                return 0;
            }

            @Override
            int read(ConnectionSlot slot, Packet in) {
                return 0;
            }

            @Override
            int timeout(ConnectionSlot slot) {
                slot.transitionState(ClientEmpty);
                return -1;
            }
        },
        ClientConnected(0.5f, true) {
            @Override
            int readMessage(ConnectionSlot slot, Message message) {
                slot.netDriver.messageReader.deserialize(message, slot.netDriver.getEngine(), slot.clientId);
                return 0;
            }

            @Override
            int read(ConnectionSlot slot, Packet in) {
                return 0;
            }

            @Override
            int timeout(ConnectionSlot slot) {
                GameEngine engine = (GameEngine) slot.netDriver.getEngine();
                engine.pauseGame();
                slot.transitionState(ClientTimeoutPending);
                return -1;
            }

            @Override
            int update(ConnectionSlot slot, float t) {
                slot.messageBuffer.syncFrameNum++;
                if (super.update(slot, t) == -1) return -1;

                // send events
                boolean sent = false;
                for (NetDriver.ClientEvent clientEvent : slot.netDriver.clientEvents) {
                    if (clientEvent.event instanceof PlayerControlEvent) {
                        slot.netDriver.messageReader.serializeInput(slot.sendPacket.getMessage(), slot.netDriver.getEngine(), (PlayerControlEvent) clientEvent.event);
                    }
                    else {
                        slot.netDriver.messageReader.serializeEvent(slot.sendPacket.getMessage(), slot.netDriver.getEngine(), clientEvent.event);
                    }
//                Log.info("Send event " + sendPacket.getMessage());
                    slot.sendDataPacket(slot.sendPacket);
                    slot.sendPacket.clear();
                    sent = true;
                }
                slot.netDriver.clientEvents.clear();

                // send heartbeat if needed
                if (!sent) {
                    slot.netDriver.messageReader.serialize(slot.sendPacket.getMessage(), slot.netDriver.getEngine(), Message.MessageType.Empty, slot);
                    slot.sendDataPacket(slot.sendPacket);
                    slot.sendPacket.clear();
                }
                return 0;
            }
        },
        ClientTimeoutPending(30, true) {
            @Override
            int readMessage(ConnectionSlot slot, Message message) {
                if (message.getType() == Message.MessageType.Snapshot) {
                    GameEngine engine = (GameEngine) slot.netDriver.getEngine();
                    engine.resumeGame();
                    slot.transitionState(ClientConnected);
                    ClientConnected.readMessage(slot, message);
                }
                else {
                    Log.debug("Waiting for snapshot");
                    slot.sendPacket.type = Packet.PacketType.Reconnect;
                    slot.sendTransportPacket(slot.sendPacket);
                    slot.sendPacket.clear();
                }
                return 0;
            }

            @Override
            int read(ConnectionSlot slot, Packet in) {
                ClientConnected.read(slot, in);
                slot.sendPacket.type = Packet.PacketType.Reconnect;
                slot.sendTransportPacket(slot.sendPacket);
                slot.sendPacket.clear();
                return 0;
            }

            @Override
            int timeout(ConnectionSlot slot) {
                slot.transitionState(ClientEmpty);
                return -1;
            }

            @Override
            int update(ConnectionSlot slot, float t) {
                if (super.update(slot, t) == -1) return -1;
                return 0;
            }
        },
        ;

        public final float timeoutThreshold;
        public final boolean checksSalt;

        ConnectionState(float timeoutThreshold, boolean checksSalt) {
            this.timeoutThreshold = timeoutThreshold;
            this.checksSalt = checksSalt;
        }

        int readMessage(ConnectionSlot slot, Message message) {
            return 0;
        }
        abstract int read(ConnectionSlot slot, Packet in);
        abstract int timeout(ConnectionSlot slot);
        int start(ConnectionSlot slot) {
            return 0;
        }
        int update(ConnectionSlot slot, float t) {
            slot.t += t;
            if (timeoutThreshold > 0 && slot.t > timeoutThreshold) return timeout(slot);
            while (slot.packetBuffer.get(slot.syncPacket)) {
                if (!slot.state.checksSalt || slot.syncPacket.salt == slot.salt) {
                    if (slot.syncPacket.type == Packet.PacketType.Data) {
                        if (slot.checkMessageNum(slot.syncPacket.getMessage().messageId)) {
                            // message already seen or old and assumed seen
                            Log.info("Discarded message num=" + slot.syncPacket.getMessage().messageId);
                        }
                        else {
                            slot.messageBuffer.receive(slot.syncPacket.getMessage());
                        }
                    }
                    else {
                        if (slot.state.read(slot, slot.syncPacket) == -1) return -1;
                    }
                }

//                Log.info("Queued packet " + slot.syncPacket);
                slot.syncPacket.clear();
                slot.t = 0;
            }

            while (slot.messageBuffer.get(slot.message)) {
//                Log.info("Reading message " + slot.message);
                slot.state.readMessage(slot, slot.message);
                slot.message.clear();
            }

            return 0;
        }
        int update2(ConnectionSlot slot) {
            return 0;
        }
    }
}
