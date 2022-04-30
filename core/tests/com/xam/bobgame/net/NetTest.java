package com.xam.bobgame.net;


import com.badlogic.gdx.math.MathUtils;
import com.esotericsoftware.minlog.Log;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.zip.CRC32;

public class NetTest {
    @Test
    public void test1() {
        NetDriver serverDriver = new NetDriver();
        serverDriver.setMode(NetDriver.Mode.Server);
        serverDriver.startServer();

        NetDriver clientDriver = new NetDriver();

        CRC32 crc32 = new CRC32();

        int count = 10;
        Packet[] packets = new Packet[count];
        Message.MessageBuilder pb = new Message.MessageBuilder();
        Message message;
        for (int i = 0; i < count; ++i) {
            packets[i] = new Packet(Net.DATA_MAX_SIZE);
            message = packets[i].getMessage();
            message.messageId = i;
            pb.setMessage(message);
            crc32.reset();
            while (pb.hasRemaining()) {
                pb.packByte((byte) MathUtils.random(0, 0xFF));
            }
            pb.flush(true);
            pb.clear();
            Log.info("Packet " + i + ": " + packets[i]);
        }

        clientDriver.connect("127.0.0.1");

//        Packet packet = new Packet(Net.DATA_MAX_SIZE);
//        for (int i = 0; i < count; ++i) {
//            serverDriver.server.sendToAllUDP(packets[i]);
//            while (!clientDriver.updateBuffer.get(packet)) {
//
//            }
//            Log.info("Check packet " + (i + 1));
//            Assertions.assertTrue(packets[i].equals(packet));
//            packet.clear();
//        }
    }
}