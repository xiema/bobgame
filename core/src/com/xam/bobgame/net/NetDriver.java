package com.xam.bobgame.net;

import com.badlogic.ashley.core.Engine;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Net;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.net.ServerSocket;
import com.badlogic.gdx.net.ServerSocketHints;
import com.badlogic.gdx.net.Socket;
import com.badlogic.gdx.net.SocketHints;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.GdxRuntimeException;
import com.xam.bobgame.utils.DebugUtils;

import java.io.IOException;
import java.nio.ByteBuffer;

public class NetDriver {
    public static final int PORT = 55192;
    private Mode mode = Mode.Client;

    private static final ServerSocketHints hints = new ServerSocketHints();
    private static final SocketHints socketHints = new SocketHints();
    static {
        hints.acceptTimeout = 0;
        socketHints.connectTimeout = 1000;
    }

    private ServerSocket serverSocket;
    final Array<Socket> clientSockets = new Array<>();
    private Socket clientSocket;

    private PacketBuffer packetBuffer = new PacketBuffer(8);
    private PacketSerializer packetSerializer = new PacketSerializer();
    private ByteBuffer syncBuffer = ByteBuffer.allocate(Packet.PACKET_MAX_SIZE);
    private ByteBuffer sendBuffer = ByteBuffer.allocate(Packet.PACKET_MAX_SIZE);

    private Thread thread;

    private int counter = 0;

    public NetDriver() {

    }

    public void setMode(Mode mode) {
        this.mode = mode;
    }

    public boolean isConnected() {
        return clientSocket != null && clientSocket.isConnected();
    }

    public void connect(String host) {
        if (mode != Mode.Client || thread != null || clientSocket != null) return;
        try {
            clientSocket = Gdx.net.newClientSocket(Net.Protocol.TCP, host, PORT, socketHints);
        }
        catch (GdxRuntimeException e) {
            e.printStackTrace();
            return;
        }
        DebugUtils.log("Client", "Connected to server");
        thread = new ClientThread(packetBuffer, clientSocket);
        thread.start();
    }

    public void startServer() {
        if (mode != Mode.Server || thread != null || serverSocket != null) return;
        serverSocket = Gdx.net.newServerSocket(Net.Protocol.TCP, PORT, hints);
        thread = new ServerIncomingConnectionsThread(this, serverSocket);
        thread.start();
    }

    public void stop() {
        if (thread != null) {
            thread.interrupt();
            thread = null;
            if (mode == Mode.Server) {
                serverSocket.dispose();
                serverSocket = null;
            }
            else {
                clientSocket.dispose();
                clientSocket = null;
            }
        }
        packetBuffer.reset();
    }

    public void syncClients(Engine engine) {
//        if (counter++ % 2 != 0) return;
        if (clientSockets.size == 0) return;
        packetSerializer.serialize(sendBuffer, engine);
        sendBuffer.flip();
        synchronized (clientSockets) {
            for (Socket socket : clientSockets) {
                try {
                socket.getOutputStream().write(sendBuffer.array(), 0, sendBuffer.limit());
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        sendBuffer.clear();
    }

    public void syncWithServer(Engine engine) {
        if (mode != Mode.Client) return;
        if (packetBuffer.get(syncBuffer)) {
            syncBuffer.flip();
            syncBuffer.position(4);
            packetSerializer.syncEngine(syncBuffer, engine);
        }
        syncBuffer.clear();
    }

    private static class ServerIncomingConnectionsThread extends Thread {
        public ServerIncomingConnectionsThread (final NetDriver netDriver, final ServerSocket serverSocket) {
            super(new Runnable() {
                @Override
                public void run() {
                    DebugUtils.log("Server", "Accepting connections...");
                    while (true) {
                        Socket socket = null;
                        try {
                            socket = serverSocket.accept(null);
                        }
                        catch (GdxRuntimeException e) {
                            e.printStackTrace();
                        }
                        if (socket != null) {
                            synchronized (netDriver.clientSockets) {
                                netDriver.clientSockets.add(socket);
                            }
                        }
                    }
                }
            });
        }
    }

    private static class ClientThread extends Thread {
        public ClientThread (final PacketBuffer packetBuffer, final Socket socket) {
            super(new Runnable() {
                @Override
                public void run() {
                    while (true) {
                        packetBuffer.receive(socket.getInputStream());
                    }
                }
            });
        }
    }

    public enum Mode {
        Client, Server
    }
}
