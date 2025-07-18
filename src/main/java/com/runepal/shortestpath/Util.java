package com.runepal.shortestpath;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

public class Util {
    public static byte[] readAllBytes(InputStream in) throws IOException {
        ByteArrayOutputStream result = new ByteArrayOutputStream();
        byte[] buffer = new byte[1024];

        while (true) {
            int read = in.read(buffer, 0, buffer.length);

            if (read == -1) {
                return result.toByteArray();
            }

            result.write(buffer, 0, read);
        }
    }

    public static int[] concatenate(int[][] arrays) {
        int n = 0;
        for (int i = 0; i < arrays.length; i++) {
            n += (arrays[i] == null) ? 0 : arrays[i].length;
        }
        if (n == 0) {
            return null;
        }
        int[] array = new int[n];
        int k = 0;
        for (int i = 0; i < arrays.length; i++) {
            if (arrays[i] != null) {
                for (int j = 0; j < arrays[i].length; j++) {
                    array[k++] = arrays[i][j];
                }
            }
        }
        return array;
    }
}
