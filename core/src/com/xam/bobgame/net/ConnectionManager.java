package com.xam.bobgame.net;

import com.badlogic.gdx.utils.Pool;
import com.badlogic.gdx.utils.Pools;
import com.esotericsoftware.kryonet.Connection;
import com.esotericsoftware.minlog.Log;
import com.xam.bobgame.GameEngine;
import com.xam.bobgame.events.ClientConnectedEvent;
import com.xam.bobgame.events.EventsSystem;
import com.xam.bobgame.events.PlayerControlEvent;
import com.xam.bobgame.utils.Bits2;
import com.xam.bobgame.utils.SequenceNumChecker;

public class ConnectionManager {

    private NetDriver netDriver;

    private final ConnectionSlot[] connectionSlots = new ConnectionSlot[NetDriver.MAX_CLIENTS];

    private Bits2 activeConnectionsMask = new Bits2(NetDriver.MAX_CLIENTS);

    private int hostClientId = -1;

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

    public int addConnection(Connection connection) {
        synchronized (connectionSlots) {
            for (int i = 0; i < NetDriver.MAX_CLIENTS; ++i) {
                if (connectionSlots[i] == null) {
                    ConnectionManager.ConnectionSlot connectionSlot = Pools.obtain(ConnectionManager.ConnectionSlot.class);
                    connectionSlot.initialize(netDriver);
                    connectionSlot.clientId = i;
                    connectionSlot.connection = connection;
                    connectionSlot.hostAddress = connection.getRemoteAddressTCP().getAddress().getHostAddress();
                    connectionSlot.state = netDriver.getMode() == NetDriver.Mode.Server ? ConnectionState.ServerEmpty : ConnectionState.ClientEmpty;
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

    public void sendDisconnect(int clientId) {
        ConnectionSlot slot = connectionSlots[clientId];
        slot.sendPacket.type = Packet.PacketType.Disconnect;
        int count = 10;
        while (count-- > 0) {
            slot.sendTransportPacket(slot.sendPacket);
        }
        slot.sendPacket.clear();
        removeConnection(clientId);
    }

    public void removeConnection(int clientId) {
        Log.info("Disconnecting from " + connectionSlots[clientId].hostAddress + " (slot " + clientId + ")");
        Pools.free(connectionSlots[clientId]);
        connectionSlots[clientId] = null;
        activeConnectionsMask.unset(clientId);
        netDriver.transport.removeTransportConnection(clientId);
    }

    public void acceptConnection(int clientId) {
        connectionSlots[clientId].state = ConnectionState.ServerConnected;
    }

    public void acceptHostConnection(int clientId) {
        connectionSlots[clientId].state = ConnectionState.ClientConnected;
//        acceptConnection(clientId);
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
        int clientId = -1;
        int playerId = -1;

        ConnectionState state = null;
        float t = 0;

        int messageSeqCounter = 0;

        Packet sendPacket = new Packet(NetDriver.DATA_MAX_SIZE);
        Packet syncPacket = new Packet(NetDriver.DATA_MAX_SIZE);

        PacketBuffer packetBuffer;
        MessageBuffer messageBuffer;

        public boolean needsSnapshot = true;
        Message message = new Message(NetDriver.DATA_MAX_SIZE);

        final SequenceNumChecker messageNumChecker = new SequenceNumChecker(128);

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

        public void transitionState(ConnectionState newState) {
            Log.info("Connection " + clientId + " transition from " + state + " to " + newState);
            this.state = newState;
            t = 0;
        }

        public void sendDataPacket(Packet packet) {
            packet.type = Packet.PacketType.Data;
//            Log.info("Sending packet " + packet);
            connection.sendUDP(packet);
        }

        public void sendTransportPacket(Packet packet) {
            connection.sendUDP(packet);
        }

        @Override
        public void reset() {
            netDriver = null;
            connection = null;
            clientId = -1;
            playerId = -1;
            state = null;
            t = 0;
            needsSnapshot = true;
            messageSeqCounter = 0;
            packetBuffer.reset();
            syncPacket.clear();
            sendPacket.clear();
            messageNumChecker.clear();
        }
    }

    public enum ConnectionState {
        ServerEmpty(-1) {
            @Override
            int read(ConnectionSlot slot, Packet in) {
                if (in.type == Packet.PacketType.ConnectionRequest) {
                    slot.transitionState(ServerPending);
                    slot.sendPacket.type = Packet.PacketType.ConnectionChallenge;
                    slot.sendTransportPacket(slot.sendPacket);
                    slot.sendPacket.clear();
                }
                return 0;
            }

            @Override
            int timeout(ConnectionSlot slot) {
                return -1;
            }
        },
        ServerPending(5) {
            @Override
            int read(ConnectionSlot slot, Packet in) {
                if (in.type == Packet.PacketType.ConnectionChallengeResponse) {
                    ClientConnectedEvent event = Pools.obtain(ClientConnectedEvent.class);
                    event.clientId = slot.clientId;
                    slot.netDriver.getEngine().getSystem(EventsSystem.class).queueEvent(event);
                    slot.needsSnapshot = true;
                    slot.transitionState(ServerConnected);
                }
                return 0;
            }

            @Override
            int timeout(ConnectionSlot slot) {
                slot.transitionState(ServerEmpty);
                return -1;
            }
        },
        ServerConnected(5) {
            @Override
            int readMessage(ConnectionSlot slot, Message message) {
                slot.netDriver.messageReader.deserialize(message, slot.netDriver.getEngine(), slot.clientId);
                return 0;
            }

            @Override
            int read(ConnectionSlot slot, Packet in) {
                if (in.type == Packet.PacketType.Disconnect) {
                    slot.netDriver.connectionManager.removeConnection(slot.clientId);
                }
//                return readData(slot, in);
                return -1;
            }

            @Override
            int timeout(ConnectionSlot slot) {
                slot.transitionState(ServerEmpty);
                return -1;
            }

            @Override
            int update2(ConnectionSlot slot) {
                slot.netDriver.getServer().syncClient(slot);
                return 0;
            }
        },
        ClientEmpty(-1) {
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
        ClientPending1(5) {
            @Override
            int read(ConnectionSlot slot, Packet in) {
                if (in.type == Packet.PacketType.ConnectionChallenge) {
                    slot.transitionState(ClientPending2);
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
        ClientPending2(5) {
            @Override
            int readMessage(ConnectionSlot slot, Message message) {
                slot.transitionState(ClientConnected);
                Log.info("Connected to " + slot.connection.getRemoteAddressUDP().getAddress().getHostAddress());
                slot.netDriver.getClient().setHostId(slot.clientId);
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
        ClientConnected(5) {
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
                slot.transitionState(ClientEmpty);
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
        ;

        public final float timeoutThreshold;

        ConnectionState(float timeoutThreshold) {
            this.timeoutThreshold = timeoutThreshold;
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
                if (slot.syncPacket.type == Packet.PacketType.Data) {
                    synchronized (slot.messageNumChecker) {
                        if (slot.messageNumChecker.getAndSet(slot.syncPacket.getMessage().messageId)) {
                            // message already seen or old and assumed seen
                            Log.info("Discarded message num=" + slot.syncPacket.getMessage().messageId);
                            return 0;
                        }
                    }
                    slot.messageBuffer.receive(slot.syncPacket.getMessage());
                }
                else {
                    if (slot.state.read(slot, slot.syncPacket) == -1) return -1;
                }
                slot.syncPacket.clear();
                slot.t = 0;
            }

            while (slot.messageBuffer.get(slot.message)) {
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
