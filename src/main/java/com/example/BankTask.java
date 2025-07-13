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
        INTERACTING_WITH_BANK, FAILED
    }

    private final Client client;
    private final ActionService actionService;
    private final GameService gameService;

    private BankState currentState;
    private int idleTicks = 0;

    @Inject
    public BankTask(RunepalPlugin plugin, ActionService actionService, GameService gameService) {
        this.client = plugin.getClient();
        this.actionService = actionService;
        this.gameService = gameService;
    }
    
    private EventService eventService;

    public BankTask(RunepalPlugin plugin, ActionService actionService, GameService gameService, EventService eventService) {
        this.client = plugin.getClient();
        this.actionService = actionService;
        this.gameService = gameService;
        this.eventService = eventService;
    }

    @Override
    public void onStart() {
        log.info("Starting bank task.");
        this.currentState = BankState.FIND_BANK;
        if (eventService != null) {
            eventService.subscribe(InteractionCompletedEvent.class, this::onInteractionCompleted);
        }
    }

    @Override
    public void onLoop() {
        switch (currentState) {
            case FIND_BANK:
                findAndOpenBank();
                break;
            case INTERACTING_WITH_BANK:
                // State transition handled by InteractionCompletedEvent
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
            if (!actionService.isInteracting()) {
                actionService.interactWithGameObject(bankBooth, "Bank");
                currentState = BankState.INTERACTING_WITH_BANK;
            }
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
            idleTicks = 0;
        } else {
            idleTicks++;
        }
        if (idleTicks > 5) {
            log.info("Bank did not open, retrying.");
            idleTicks = 0;
            findAndOpenBank();
        }
    }

    private void depositItems() {
        if (gameService.isInventoryEmpty()) {
            log.info("Inventory is empty, skipping deposit.");
            currentState = BankState.FINISHED;
            return;
        }
        Widget depositInventoryButton = client.getWidget(InterfaceID.Bankmain.DEPOSITINV);
        if (depositInventoryButton != null && !depositInventoryButton.isHidden()) {
            log.info("Depositing inventory.");
            actionService.sendClickRequest(gameService.getRandomClickablePoint(depositInventoryButton), true);
            currentState = BankState.WAITING_FOR_DEPOSIT;
        }
    }

    private void waitForDeposit() {
        if (gameService.isInventoryEmpty()) {
            log.info("Inventory is empty. Banking complete.");
            currentState = BankState.FINISHED;
            idleTicks = 0;
        } else {
            idleTicks++;
        }
        if (idleTicks > 5) {
            log.info("Failed to empty inventory. Check for unbankable items.");
            idleTicks = 0;
            currentState = BankState.FAILED;
        }
    }


    @Override
    public void onStop() {
        log.info("Stopping bank task.");
        if (eventService != null) {
            eventService.unsubscribe(InteractionCompletedEvent.class, this::onInteractionCompleted);
        }
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

    private void onInteractionCompleted(InteractionCompletedEvent event) {
        if (currentState == BankState.INTERACTING_WITH_BANK) {
            if (event.isSuccess()) {
                log.info("Bank interaction completed successfully");
                currentState = BankState.OPENING_BANK;
            } else {
                log.warn("Bank interaction failed: {}", event.getFailureReason());
                currentState = BankState.FIND_BANK;
            }
        }
    }

    @Override
    public String getTaskName() {
        return "Banking";
    }
} 