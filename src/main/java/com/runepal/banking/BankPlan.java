package com.runepal.banking;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class BankPlan {
    private final boolean depositInventory;
    private final Map<Integer, Integer> itemsToWithdraw;

    private BankPlan(boolean depositInventory, Map<Integer, Integer> itemsToWithdraw) {
        this.depositInventory = depositInventory;
        this.itemsToWithdraw = itemsToWithdraw == null ? Collections.emptyMap() : new HashMap<>(itemsToWithdraw);
    }

    public static BankPlan depositOnly() {
        return new BankPlan(true, Collections.emptyMap());
    }

    public static BankPlan depositAndWithdraw(Map<Integer, Integer> itemsToWithdraw) {
        return new BankPlan(true, itemsToWithdraw);
    }

    public boolean shouldDepositInventory() {
        return depositInventory;
    }

    public Map<Integer, Integer> getItemsToWithdraw() {
        return Collections.unmodifiableMap(itemsToWithdraw);
    }
}
