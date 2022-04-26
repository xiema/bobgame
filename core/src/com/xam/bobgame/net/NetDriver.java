package com.xam.bobgame.net;

import com.badlogic.ashley.core.Engine;
import com.badlogic.gdx.net.*;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.GdxRuntimeException;
import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryonet.Client;
import com.esotericsoftware.kryonet.Connection;
import com.esotericsoftware.kryonet.Listener;
import com.esotericsoftware.kryonet.Server;
import com.xam.bobgame.utils.DebugUtils;

import java.io.IOException;

public class NetDriver {
    public static final int PORT_TCP = 55192;
    public static final int PORT_UDP = 55196;
    private Mode mode = Mode.Client;

    private Server server;
    private Client client;

    private PacketBuffer packetBuffer = new PacketBuffer(8);
    private PacketSerializer packetSerializer = new PacketSerializer();
    private Packet syncBuffer = new Packet(Net.PACKET_MAX_SIZE);
    private Packet sendBuffer = new Packet(Net.PACKET_MAX_SIZE);

    private DebugUtils.ExpoMovingAverage movingAverage = new DebugUtils.ExpoMovingAverage(0.1f);
    private float packetBits = 0;
    private float bitrate = 0;

    public NetDriver() {

    }

    private void registerSerializers(Kryo kryo) {
        kryo.register(Packet.class, new NetSerializer(Net.PACKET_MAX_SIZE));
    }

    public void setMode(Mode mode) {
        this.mode = mode;
    }

    public boolean isConnected() {
        return client != null && client.isConnected();
    }

    public void connect(String host) {
        if (mode != Mode.Client || client != null) return;
        client = new Client();
        registerSerializers(client.getKryo());
        client.addListener(new Listener() {
            @Override
            public void disconnected(Connection connection) {
                client = null;
            }

            @Override
            public void received(Connection connection, Object o) {
                if (o instanceof Packet) {
                    packetBuffer.receive(((Packet) o).byteBuffer);
                }
            }
        });
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
        server = new Server();
        registerSerializers(server.getKryo());
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
        packetBits = packetSerializer.serialize(sendBuffer.byteBuffer, engine);
//        DebugUtils.log("Packet", DebugUtils.bytesHex(sendBuffer.array()));
        sendBuffer.byteBuffer.flip();
        DebugUtils.log("Packet", "length=" + sendBuffer.byteBuffer.limit());
        server.sendToAllUDP(sendBuffer);
        sendBuffer.byteBuffer.clear();
    }

    public void syncWithServer(Engine engine) {
        if (mode != Mode.Client) return;
        if (packetBuffer.get(syncBuffer.byteBuffer)) {
            syncBuffer.byteBuffer.flip();
            syncBuffer.byteBuffer.position(4);
            packetSerializer.syncEngine(syncBuffer.byteBuffer, engine);
        }
        syncBuffer.byteBuffer.clear();
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
