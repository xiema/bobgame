package com.xam.bobgame.net;

import com.badlogic.gdx.utils.Pools;
import com.esotericsoftware.kryonet.Connection;
import com.esotericsoftware.kryonet.Listener;
import com.esotericsoftware.kryonet.Serialization;
import com.esotericsoftware.kryonet.Server;
import com.esotericsoftware.minlog.Log;
import com.xam.bobgame.events.ClientConnectedEvent;
import com.xam.bobgame.events.EventsSystem;

public class NetServer extends Server {

    private NetDriver netDriver;

    public NetServer(NetDriver netDriver, Serialization serialization) {
        super(8192, 2048, serialization);
        this.netDriver = netDriver;

        addListener(listener);
    }

    private Listener listener = new Listener() {
        @Override
        public void connected(Connection connection) {
            if (netDriver.getEngine() == null) return;
            EventsSystem eventsSystem = netDriver.getEngine().getSystem(EventsSystem.class);
            if (eventsSystem == null) return;

            int clientId = netDriver.getConnectionManager().addConnection(connection);

            ClientConnectedEvent event = Pools.obtain(ClientConnectedEvent.class);
            event.clientId = clientId;
            eventsSystem.queueEvent(event);
        }

        @Override
        public void received(Connection connection, Object o) {
            if (!(o instanceof Packet)) return;
            Packet packet = (Packet) o;
            if (packet.getType() == Packet.PacketType.Data) {
                Message message = packet.getMessage();

                synchronized (netDriver.messageNumChecker) {
                    if (netDriver.messageNumChecker.getAndSet(message.messageId)) {
                        // message already seen or old and assumed seen
                        Log.info("Discarded message num=" + message.messageId);
                        return;
                    }
                }
                netDriver.updateBuffer.receive(packet);
            }
            else {
                int clientId = netDriver.getConnectionManager().getClientId(connection);
                ConnectionManager.ConnectionSlot connectionSlot = netDriver.getConnectionManager().getConnectionSlot(clientId);
    //            connectionSlot.state = connectionSlot.state.read(packet);
            }
        }
    };

    public void acceptConnection(int clientId, int playerId) {
        ConnectionManager connectionManager = netDriver.getConnectionManager();
        connectionManager.getConnectionSlot(clientId).playerId = playerId;
        connectionManager.acceptConnection(clientId);
        Log.info("Player " + playerId + " connected in slot " + clientId);
    }

}
