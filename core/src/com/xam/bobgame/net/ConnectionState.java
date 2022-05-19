package com.xam.bobgame.net;

import com.badlogic.gdx.utils.Pools;
import com.esotericsoftware.minlog.Log;
import com.xam.bobgame.GameEngine;
import com.xam.bobgame.events.ClientConnectedEvent;
import com.xam.bobgame.events.ClientDisconnectedEvent;
import com.xam.bobgame.events.EventsSystem;
import com.xam.bobgame.events.PlayerControlEvent;
import com.xam.bobgame.game.RefereeSystem;

public enum ConnectionState {
    ServerEmpty(-1, false) {
        @Override
        int read(ConnectionManager.ConnectionSlot slot, Packet in) {
            if (in.type == Packet.PacketType.ConnectionRequest) {
                slot.transitionState(ServerPending);
            } else if (in.type == Packet.PacketType.Reconnect) {
                ConnectionManager.ConnectionSlot oldSlot = slot.netDriver.getConnectionManager().findSalt(in.salt);
                if (oldSlot == null) {
                    Log.debug("Invalid reconnect request was ignored");
                    return 0;
                } else {
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
        int timeout(ConnectionManager.ConnectionSlot slot) {
            return -1;
        }
    },
    ServerPending(5, false) {
        @Override
        int start(ConnectionManager.ConnectionSlot slot) {
            slot.sendPacket.type = Packet.PacketType.ConnectionChallenge;
            slot.generateSalt();
            slot.sendTransportPacket(slot.sendPacket);
            slot.sendPacket.clear();
            return 0;
        }

        @Override
        int read(ConnectionManager.ConnectionSlot slot, Packet in) {
            if (in.type == Packet.PacketType.ConnectionChallengeResponse) {
                if (in.salt >> 15 == slot.salt) {
                    slot.salt = in.salt;
                    slot.transitionState(ServerConnected);
                } else {
                    Log.debug("Client " + slot.clientId + " sent incorrect challenge response");
                    slot.netDriver.getConnectionManager().removeConnection(slot.clientId);
                }
            }
            return 0;
        }

        @Override
        int timeout(ConnectionManager.ConnectionSlot slot) {
            slot.transitionState(ServerEmpty);
            return -1;
        }
    },
    ServerConnected(5, true) {
        @Override
        int start(ConnectionManager.ConnectionSlot slot) {
            Log.info("Client " + slot.clientId + " (" + slot.getAddress() + ") connected");
            ClientConnectedEvent event = Pools.obtain(ClientConnectedEvent.class);
            event.clientId = slot.clientId;
            slot.netDriver.getEngine().getSystem(EventsSystem.class).queueEvent(event);
            slot.needsSnapshot = true;
            return 0;
        }

        @Override
        int readMessage(ConnectionManager.ConnectionSlot slot, Message message) {
            slot.netDriver.messageReader.deserialize(message, slot.netDriver.getEngine(), slot.clientId);
            return 0;
        }

        @Override
        int receiveData(ConnectionManager.ConnectionSlot slot, Packet in) {
            if (in.requestSnapshot) slot.needsSnapshot = true;
            return super.receiveData(slot, in);
        }

        @Override
        int read(ConnectionManager.ConnectionSlot slot, Packet in) {
            if (in.requestSnapshot) slot.needsSnapshot = true;
            switch (in.type) {
                case Disconnect:
                    Log.info("Client " + slot.clientId + " (" + slot.getAddress() + ") disconnected");
                    ClientDisconnectedEvent event = Pools.obtain(ClientDisconnectedEvent.class);
                    event.clientId = slot.clientId;
                    event.playerId = slot.playerId;
                    event.cleanDisconnect = true;
                    slot.netDriver.getEngine().getSystem(EventsSystem.class).queueEvent(event);
                    slot.netDriver.connectionManager.removeConnection(slot.clientId);
                    return 0;
                case Reconnect:
                    Log.info("Client " + slot.clientId + " (" + slot.getAddress() + ") reconnected");
                    slot.netDriver.getEngine().getSystem(RefereeSystem.class).assignPlayer(slot.clientId, slot.playerId);
            }
            return -1;
        }

        @Override
        int timeout(ConnectionManager.ConnectionSlot slot) {
            Log.info("Client " + slot.clientId + " didn't respond for " + this.timeoutThreshold + " seconds");
            slot.transitionState(ServerTimeoutPending);
            return -1;
        }

        @Override
        int update2(ConnectionManager.ConnectionSlot slot) {
            slot.netDriver.server.syncClient(slot);
            return 0;
        }
    },
    ServerTimeoutPending(NetDriver.INACTIVITY_DISCONNECT_TIMEOUT, true) {
        @Override
        int readMessage(ConnectionManager.ConnectionSlot slot, Message message) {
            Log.info("Client " + slot.clientId + " is active");
            slot.transitionState(ServerConnected);
            ServerConnected.readMessage(slot, message);
            return 0;
        }

        @Override
        int read(ConnectionManager.ConnectionSlot slot, Packet in) {
            Log.info("Client " + slot.clientId + " is active");
            slot.transitionState(ServerConnected);
            slot.state.read(slot, in);
            return 0;
        }

        @Override
        int timeout(ConnectionManager.ConnectionSlot slot) {
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
        int read(ConnectionManager.ConnectionSlot slot, Packet in) {
            return 0;
        }

        @Override
        int timeout(ConnectionManager.ConnectionSlot slot) {
            return -1;
        }
    },
    ClientPending1(5, false) {
        @Override
        int start(ConnectionManager.ConnectionSlot slot) {
            slot.sendPacket.type = Packet.PacketType.ConnectionRequest;
            slot.sendTransportPacket(slot.sendPacket);
            slot.sendPacket.clear();
            return 0;
        }

        @Override
        int read(ConnectionManager.ConnectionSlot slot, Packet in) {
            if (in.type == Packet.PacketType.ConnectionChallenge) {
                slot.salt = in.salt;
                slot.generateSalt();
                slot.transitionState(ClientPending2);
            }
            return 0;
        }

        @Override
        int timeout(ConnectionManager.ConnectionSlot slot) {
            slot.transitionState(ClientEmpty);
            return -1;
        }
    },
    ClientPending2(5, false) {
        @Override
        int start(ConnectionManager.ConnectionSlot slot) {
            slot.sendPacket.type = Packet.PacketType.ConnectionChallengeResponse;
            slot.sendTransportPacket(slot.sendPacket);
            slot.sendPacket.clear();
            return 0;
        }

        @Override
        int readMessage(ConnectionManager.ConnectionSlot slot, Message message) {
            slot.transitionState(ClientConnected);
            slot.messageBuffer.syncFrameNum = message.frameNum - NetDriver.JITTER_BUFFER_SIZE;
            ClientConnected.readMessage(slot, message);
//                slot.packetBuffer.frameOffset = message.frameNum - ((GameEngine) slot.netDriver.getEngine()).getCurrentFrame() - NetDriver.JITTER_BUFFER_SIZE;
            return 0;
        }

        @Override
        int read(ConnectionManager.ConnectionSlot slot, Packet in) {
            return 0;
        }

        @Override
        int timeout(ConnectionManager.ConnectionSlot slot) {
            slot.transitionState(ClientEmpty);
            return -1;
        }
    },
    ClientConnected(0.5f, true) {
        @Override
        int start(ConnectionManager.ConnectionSlot slot) {
            // TODO: Check behavior on reconnect
            Log.info("Connected to " + slot.getAddress());
            slot.netDriver.client.reconnectSalt = slot.salt;
            ClientConnectedEvent event = Pools.obtain(ClientConnectedEvent.class);
            event.clientId = slot.clientId;
            slot.netDriver.getEngine().getSystem(EventsSystem.class).queueEvent(event);
            return 0;
        }

        @Override
        int readMessage(ConnectionManager.ConnectionSlot slot, Message message) {
            if (message.getType() == Message.MessageType.Update && slot.lastSnapshotFrame == -1) {
                Log.info("Can't update while still waiting for snapshot");
                return -1;
            } else if (message.getType() == Message.MessageType.Snapshot) {
                slot.lastSnapshotFrame = message.frameNum;
            }
            slot.netDriver.messageReader.deserialize(message, slot.netDriver.getEngine(), slot.clientId);
            return 0;
        }

        @Override
        int read(ConnectionManager.ConnectionSlot slot, Packet in) {
            return 0;
        }

        @Override
        int timeout(ConnectionManager.ConnectionSlot slot) {
            slot.transitionState(ClientTimeoutPending);
            return -1;
        }

        @Override
        int update(ConnectionManager.ConnectionSlot slot, float t) {
            slot.messageBuffer.syncFrameNum++;
            if (super.update(slot, t) == -1) return -1;

            boolean sent = false;

            // send events
            for (NetDriver.ClientEvent clientEvent : slot.netDriver.clientEvents) {
                if (clientEvent.event instanceof PlayerControlEvent) {
                    slot.netDriver.messageReader.serializeInput(slot.sendPacket.getMessage(), slot.netDriver.getEngine(), (PlayerControlEvent) clientEvent.event);
                } else {
                    slot.netDriver.messageReader.serializeEvent(slot.sendPacket.getMessage(), slot.netDriver.getEngine(), clientEvent.event);
                }
//                Log.info("Send event " + sendPacket.getMessage());
                slot.sendPacket.requestSnapshot = slot.needsSnapshot;
                slot.needsSnapshot = false;
                slot.sendDataPacket(slot.sendPacket);
                slot.sendPacket.clear();
                sent = true;
            }
            slot.netDriver.clientEvents.clear();

            // send heartbeat if needed
            if (!sent) {
                slot.netDriver.messageReader.serialize(slot.sendPacket.getMessage(), slot.netDriver.getEngine(), Message.MessageType.Empty);
                slot.sendPacket.requestSnapshot = slot.needsSnapshot;
                slot.needsSnapshot = false;
                slot.sendDataPacket(slot.sendPacket);
                slot.sendPacket.clear();
            }
            return 0;
        }
    },
    ClientTimeoutPending(30, true) {
        @Override
        int start(ConnectionManager.ConnectionSlot slot) {
            GameEngine engine = (GameEngine) slot.netDriver.getEngine();
            engine.pauseSystems();
            return 0;
        }

        @Override
        int readMessage(ConnectionManager.ConnectionSlot slot, Message message) {
            if (message.getType() == Message.MessageType.Snapshot) {
                GameEngine engine = (GameEngine) slot.netDriver.getEngine();
                engine.resumeSystems();
                slot.transitionState(ClientConnected);
                ClientConnected.readMessage(slot, message);
            } else {
                Log.debug("Waiting for snapshot");
                slot.sendPacket.type = Packet.PacketType.Empty;
                slot.sendPacket.requestSnapshot = true;
                slot.sendTransportPacket(slot.sendPacket);
                slot.sendPacket.clear();
            }
            return 0;
        }

        @Override
        int read(ConnectionManager.ConnectionSlot slot, Packet in) {
            ClientConnected.read(slot, in);
            slot.sendPacket.type = Packet.PacketType.Reconnect;
            slot.sendTransportPacket(slot.sendPacket);
            slot.sendPacket.clear();
            return 0;
        }

        @Override
        int timeout(ConnectionManager.ConnectionSlot slot) {
            slot.transitionState(ClientEmpty);
            return -1;
        }

        @Override
        int update(ConnectionManager.ConnectionSlot slot, float t) {
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

    int readMessage(ConnectionManager.ConnectionSlot slot, Message message) {
        return 0;
    }

    int receiveData(ConnectionManager.ConnectionSlot slot, Packet in) {
        slot.messageBuffer.receive(in.getMessage());
        return 0;
    }

    abstract int read(ConnectionManager.ConnectionSlot slot, Packet in);

    abstract int timeout(ConnectionManager.ConnectionSlot slot);

    int start(ConnectionManager.ConnectionSlot slot) {
        return 0;
    }

    /**
     * Called during the NetDriver update, before other systems have updated.
     * @param slot The connection slot to update
     * @param t Delta time
     * @return An error code
     */
    int update(ConnectionManager.ConnectionSlot slot, float t) {
        slot.accumulator += t;
        if (timeoutThreshold > 0 && slot.accumulator > timeoutThreshold) return timeout(slot);
        while (slot.packetBuffer.get(slot.syncPacket)) {
            if (!slot.state.checksSalt || slot.syncPacket.salt == slot.salt) {
                if (slot.syncPacket.type == Packet.PacketType.Data) {
                    if (slot.checkMessageNum(slot.syncPacket.getMessage().messageId)) {
                        // message already seen or old and assumed seen
                        Log.info("Discarded message num=" + slot.syncPacket.getMessage().messageId);
                    } else {
                        slot.state.receiveData(slot, slot.syncPacket);
                    }
                } else {
                    if (slot.state.read(slot, slot.syncPacket) == -1) return -1;
                }
            }

//            Log.info("Queued packet " + slot.syncPacket);
            slot.syncPacket.clear();
            slot.accumulator = 0;
        }

        while (slot.messageBuffer.get(slot.message)) {
//            Log.info("Reading message " + slot.message);
            slot.state.readMessage(slot, slot.message);
            slot.message.clear();
        }

        return 0;
    }

    /**
     * Called at the end of the engine update cycle, after all systems have updated.
     * @param slot The connection slot to update
     * @return An error code
     */
    int update2(ConnectionManager.ConnectionSlot slot) {
        return 0;
    }
}
