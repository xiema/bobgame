package com.xam.bobgame.net;


import com.badlogic.gdx.math.MathUtils;
import com.esotericsoftware.minlog.Log;
import com.xam.bobgame.utils.DebugUtils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.util.zip.CRC32;

public class NetTest {
    @Test
    public void test1() {
        NetDriver serverDriver = new NetDriver();
        serverDriver.setMode(NetDriver.Mode.Server);
        NetDriver clientDriver = new NetDriver();

        CRC32 crc32 = new CRC32();

        int count = 10;
        Packet[] packets = new Packet[count];
        Packet.PacketBuilder pb = new Packet.PacketBuilder();
        Packet packet;
        for (int i = 0; i < count; ++i) {
            packet = new Packet(Net.DATA_MAX_SIZE);
            pb.setPacket(packet);
            crc32.reset();
            while (pb.hasRemaining()) {
                pb.packByte((byte) MathUtils.random(0, 0xFF));
            }
            pb.flush(true);
            pb.clear();
            packets[i] = packet;
            Log.info("Packet " + i + ": " + packet);
        }

        serverDriver.startServer();
        clientDriver.connect("127.0.0.1");

        packet = new Packet(Net.DATA_MAX_SIZE);
        for (int i = 0; i < count; ++i) {
            serverDriver.server.sendToAllUDP(packets[i]);
            while (!clientDriver.packetBuffer.get(packet)) {

            }
            Log.info("Check packet " + (i + 1));
            Assertions.assertTrue(packets[i].equals(packet));
            packet.clear();
        }
    }
}