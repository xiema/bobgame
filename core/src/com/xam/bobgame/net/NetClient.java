package com.xam.bobgame.net;

import com.esotericsoftware.kryonet.Client;
import com.esotericsoftware.kryonet.Connection;
import com.esotericsoftware.kryonet.Listener;
import com.esotericsoftware.kryonet.Serialization;
import com.esotericsoftware.minlog.Log;

import java.io.IOException;

public class NetClient extends Client {

    private final NetDriver netDriver;

    public NetClient(NetDriver netDriver, Serialization serialization) {
        super(8192, 2048, serialization);
        this.netDriver = netDriver;
        addListener(listener);
    }

    public void connect(String host) {
        if (isConnected()) return;
        start();
        try {
            connect(5000, host, NetDriver.PORT_TCP, NetDriver.PORT_UDP);
            Log.info("Connected to " + host);
        } catch (IOException e) {
            stop();
            e.printStackTrace();
        }
    }

    @Override
    public void stop() {
        if (!isConnected()) return;
        super.stop();
        netDriver.connectionManager.clear();
    }

    private Listener listener = new Listener() {
        @Override
        public void connected(Connection connection) {
            int clientId = netDriver.connectionManager.addConnection(connection);
            netDriver.connectionManager.initiateHandshake(clientId);
        }

        @Override
        public void received(Connection connection, Object o) {
            if (!(o instanceof Packet)) return;
            Packet packet = (Packet) o;
            netDriver.connectionManager.getConnectionSlot(connection).packetBuffer.receive(packet);
        }
    };
}
