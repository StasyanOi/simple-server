package com.util;

public class Arrays {

    public static byte[] concatArrays(byte[] header, byte[] bytes) {
        byte[] total = new byte[header.length + bytes.length];
        System.arraycopy(header, 0, total, 0, header.length);
        System.arraycopy(bytes, 0, total, header.length, bytes.length);
        return total;
    }
}
