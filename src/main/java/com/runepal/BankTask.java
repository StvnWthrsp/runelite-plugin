package com.runepal;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameObject;
import net.runelite.api.ItemContainer;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.widgets.Widget;

import java.util.Map;

import javax.inject.Inject;

@Slf4j
public class BankTask implements BotTask {

    private enum BankState {
        FIND_BANK,
        OPENING_BANK,
        DEPOSITING,
        WAITING_FOR_DEPOSIT,
        FINISHED,
        INTERACTING_WITH_BANK,
        FAILED,
        WITHDRAWING,
        DEPOSITING_ALL_ITEMS
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

    private Map<Integer, Integer> itemsToWithdraw;

    public BankTask(RunepalPlugin plugin, Map<Integer, Integer> itemsToWithdraw, ActionService actionService, GameService gameService) {
        this.client = plugin.getClient();
        this.actionService = actionService;
        this.gameService = gameService;
        this.itemsToWithdraw = itemsToWithdraw;
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
                depositAllItems();
                break;
            case WITHDRAWING:
                doWithdrawing();
                break;
            case DEPOSITING_ALL_ITEMS:
                depositAllItems();
                break;
            case WAITING_FOR_DEPOSIT:
                waitForDeposit();
                break;
            default:
                break;
        }
    }

    private void doWithdrawing() {
        ItemContainer bankContainer = client.getItemContainer(InventoryID.BANK);
        if (bankContainer == null) {
            log.warn("Bank container not found. Trying again.");
            currentState = BankState.OPENING_BANK;
            return;
        }
        for (Map.Entry<Integer, Integer> entry : itemsToWithdraw.entrySet()) { 
            int itemId = entry.getKey();
            int quantity = entry.getValue();
            int itemIndex = bankContainer.find(itemId);
            if (itemIndex != -1) {
                actionService.sendClickRequest(gameService.getBankItemPoint(itemIndex), true);
            }
        }
    }

    private void findAndOpenBank() {
        GameObject bankBooth = gameService.findNearestGameObject(10583, 10355, 18491, 27291, 53015);
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

    private void depositAllItems() {
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