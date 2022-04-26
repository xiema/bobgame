package com.xam.bobgame.net;

import com.badlogic.ashley.core.Engine;
import com.esotericsoftware.kryonet.Client;
import com.esotericsoftware.kryonet.Server;
import com.esotericsoftware.minlog.Log;
import com.xam.bobgame.utils.DebugUtils;

import java.io.IOException;

public class NetDriver {
    public static final int PORT_TCP = 55192;
    public static final int PORT_UDP = 55196;
    private Mode mode = Mode.Client;

    Server server;
    Client client;

    PacketBuffer packetBuffer = new PacketBuffer(8);
    private PacketSerializer packetSerializer = new PacketSerializer();
    private NetSerialization serialization = new NetSerialization(packetBuffer);
    private Packet syncPacket = new Packet(Net.DATA_MAX_SIZE);
    private Packet sendPacket = new Packet(Net.DATA_MAX_SIZE);

    private DebugUtils.ExpoMovingAverage movingAverage = new DebugUtils.ExpoMovingAverage(0.1f);
    private float packetBits = 0;
    private float bitrate = 0;

    public NetDriver() {

    }

    public void setMode(Mode mode) {
        this.mode = mode;
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

    public void syncClients(Engine engine) {
        if (server.getConnections().length == 0) return;
        packetBits = packetSerializer.serialize(sendPacket, engine);
//        DebugUtils.log("Packet", DebugUtils.bytesHex(sendBuffer.array()));
        Log.info("Packet", "[" + sendPacket.getLength() + "] " + sendPacket);
        server.sendToAllUDP(sendPacket);
        sendPacket.clear();
    }

    public void syncWithServer(Engine engine) {
        if (mode != Mode.Client) return;
        if (packetBuffer.get(syncPacket)) {
            packetSerializer.syncEngine(syncPacket, engine);
        }
        syncPacket.clear();
    }

    public float updateBitrate(float deltaTime) {
        bitrate = packetBits / deltaTime;
        return movingAverage.update(bitrate);
    }

    public Server getServer() {
        return server;
    }

    public enum Mode {
        Client, Server
    }
}
