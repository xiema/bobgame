package com.xam.bobgame.net;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Net;
import com.badlogic.gdx.net.Socket;
import com.badlogic.gdx.net.SocketHints;
import com.badlogic.gdx.utils.GdxRuntimeException;

import java.io.IOException;
import java.net.SocketException;

public class Client {
    private SocketHints hints = new SocketHints();
    private Socket socket;

    public Client() {
        hints.connectTimeout = 100;
    }

    public void connect() {
        try {
            socket = Gdx.net.newClientSocket(Net.Protocol.TCP, "0.0.0.0", 55192, hints);
        }
        catch (GdxRuntimeException e) {
            return;
        }
        String msg = "Hello!";
        try {
            socket.getOutputStream().write(msg.getBytes());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
