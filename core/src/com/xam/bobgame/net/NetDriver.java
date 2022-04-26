package com.xam.bobgame.net;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.EntitySystem;
import com.badlogic.gdx.utils.IntArray;
import com.esotericsoftware.kryonet.Client;
import com.esotericsoftware.kryonet.Connection;
import com.esotericsoftware.kryonet.Listener;
import com.esotericsoftware.kryonet.Server;
import com.esotericsoftware.minlog.Log;
import com.xam.bobgame.utils.DebugUtils;

import java.io.IOException;

public class NetDriver extends EntitySystem {
    public static final int PORT_TCP = 55192;
    public static final int PORT_UDP = 55196;
    private Mode mode = Mode.Client;

    Server server;
    Client client;
    private IntArray needsSnapshot = new IntArray(false, 4);

    PacketBuffer packetBuffer = new PacketBuffer(8);
    private PacketReader packetReader = new PacketReader();
    private NetSerialization serialization = new NetSerialization(packetBuffer);
    private Packet syncPacket = new Packet(Net.DATA_MAX_SIZE);
    private Packet sendPacket = new Packet(Net.DATA_MAX_SIZE);
    private Packet snapshotPacket = new Packet(Net.SNAPSHOT_MAX_SIZE);

    private DebugUtils.ExpoMovingAverage movingAverage = new DebugUtils.ExpoMovingAverage(0.1f);
    private float packetBits = 0;
    private float bitrate = 0;

    private int t = 0;

    public NetDriver() {
        this(0);
    }

    public NetDriver(int priority) {
        super(priority);
    }

    @Override
    public void update(float deltaTime) {
        if (mode == Mode.Client) {
            if (isConnected()) {
                syncWithServer();
            }
            else if (t++ % 100 == 0) {
                connect("127.0.0.1");
            }
        }
    }

    public float getAverageBitrate() {
        return movingAverage.getAverage();
    }

    public void setMode(Mode mode) {
        this.mode = mode;
    }

    public Mode getMode() {
        return mode;
    }

    public boolean isConnected() {
        return client != null && client.isConnected();
    }

    public void connect(String host) {
        if (mode != Mode.Client || client != null) return;
        client = new Client(8192, 2048, serialization);
        client.start();
        try {
            client.connect(5000, host, PORT_TCP, PORT_UDP);
        } catch (IOException e) {
            client.stop();
            client = null;
            e.printStackTrace();
        }
    }

    public void startServer() {
        if (mode != Mode.Server || server != null) return;
        server = new Server(8192, 2048, serialization);
        server.addListener(new Listener() {
            @Override
            public void connected(Connection connection) {
                needsSnapshot.add(connection.getID());
            }
        });
        server.start();
        try {
            server.bind(PORT_TCP, PORT_UDP);
        } catch (IOException e) {
            server.stop();
            server = null;
            e.printStackTrace();
        }
    }

    public void stop() {
        if (server != null) {
            server.stop();
            server = null;
        }
        if (client != null) {
            client.stop();
            client = null;
        }
        packetBuffer.reset();
    }

    public void syncClients(float deltaTime) {
        if (server.getConnections().length == 0) return;

        packetReader.serialize(sendPacket, getEngine(), 1);
        packetBits = 0;
//        DebugUtils.log("Packet", DebugUtils.bytesHex(sendBuffer.array()));
//        Log.info("Packet", "[" + sendPacket.getLength() + "] " + sendPacket);
        boolean snapshot = false;

        for (Connection connection : server.getConnections()) {
            if (needsSnapshot.contains(connection.getID())) {
                if (!snapshot) {
                    packetReader.serialize(snapshotPacket, getEngine(), 2);
                    snapshot = true;
                }
                needsSnapshot.removeValue(connection.getID());
//                Log.info("Snapshot: [" + snapshotPacket.getLength() + "] " + snapshotPacket);
                connection.sendUDP(snapshotPacket);
                packetBits += snapshotPacket.getLength() * 8;
            }
            else {
                connection.sendUDP(sendPacket);
                packetBits += sendPacket.getLength() * 8;
            }
        }
//        server.sendToAllUDP(sendPacket);

        bitrate = packetBits / deltaTime;
        movingAverage.update(bitrate);

        sendPacket.clear();
        snapshotPacket.clear();
    }

    public void syncWithServer() {
        if (mode != Mode.Client) return;
        if (packetBuffer.get(syncPacket)) {
            packetReader.syncEngine(syncPacket, getEngine());
        }
        syncPacket.clear();
    }

    public Server getServer() {
        return server;
    }

    public enum Mode {
        Client, Server
    }
}
