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

        addListener(connectionListener);
    }

    private Listener connectionListener = new Listener() {
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
    };

    public void acceptConnection(int clientId, int playerId) {
        ConnectionManager connectionManager = netDriver.getConnectionManager();
        connectionManager.getConnectionSlot(clientId).playerId = playerId;
        connectionManager.acceptConnection(clientId);
        Log.info("Player " + playerId + " connected in slot " + clientId);
    }

}
