package com.example;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameObject;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;

import javax.inject.Inject;

@Slf4j
public class BankTask implements BotTask {

    private enum BankState {
        FIND_BANK,
        OPENING_BANK,
        DEPOSITING,
        WAITING_FOR_DEPOSIT,
        FINISHED,
        FAILED
    }

    private final AndromedaPlugin plugin;
    private final Client client;

    private BankState state = BankState.FIND_BANK;

    @Inject
    public BankTask(AndromedaPlugin plugin) {
        this.plugin = plugin;
        this.client = plugin.getClient();
    }

    @Override
    public void onStart() {
        log.info("Starting bank task.");
        this.state = BankState.FIND_BANK;
    }

    @Override
    public void onLoop() {
        switch (state) {
            case FIND_BANK:
                findAndOpenBank();
                break;
            case OPENING_BANK:
                waitForBankWidget();
                break;
            case DEPOSITING:
                depositItems();
                break;
            case WAITING_FOR_DEPOSIT:
                waitForDeposit();
                break;
            default:
                break;
        }
    }

    private void findAndOpenBank() {
        GameObject bankBooth = plugin.findNearestGameObject(10583, 10355);
        if (bankBooth != null) {
            log.info("Found bank booth. Clicking it.");
            plugin.sendClickRequest(plugin.getRandomClickablePoint(bankBooth), true);
            state = BankState.OPENING_BANK;
        } else {
            log.warn("No bank booth found. Cannot proceed with banking.");
            state = BankState.FAILED;
        }
    }

    private void waitForBankWidget() {
        Widget bankWidget = client.getWidget(InterfaceID.Bankmain.ITEMS_CONTAINER);
        if (bankWidget != null && !bankWidget.isHidden()) {
            log.info("Bank is open.");
            state = BankState.DEPOSITING;
        }
        // TODO: Add a timeout here in case the widget never opens.
    }

    private void depositItems() {
        Widget depositInventoryButton = client.getWidget(InterfaceID.Bankmain.DEPOSITINV);
        if (depositInventoryButton != null && !depositInventoryButton.isHidden()) {
            log.info("Depositing inventory.");
            plugin.sendClickRequest(plugin.getRandomPointInBounds(depositInventoryButton.getBounds()), true);
            state = BankState.WAITING_FOR_DEPOSIT;
        }
        // TODO: Wait for inventory to be empty.
    }

    private void waitForDeposit() {
        if (plugin.isInventoryEmpty()) {
            log.info("Inventory is empty. Banking complete.");
            state = BankState.FINISHED;
        }
        // TODO: Add a timeout here in case inventory never becomes empty
    }


    @Override
    public void onStop() {
        log.info("Stopping bank task.");
    }

    @Override
    public boolean isFinished() {
        return state == BankState.FINISHED || state == BankState.FAILED;
    }

    @Override
    public String getTaskName() {
        return "Banking";
    }
} 