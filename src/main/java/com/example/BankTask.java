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

    private final RunepalPlugin plugin;
    private final Client client;
    private final ActionService actionService;
    private final GameService gameService;

    private BankState currentState = BankState.FIND_BANK;

    @Inject
    public BankTask(RunepalPlugin plugin, ActionService actionService, GameService gameService) {
        this.plugin = plugin;
        this.client = plugin.getClient();
        this.actionService = actionService;
        this.gameService = gameService;
    }

    @Override
    public void onStart() {
        log.info("Starting bank task.");
        this.currentState = BankState.FIND_BANK;
    }

    @Override
    public void onLoop() {
        switch (currentState) {
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
        GameObject bankBooth = gameService.findNearestGameObject(10583, 10355, 18491, 27291);
        if (bankBooth != null) {
            log.info("Found bank booth. Clicking it.");
            actionService.interactWithGameObject(bankBooth, "Bank");
            // actionService.sendClickRequest(gameService.getRandomClickablePoint(bankBooth), true);
            currentState = BankState.OPENING_BANK;
        } else {
            log.warn("No bank booth found. Cannot proceed with banking.");
            currentState = BankState.FAILED;
        }
    }

    private void waitForBankWidget() {
        Widget bankWidget = client.getWidget(InterfaceID.Bankmain.ITEMS_CONTAINER);
        if (bankWidget != null && !bankWidget.isHidden()) {
            log.info("Bank is open.");
            currentState = BankState.DEPOSITING;
        }
        // TODO: Add a timeout here in case the widget never opens.
    }

    private void depositItems() {
        Widget depositInventoryButton = client.getWidget(InterfaceID.Bankmain.DEPOSITINV);
        if (depositInventoryButton != null && !depositInventoryButton.isHidden()) {
            log.info("Depositing inventory.");
            actionService.sendClickRequest(gameService.getRandomPointInBounds(depositInventoryButton.getBounds()), true);
            currentState = BankState.WAITING_FOR_DEPOSIT;
        }
        // TODO: Wait for inventory to be empty.
    }

    private void waitForDeposit() {
        if (gameService.isInventoryEmpty()) {
            log.info("Inventory is empty. Banking complete.");
            currentState = BankState.FINISHED;
        }
        // TODO: Add a timeout here in case inventory never becomes empty
    }


    @Override
    public void onStop() {
        log.info("Stopping bank task.");
    }

    @Override
    public boolean isFinished() {
        return currentState == BankState.FINISHED || currentState == BankState.FAILED;
    }

    @Override
    public boolean isStarted() {
        if (currentState == null) {
            return false;
        }
        return true;
    }

    @Override
    public String getTaskName() {
        return "Banking";
    }
} 