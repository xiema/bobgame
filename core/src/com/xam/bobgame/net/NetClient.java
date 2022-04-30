package com.xam.bobgame.net;

import com.esotericsoftware.kryonet.Client;
import com.esotericsoftware.kryonet.Connection;
import com.esotericsoftware.kryonet.Listener;
import com.esotericsoftware.kryonet.Serialization;
import com.esotericsoftware.minlog.Log;

public class NetClient extends Client {

    private final NetDriver netDriver;

    public NetClient(NetDriver netDriver, Serialization serialization) {
        super(8192, 2048, serialization);
        this.netDriver = netDriver;
        addListener(listener);
    }

    private Listener listener = new Listener() {
        @Override
        public void connected(Connection connection) {
            int clientId = netDriver.getConnectionManager().addConnection(connection);
//            netDriver.getConnectionManager().acceptHostConnection(clientId);
            netDriver.getConnectionManager().initiateHandshake(clientId);
        }

        @Override
        public void received(Connection connection, Object o) {
            if (!(o instanceof Packet)) return;
            Packet packet = (Packet) o;
            netDriver.getConnectionManager().getConnectionSlot(connection).packetBuffer.receive(packet);
//            if (packet.getType() == Packet.PacketType.Data) {
//                netDriver.getConnectionManager().getConnectionSlot(connection).packetBuffer.receive(packet);
//            }
//            else {
//                int clientId = netDriver.getConnectionManager().getClientId(connection);
//                ConnectionManager.ConnectionSlot connectionSlot = netDriver.getConnectionManager().getConnectionSlot(clientId);
//                connectionSlot.state.read(connectionSlot, packet);
//            }
        }
    };
}
