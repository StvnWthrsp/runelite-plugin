package com.runepal.shortestpath;

import org.junit.Assert;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

public class TransportVarRequirementTest {
    @Test
    public void evaluatesBasicComparisonChecks() {
        Map<Integer, Integer> values = new HashMap<>();
        values.put(1, 10);

        Assert.assertTrue(new TransportVarbit(1, 10, TransportVarCheck.EQUAL).check(values));
        Assert.assertFalse(new TransportVarbit(1, 11, TransportVarCheck.EQUAL).check(values));

        Assert.assertTrue(new TransportVarPlayer(1, 9, TransportVarCheck.GREATER).check(values));
        Assert.assertFalse(new TransportVarPlayer(1, 10, TransportVarCheck.GREATER).check(values));

        Assert.assertTrue(new TransportVarPlayer(1, 11, TransportVarCheck.SMALLER).check(values));
        Assert.assertFalse(new TransportVarPlayer(1, 10, TransportVarCheck.SMALLER).check(values));
    }

    @Test
    public void evaluatesBitAndMissingValueChecks() {
        Map<Integer, Integer> values = new HashMap<>();
        values.put(2, 0b1010);

        Assert.assertTrue(new TransportVarbit(2, 0b0010, TransportVarCheck.BIT_SET).check(values));
        Assert.assertFalse(new TransportVarbit(2, 0b0100, TransportVarCheck.BIT_SET).check(values));
        Assert.assertFalse(new TransportVarbit(99, 1, TransportVarCheck.EQUAL).check(values));
    }
}
