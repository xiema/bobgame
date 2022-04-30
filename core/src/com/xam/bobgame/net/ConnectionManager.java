package com.xam.bobgame.net;

import com.badlogic.gdx.utils.Pool;
import com.badlogic.gdx.utils.Pools;
import com.esotericsoftware.kryonet.Connection;
import com.esotericsoftware.minlog.Log;
import com.xam.bobgame.events.ClientConnectedEvent;
import com.xam.bobgame.events.EventsSystem;
import com.xam.bobgame.utils.SequenceNumChecker;

public class ConnectionManager {
    public static final int MAX_CLIENTS = 32;

    private NetDriver netDriver;

    private ConnectionSlot[] connectionSlots = new ConnectionSlot[MAX_CLIENTS];

    private int hostClientId = -1;

    public ConnectionManager(NetDriver netDriver) {
        this.netDriver = netDriver;
    }

    public void update(float deltaTime) {
        for (int i = 0; i < MAX_CLIENTS; ++i) {
            ConnectionSlot connectionSlot = connectionSlots[i];
            if (connectionSlot != null) {
                connectionSlot.state.update(connectionSlot, deltaTime);
            }
        }
    }

    public void update2() {
        for (int i = 0; i < MAX_CLIENTS; ++i) {
            if (connectionSlots[i] != null) {
                connectionSlots[i].state.update2(connectionSlots[i]);
            }
        }
    }

    public void clear() {
        for (int i = 0; i < MAX_CLIENTS; ++i) {
            ConnectionSlot slot = connectionSlots[i];
            if (slot != null) {
                slot.reset();
                connectionSlots[i] = null;
            }
        }
    }

    public int addConnection(Connection connection) {
        for (int i = 0; i < MAX_CLIENTS; ++i) {
            if (connectionSlots[i] == null) {
                ConnectionManager.ConnectionSlot connectionSlot = Pools.obtain(ConnectionManager.ConnectionSlot.class);
                connectionSlot.initialize(netDriver);
                connectionSlot.clientId = i;
                connectionSlot.connection = connection;
                connectionSlot.state = netDriver.getMode() == NetDriver.Mode.Server ? ConnectionState.ServerEmpty : ConnectionState.ClientEmpty;
                connectionSlots[i] = connectionSlot;

                netDriver.getTransport().addTransportConnection(i);

                return i;
            }
        }
        Log.info("Reached maximum number of connected clients");
        return -1;
    }

    public void initiateHandshake(int clientId) {
        // TODO: check if in client mode
        ConnectionSlot connectionSlot = connectionSlots[clientId];
        connectionSlot.state.read(connectionSlot, null);
    }

    public void removeConnection(int clientId) {
        // TODO: clean disconnect
        Pools.free(connectionSlots[clientId]);
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
        return connectionSlots[getClientId(connection)];
    }

    public int getClientId(Connection connection) {
        for (int i = 0; i < MAX_CLIENTS; ++i) {
            if (connectionSlots[i].connection == connection) return i;
        }
        return -1;
    }


    public static class ConnectionSlot implements Pool.Poolable {
        NetDriver netDriver = null;
        Connection connection = null;
        int clientId = -1;
        int playerId = -1;

        ConnectionState state = null;
        float t = 0;

        int messageSeqCounter = 0;

        Packet sendPacket = new Packet(Net.DATA_MAX_SIZE);
        Packet syncPacket = new Packet(Net.DATA_MAX_SIZE);

        PacketBuffer packetBuffer;

        public boolean needsSnapshot = true;

        final SequenceNumChecker messageNumChecker = new SequenceNumChecker(128);

        public void initialize(NetDriver netDriver) {
            this.netDriver = netDriver;
            packetBuffer = new PacketBuffer(netDriver, 8);
        }

        public Connection getConnection() {
            return connection;
        }

        public int getPlayerId() {
            return playerId;
        }

        public void transitionState(ConnectionState newState) {
            Log.info("Connection " + clientId + " transition from " + state + " to " + newState);
            this.state = newState;
            t = 0;
        }

        public void sendDataPacket(Packet packet) {
            packet.type = Packet.PacketType.Data;
            packet.clientId = clientId;
            connection.sendUDP(packet);
        }

        public void sendTransportPacket(Packet packet) {
            packet.clientId = clientId;
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
                slot.t = 0;
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
            int read(ConnectionSlot slot, Packet in) {
                if (in.getMessage().getType() == Message.MessageType.Event) {
                    slot.netDriver.messageReader.readEvent(in.getMessage(), slot.netDriver.getEngine(), in.clientId);
                }
                else {
                    slot.netDriver.messageReader.deserialize(in.getMessage(), slot.netDriver.getEngine());
                }
//                return readData(slot, in);
                return 0;
            }

            @Override
            int timeout(ConnectionSlot slot) {
                slot.transitionState(ServerEmpty);
                return -1;
            }

            @Override
            int update2(ConnectionSlot slot) {
                slot.netDriver.syncClient(slot);
                return 0;
            }
        },
        ClientEmpty(-1) {
            @Override
            int read(ConnectionSlot slot, Packet in) {
                slot.transitionState(ClientPending1);
                slot.sendPacket.type = Packet.PacketType.ConnectionRequest;
                slot.sendTransportPacket(slot.sendPacket);
                slot.sendPacket.clear();
                return 0;
            }

            @Override
            int timeout(ConnectionSlot slot) {
                slot.t = 0;
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
            int read(ConnectionSlot slot, Packet in) {
                if (in.type == Packet.PacketType.Data) {
                    slot.transitionState(ClientConnected);
                    Log.info("Connected to " + slot.connection.getRemoteAddressUDP().getAddress().getHostAddress());
                    return ClientConnected.read(slot, in);
                }
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
            int read(ConnectionSlot slot, Packet in) {
                if (in.getMessage().getType() == Message.MessageType.Event) {
                    slot.netDriver.messageReader.readEvent(in.getMessage(), slot.netDriver.getEngine(), in.clientId);
                }
                else {
                    slot.netDriver.messageReader.deserialize(in.getMessage(), slot.netDriver.getEngine());
                }
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

                // send events
                boolean sent = false;
                for (NetDriver.ClientEvent clientEvent : slot.netDriver.clientEvents) {
                    slot.netDriver.messageReader.serializeEvent(slot.sendPacket.getMessage(), clientEvent.event);
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

        abstract int read(ConnectionSlot slot, Packet in);
        abstract int timeout(ConnectionSlot slot);
        int update(ConnectionSlot slot, float t) {
            slot.t += t;
            if (slot.t > timeoutThreshold) return timeout(slot);
            while (slot.packetBuffer.get(slot.syncPacket)) {
                synchronized (slot.messageNumChecker) {
                    if (slot.messageNumChecker.getAndSet(slot.syncPacket.getMessage().messageId)) {
                        // message already seen or old and assumed seen
                        Log.info("Discarded message num=" + slot.syncPacket.getMessage().messageId);
                        return 0;
                    }
                }
                slot.state.read(slot, slot.syncPacket);
                slot.syncPacket.clear();
                slot.t = 0;
            }
            return 0;
        }
        int update2(ConnectionSlot slot) {
            return 0;
        }
    }
}
