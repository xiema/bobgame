package com.xam.bobgame.utils;


import java.nio.ByteBuffer;

public class DebugUtils {

    public static String intHex(int i) {
        StringBuilder sb = new StringBuilder();
        ByteBuffer b = ByteBuffer.allocate(4);
        b.putInt(0, i);
        return bytesHex(b.array());
    }

    public static String bytesHex(byte[] bytes) {
        return bytesHex(bytes, 0, bytes.length);
    }

    public static String bytesHex(byte[] bytes, int i, int j) {
        StringBuilder sb = new StringBuilder();
        for (; i < j; ++i) {
            sb.append(Integer.toHexString(bytes[i] & 0xFF));
            sb.append(' ');
        }
        return sb.toString();
    }

    public static String bytesHex(ByteBuffer byteBuffer, int i, int j) {
        StringBuilder sb = new StringBuilder();
        for (; i < j; ++i) {
            sb.append(Integer.toHexString(byteBuffer.get(i) & 0xFF));
            sb.append(' ');
        }
        return sb.toString();
    }

    public static String bitString(long bits, int length) {
        StringBuilder sb = new StringBuilder();
        int i = 0;
        while (length > 0) {
            sb.append(bits & 1);
            bits >>>= 1;
            i++;
            length--;
            if ((i % 8) == 0) sb.append(' ');
        }
        return sb.toString();
    }
}
