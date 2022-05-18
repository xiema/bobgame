package com.xam.bobgame.net;

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
    private Packet eventPacket = new Packet(NetDriver.DATA_MAX_SIZE);
    private Packet sendPacket = new Packet(NetDriver.DATA_MAX_SIZE);

    private int counter = 0;
    private boolean hasUpdatePacket = false;
    private boolean hasSnapshotPacket = false;

    public NetServer(NetDriver netDriver, Serialization serialization) {
        super(8192, 2048, serialization);
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

    public void syncClient(ConnectionManager.ConnectionSlot connectionSlot) {
        if (counter != netDriver.counter) {
            counter = netDriver.counter;
            updatePacket.clear();
            snapshotPacket.clear();
            hasUpdatePacket = false;
            hasSnapshotPacket = false;
        }

        if (connectionSlot.needsSnapshot && connectionSlot.timeSinceLastSnapshot >= NetDriver.SNAPSHOT_INTERVAL) {
            if (!hasSnapshotPacket) {
                netDriver.messageReader.serialize(snapshotPacket.getMessage(), netDriver.getEngine(), Message.MessageType.Snapshot, connectionSlot);
                hasSnapshotPacket = true;
            }
            connectionSlot.needsSnapshot = false;
            connectionSlot.timeSinceLastSnapshot = 0;
            snapshotPacket.copyTo(sendPacket);
//            Log.info("Send snapshot " + snapshotPacket.getMessage());
        }
        else {
            if (netDriver.counter % NetDriver.SERVER_UPDATE_FREQUENCY == 0) {
                if (!hasUpdatePacket) {
                    netDriver.messageReader.serialize(updatePacket.getMessage(), netDriver.getEngine(), Message.MessageType.Update, null);
                    hasUpdatePacket = true;
                }
                updatePacket.copyTo(sendPacket);
            }

            for (int i = 0; i < netDriver.clientEvents.size; ++i) {
                NetDriver.ClientEvent clientEvent = netDriver.clientEvents.get(i);
                // TODO: pre-serialize message
                if (clientEvent.clientMask.get(connectionSlot.clientId)) {
                    netDriver.messageReader.serializeEvent(eventPacket.getMessage(), netDriver.getEngine(), clientEvent.event);
                    sendPacket.getMessage().append(eventPacket.getMessage());
                    eventPacket.clear();
                    clientEvent.clientMask.unset(connectionSlot.clientId);

                    if (!clientEvent.clientMask.anySet()) {
    //                    Log.info("Removing event " + clientEvent.event + " from queue");
                        netDriver.clientEvents.removeIndex(i);
                        i--;
                    }
                }
            }
        }

        if (sendPacket.getMessage().messageId != -1) {
            connectionSlot.sendDataPacket(sendPacket);
        }

        sendPacket.clear();
    }

    public void flagSnapshot(int clientId) {
        netDriver.connectionManager.getConnectionSlot(clientId).needsSnapshot = true;
    }

    public boolean isRunning() {
        return running;
    }
}
