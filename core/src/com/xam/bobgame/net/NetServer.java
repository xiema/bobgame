package com.xam.bobgame.net;

import com.badlogic.gdx.utils.Pools;
import com.esotericsoftware.kryonet.Connection;
import com.esotericsoftware.kryonet.Listener;
import com.esotericsoftware.kryonet.Serialization;
import com.esotericsoftware.kryonet.Server;
import com.esotericsoftware.minlog.Log;
import com.xam.bobgame.BoBGame;
import com.xam.bobgame.GameEngine;

import java.io.IOException;

public class NetServer extends Server {

    private NetDriver netDriver;
    private boolean running = false;

    private Packet updatePacket = new Packet(NetDriver.DATA_MAX_SIZE);
    private Packet snapshotPacket = new Packet(NetDriver.DATA_MAX_SIZE);
//    private Packet eventPacket = new Packet(NetDriver.DATA_MAX_SIZE);
    private Packet sendPacket = new Packet(NetDriver.DATA_MAX_SIZE);

    private boolean hasUpdatePacket = false;
    private boolean hasSnapshotPacket = false;

    public NetServer(NetDriver netDriver, Serialization serialization) {
        super(8192, 8192, serialization);
        this.netDriver = netDriver;

        addListener(listener);
    }

    @Override
    public void start() {
        if (isRunning()) return;
        super.start();
        ((GameEngine) netDriver.getEngine()).setMode(GameEngine.Mode.Server);
        try {
            if (BoBGame.isNoUDP()) {
                bind(NetDriver.PORT_TCP);
                Log.info("Server listening on " + NetDriver.PORT_TCP);
            }
            else {
                bind(NetDriver.PORT_TCP, NetDriver.PORT_UDP);
                Log.info("Server listening on " + NetDriver.PORT_TCP + "/" + NetDriver.PORT_UDP);
            }
            ((GameEngine) netDriver.getEngine()).setupServer();
            running = true;
            return;
        } catch (IOException e) {
            stop();
            e.printStackTrace();
        }
        ((GameEngine) netDriver.getEngine()).setMode(GameEngine.Mode.None);
    }

    private Listener listener = new Listener() {
        @Override
        public void connected(Connection connection) {
            netDriver.connectionManager.addConnection(connection, true);
        }

        @Override
        public void received(Connection connection, Object o) {
            if (!(o instanceof Packet)) return;
            Packet packet = (Packet) o;
            ConnectionManager.ConnectionSlot slot = netDriver.connectionManager.getConnectionSlot(connection);
            slot.packetBuffer.receive(packet);
        }
    };

    @Override
    public void stop() {
        if (!running) return;
        super.stop();
        running = false;
        ((GameEngine) netDriver.getEngine()).restart();
    }

    private int lastSyncFrame = -1;

    public void syncClient(ConnectionManager.ConnectionSlot connectionSlot) {
        int currentFrame = ((GameEngine) netDriver.getEngine()).getCurrentFrame();

        if (lastSyncFrame != currentFrame) {
            lastSyncFrame = currentFrame;
            updatePacket.clear();
            snapshotPacket.clear();
            hasUpdatePacket = false;
            hasSnapshotPacket = false;
        }

        if (connectionSlot.needsSnapshot && (connectionSlot.lastSnapshotFrame == -1 || currentFrame - connectionSlot.lastSnapshotFrame >= NetDriver.SNAPSHOT_FRAME_INTERVAL)) {
            if (!hasSnapshotPacket) {
                if (snapshotPacket.createMessage() != null) {
                    netDriver.messageReader.serialize(snapshotPacket.getMessage(0), Message.MessageType.Snapshot);
                    hasSnapshotPacket = true;
                }
                else {
                    Log.warn("NetServer", "Failed to send Snapshot Message");
                }
            }
            if (snapshotPacket.messageCount > 0) {
                connectionSlot.needsSnapshot = false;
                connectionSlot.lastSnapshotFrame = currentFrame;
                sendPacket.addMessage(snapshotPacket.getMessage(0));
    //            Log.info("Send snapshot " + snapshotPacket.getMessage());
            }
        }
        else {
            if (((GameEngine) netDriver.getEngine()).getCurrentFrame() % NetDriver.SERVER_UPDATE_FREQUENCY == 0) {
                if (!hasUpdatePacket) {
                    if (updatePacket.createMessage() != null) {
                        netDriver.messageReader.serialize(updatePacket.getMessage(0), Message.MessageType.Update);
                        hasUpdatePacket = true;
                    }
                    else {
                        Log.warn("NetServer", "Failed to send Update Message");
                    }
                }
                if (updatePacket.messageCount > 0) {
                    sendPacket.addMessage(updatePacket.getMessage(0));
                }
            }

            for (int i = 0; i < netDriver.clientEvents.size; ++i) {
                NetDriver.ClientEvent clientEvent = netDriver.clientEvents.get(i);
                if (clientEvent.clientMask.get(connectionSlot.clientId)) {
                    if (clientEvent.serializedMessage.messageId == -1) {
                        netDriver.messageReader.serializeEvent(clientEvent.serializedMessage, clientEvent.event);
                    }

                    if (sendPacket.addMessage(clientEvent.serializedMessage)) {
    //                    eventPacket.clear();
                        clientEvent.clientMask.unset(connectionSlot.clientId);
                        if (!clientEvent.clientMask.anySet()) {
        //                    Log.info("Removing event " + clientEvent.event + " from queue");
                            Pools.free(clientEvent);
                            netDriver.clientEvents.removeIndex(i);
                            i--;
                        }
                    }
                    else {
                        Log.warn("NetServer", "Failed to send Event Message: " + clientEvent.event);
                    }
                }
            }
        }

        if (sendPacket.getMessageCount() > 0) {
            connectionSlot.sendDataPacket(sendPacket);
        }

        sendPacket.clear();
    }

    public boolean isRunning() {
        return running;
    }
}
