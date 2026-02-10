package com.runepal.banking;

import java.util.HashMap;
import java.util.Map;

public class BankingService {
    public BankPlan createDepositOnlyPlan() {
        return BankPlan.depositOnly();
    }

    public BankPlan createPlan(Map<Integer, Integer> itemsToWithdraw) {
        return BankPlan.depositAndWithdraw(itemsToWithdraw);
    }

    public BankPlan createSingleItemPlan(int itemId, int quantity) {
        Map<Integer, Integer> itemsToWithdraw = new HashMap<>();
        itemsToWithdraw.put(itemId, quantity);
        return BankPlan.depositAndWithdraw(itemsToWithdraw);
    }
}
