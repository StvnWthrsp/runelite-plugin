package com.runepal.banking;

import org.junit.Assert;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

public class BankPlanTest {
    @Test
    public void depositOnlyPlanHasNoWithdrawals() {
        BankPlan plan = BankPlan.depositOnly();

        Assert.assertTrue(plan.shouldDepositInventory());
        Assert.assertTrue(plan.getItemsToWithdraw().isEmpty());
    }

    @Test
    public void depositAndWithdrawPlanCopiesInputMap() {
        Map<Integer, Integer> input = new HashMap<>();
        input.put(100, 2);

        BankPlan plan = BankPlan.depositAndWithdraw(input);
        input.put(200, 5);

        Assert.assertEquals(1, plan.getItemsToWithdraw().size());
        Assert.assertEquals(Integer.valueOf(2), plan.getItemsToWithdraw().get(100));
    }
}
