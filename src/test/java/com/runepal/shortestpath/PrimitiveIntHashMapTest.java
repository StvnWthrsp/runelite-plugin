package com.runepal.shortestpath;

import org.junit.Assert;
import org.junit.Test;

public class PrimitiveIntHashMapTest {

    @Test
    public void retainsAllEntriesAfterRehash() {
        PrimitiveIntHashMap<Integer> map = new PrimitiveIntHashMap<>(8);
        int entryCount = 10000;

        for (int i = 0; i < entryCount; i++) {
            map.put(i, i * 2);
        }

        Assert.assertEquals(entryCount, map.size());

        for (int i = 0; i < entryCount; i++) {
            Assert.assertEquals(Integer.valueOf(i * 2), map.get(i));
        }
    }
}
