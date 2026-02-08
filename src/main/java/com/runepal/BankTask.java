package com.runepal;

import com.runepal.runtime.SubscriptionBag;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameObject;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.widgets.Widget;

import javax.inject.Inject;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

@Slf4j
public class BankTask implements BotTask {

    private enum BankState {
        FIND_BANK,
        INTERACTING_WITH_BANK,
        OPENING_BANK,
        DEPOSITING,
        WAITING_FOR_DEPOSIT,
        WITHDRAWING,
        FINISHED,
        FAILED
    }

    private final Client client;
    private final ActionService actionService;
    private final GameService gameService;
    private final EventService eventService;
    private final Map<Integer, Integer> itemsToWithdraw;
    private SubscriptionBag subscriptionBag;

    private BankState currentState;
    private int idleTicks = 0;

    @Inject
    public BankTask(RunepalPlugin plugin, ActionService actionService, GameService gameService) {
        this(plugin, actionService, gameService, null, Collections.emptyMap());
    }

    public BankTask(RunepalPlugin plugin, ActionService actionService, GameService gameService, EventService eventService) {
        this(plugin, actionService, gameService, eventService, Collections.emptyMap());
    }

    public BankTask(RunepalPlugin plugin,
                    ActionService actionService,
                    GameService gameService,
                    EventService eventService,
                    Map<Integer, Integer> itemsToWithdraw) {
        this.client = Objects.requireNonNull(plugin, "plugin cannot be null").getClient();
        this.actionService = Objects.requireNonNull(actionService, "actionService cannot be null");
        this.gameService = Objects.requireNonNull(gameService, "gameService cannot be null");
        this.eventService = eventService;
        this.itemsToWithdraw = itemsToWithdraw == null ? Collections.emptyMap() : new HashMap<>(itemsToWithdraw);
    }

    public BankTask(RunepalPlugin plugin,
                    Map<Integer, Integer> itemsToWithdraw,
                    ActionService actionService,
                    GameService gameService) {
        this(plugin, actionService, gameService, null, itemsToWithdraw);
    }

    @Override
    public void onStart() {
        log.info("Starting bank task.");
        this.currentState = BankState.FIND_BANK;
        this.idleTicks = 0;
        if (eventService != null) {
            subscriptionBag = new SubscriptionBag(eventService);
            subscriptionBag.subscribe(InteractionCompletedEvent.class, this::onInteractionCompleted);
        }
    }

    @Override
    public void onLoop() {
        switch (currentState) {
            case FIND_BANK:
                findAndOpenBank();
                break;
            case INTERACTING_WITH_BANK:
                break;
            case OPENING_BANK:
                waitForBankWidget();
                break;
            case DEPOSITING:
                depositAllItems();
                break;
            case WAITING_FOR_DEPOSIT:
                waitForDeposit();
                break;
            case WITHDRAWING:
                doWithdrawing();
                break;
            default:
                break;
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
            return;
        }

        log.warn("No bank booth found. Cannot proceed with banking.");
        currentState = BankState.FAILED;
    }

    private void waitForBankWidget() {
        Widget bankWidget = client.getWidget(InterfaceID.Bankmain.ITEMS_CONTAINER);
        if (bankWidget != null && !bankWidget.isHidden()) {
            log.info("Bank is open.");
            currentState = BankState.DEPOSITING;
            idleTicks = 0;
            return;
        }

        idleTicks++;
        if (idleTicks > 5) {
            log.info("Bank did not open, retrying.");
            idleTicks = 0;
            findAndOpenBank();
        }
    }

    private void depositAllItems() {
        if (gameService.isInventoryEmpty()) {
            transitionAfterDeposit();
            return;
        }

        Widget depositInventoryButton = client.getWidget(InterfaceID.Bankmain.DEPOSITINV);
        if (depositInventoryButton != null && !depositInventoryButton.isHidden()) {
            log.info("Depositing inventory.");
            actionService.sendClickRequest(gameService.getRandomClickablePoint(depositInventoryButton), true);
            currentState = BankState.WAITING_FOR_DEPOSIT;
            idleTicks = 0;
            return;
        }

        idleTicks++;
        if (idleTicks > 5) {
            log.warn("Deposit button not available.");
            currentState = BankState.FAILED;
        }
    }

    private void waitForDeposit() {
        if (gameService.isInventoryEmpty()) {
            transitionAfterDeposit();
            idleTicks = 0;
            return;
        }

        idleTicks++;
        if (idleTicks > 5) {
            log.info("Failed to empty inventory. Check for unbankable items.");
            idleTicks = 0;
            currentState = BankState.FAILED;
        }
    }

    private void transitionAfterDeposit() {
        if (itemsToWithdraw.isEmpty()) {
            log.info("Inventory is empty. Banking complete.");
            currentState = BankState.FINISHED;
        } else {
            log.info("Inventory deposited, starting withdrawals: {}", itemsToWithdraw);
            currentState = BankState.WITHDRAWING;
        }
    }

    private void doWithdrawing() {
        if (itemsToWithdraw.isEmpty()) {
            currentState = BankState.FINISHED;
            return;
        }

        ItemContainer bankContainer = client.getItemContainer(InventoryID.BANK);
        if (bankContainer == null) {
            log.warn("Bank container not found. Trying again.");
            currentState = BankState.OPENING_BANK;
            return;
        }

        for (Map.Entry<Integer, Integer> entry : itemsToWithdraw.entrySet()) {
            int itemId = entry.getKey();
            int requiredQuantity = Math.max(entry.getValue(), 0);
            if (requiredQuantity <= 0) {
                continue;
            }

            int inventoryQuantity = getInventoryQuantity(itemId);
            if (inventoryQuantity >= requiredQuantity) {
                continue;
            }

            int itemIndex = bankContainer.find(itemId);
            if (itemIndex == -1) {
                log.warn("Unable to withdraw item {}. Item not found in bank.", itemId);
                currentState = BankState.FAILED;
                return;
            }

            actionService.sendClickRequest(gameService.getBankItemPoint(itemIndex), true);
            idleTicks++;
            if (idleTicks > 30) {
                log.warn("Withdrawal timed out for item {}.", itemId);
                currentState = BankState.FAILED;
            }
            return;
        }

        if (hasAllRequiredItems()) {
            log.info("All required items withdrawn.");
            currentState = BankState.FINISHED;
        } else {
            log.warn("Failed to withdraw all required items.");
            currentState = BankState.FAILED;
        }
    }

    private int getInventoryQuantity(int itemId) {
        ItemContainer inventory = client.getItemContainer(InventoryID.INV);
        if (inventory == null) {
            return 0;
        }

        int quantity = 0;
        for (Item item : inventory.getItems()) {
            if (item != null && item.getId() == itemId) {
                quantity += item.getQuantity();
            }
        }
        return quantity;
    }

    private boolean hasAllRequiredItems() {
        for (Map.Entry<Integer, Integer> entry : itemsToWithdraw.entrySet()) {
            int requiredQuantity = Math.max(entry.getValue(), 0);
            if (requiredQuantity == 0) {
                continue;
            }
            if (getInventoryQuantity(entry.getKey()) < requiredQuantity) {
                return false;
            }
        }
        return true;
    }

    @Override
    public void onStop() {
        log.info("Stopping bank task.");
        if (subscriptionBag != null) {
            subscriptionBag.clear();
            subscriptionBag = null;
        }
    }

    @Override
    public boolean isFinished() {
        return currentState == BankState.FINISHED || currentState == BankState.FAILED;
    }

    @Override
    public boolean isStarted() {
        return currentState != null;
    }

    private void onInteractionCompleted(InteractionCompletedEvent event) {
        if (currentState != BankState.INTERACTING_WITH_BANK) {
            return;
        }

        if (event.isSuccess()) {
            log.info("Bank interaction completed successfully");
            currentState = BankState.OPENING_BANK;
            idleTicks = 0;
        } else {
            log.warn("Bank interaction failed: {}", event.getFailureReason());
            currentState = BankState.FIND_BANK;
        }
    }

    @Override
    public String getTaskName() {
        return "Banking";
    }
}
