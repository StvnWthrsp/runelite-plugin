package com.runepal;

import lombok.extern.slf4j.Slf4j;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.ui.PluginPanel;
import javax.swing.*;
import java.awt.*;

@Slf4j
public class BotPanel extends PluginPanel {
    private final RunepalPlugin plugin;
    private final BotConfig config;
    private final ConfigManager configManager;

    private final JComboBox<BotType> botTypeComboBox;
    private final JPanel contentPanel;
    private JPanel currentBotPanel;

    public BotPanel(RunepalPlugin plugin, BotConfig config, ConfigManager configManager) {
        super();
        this.plugin = plugin;
        this.config = config;
        this.configManager = configManager;

        setLayout(new BorderLayout());

        // Create status panel (read-only connection status)
        JPanel statusPanel = createStatusPanel();

        // Create the dropdown for bot type selection
        botTypeComboBox = new JComboBox<>(BotType.values());
        botTypeComboBox.setSelectedItem(config.botType());

        // Create the main content panel that will hold bot-specific interfaces
        contentPanel = new JPanel(new BorderLayout());

        // Dropdown panel
        JPanel dropdownPanel = new JPanel(new BorderLayout());
        dropdownPanel.setBorder(BorderFactory.createTitledBorder("Select Bot Type"));
        dropdownPanel.add(botTypeComboBox, BorderLayout.CENTER);

        // Combine status and dropdown into top panel
        JPanel topPanel = new JPanel(new BorderLayout());
        topPanel.add(statusPanel, BorderLayout.NORTH);
        topPanel.add(dropdownPanel, BorderLayout.CENTER);

        add(topPanel, BorderLayout.NORTH);
        add(contentPanel, BorderLayout.CENTER);

        // Add action listener to handle bot type selection changes
        botTypeComboBox.addActionListener(e -> {
            BotType selectedType = (BotType) botTypeComboBox.getSelectedItem();
            if (selectedType != null) {
                configManager.setConfiguration("runepal", "botType", selectedType);
                updateContentPanel(selectedType);
            }
        });

        // Initialize with current bot type
        updateContentPanel(config.botType());
    }

    private JLabel statusLabel;

    private JPanel createStatusPanel() {
        JPanel statusPanel = new JPanel(new BorderLayout());
        statusPanel.setBorder(BorderFactory.createTitledBorder("RemoteInput Status"));

        // Status label
        statusLabel = new JLabel("INITIALIZING...");
        statusLabel.setHorizontalAlignment(JLabel.CENTER);
        updateStatusLabel();

        statusPanel.add(statusLabel, BorderLayout.CENTER);

        return statusPanel;
    }

    private void updateStatusLabel() {
        if (plugin.isAutomationConnected()) {
            statusLabel.setText("CONNECTED");
            statusLabel.setForeground(Color.GREEN);
        } else {
            statusLabel.setText("DISCONNECTED");
            statusLabel.setForeground(Color.LIGHT_GRAY);
        }
    }

    private void updateContentPanel(BotType botType) {
        // Remove current bot panel if it exists
        if (currentBotPanel != null) {
            contentPanel.remove(currentBotPanel);
        }

        // Create appropriate bot panel based on selection
        switch (botType) {
            case MINING_BOT:
                currentBotPanel = createMiningBotPanel();
                break;
            case COMBAT_BOT:
                currentBotPanel = createCombatBotPanel();
                break;
            case FISHING_BOT:
                currentBotPanel = createFishingBotPanel();
                break;
            case WOODCUTTING_BOT:
                currentBotPanel = createWoodcuttingBotPanel();
                break;
            case SAND_CRAB_BOT:
                currentBotPanel = createSandCrabBotPanel();
                break;
            case GEMSTONE_CRAB_BOT:
                currentBotPanel = createGemstoneCrabBotPanel();
                break;
            case HIGH_ALCH_BOT:
                currentBotPanel = new HighAlchBotPanel(plugin, config, configManager);
                break;
            default:
                log.warn("Unable to load a panel for {}", botType);
                currentBotPanel = createEmptyPanel();
                break;
        }

        contentPanel.add(currentBotPanel, BorderLayout.CENTER);

        // Refresh the panel
        revalidate();
        repaint();
    }

    private JPanel createMiningBotPanel() {
        // Create the mining-specific panel - we can pass the BotConfig directly now
        // since BotConfig contains all the methods that MiningBotPanel needs
        return new MiningBotPanel(plugin, config, configManager);
    }

    private JPanel createCombatBotPanel() {
        return new CombatBotPanel(plugin, config, configManager);
    }

    private JPanel createFishingBotPanel() {
        return new FishingBotPanel(plugin, config, configManager);
    }

    private JPanel createWoodcuttingBotPanel() {
        return new WoodcuttingBotPanel(plugin, config, configManager);
    }

    private JPanel createSandCrabBotPanel() {
        return new SandCrabBotPanel(plugin, config, configManager);
    }

    private JPanel createGemstoneCrabBotPanel() {
        return new GemstoneCrabBotPanel(plugin, config, configManager);
    }

    private JPanel createEmptyPanel() {
        JPanel emptyPanel = new JPanel();
        emptyPanel.add(new JLabel("Select a bot type from the dropdown above"));
        return emptyPanel;
    }

    public void updateBotTypeSelection() {
        botTypeComboBox.setSelectedItem(config.botType());
        updateContentPanel(config.botType());
    }

    // Delegate methods to current bot panel (for backwards compatibility)
    public void setStatus(String status) {
        if (currentBotPanel instanceof MiningBotPanel) {
            ((MiningBotPanel) currentBotPanel).setStatus(status);
        } else if (currentBotPanel instanceof CombatBotPanel) {
            ((CombatBotPanel) currentBotPanel).setStatus(status);
        } else if (currentBotPanel instanceof FishingBotPanel) {
            ((FishingBotPanel) currentBotPanel).setStatus(status);
        } else if (currentBotPanel instanceof WoodcuttingBotPanel) {
            ((WoodcuttingBotPanel) currentBotPanel).setStatus(status);
        } else if (currentBotPanel instanceof SandCrabBotPanel) {
            ((SandCrabBotPanel) currentBotPanel).setStatus(status);
        } else if (currentBotPanel instanceof GemstoneCrabBotPanel) {
            ((GemstoneCrabBotPanel) currentBotPanel).setStatus(status);
        } else if (currentBotPanel instanceof HighAlchBotPanel) {
            ((HighAlchBotPanel) currentBotPanel).setStatus(status);
        }
    }

    public void setButtonText(String text) {
        if (currentBotPanel instanceof MiningBotPanel) {
            ((MiningBotPanel) currentBotPanel).setButtonText(text);
        } else if (currentBotPanel instanceof CombatBotPanel) {
            ((CombatBotPanel) currentBotPanel).setButtonText(text);
        } else if (currentBotPanel instanceof FishingBotPanel) {
            ((FishingBotPanel) currentBotPanel).setButtonText(text);
        } else if (currentBotPanel instanceof WoodcuttingBotPanel) {
            ((WoodcuttingBotPanel) currentBotPanel).setButtonText(text);
        } else if (currentBotPanel instanceof SandCrabBotPanel) {
            ((SandCrabBotPanel) currentBotPanel).setButtonText(text);
        } else if (currentBotPanel instanceof GemstoneCrabBotPanel) {
            ((GemstoneCrabBotPanel) currentBotPanel).setButtonText(text);
        } else if (currentBotPanel instanceof HighAlchBotPanel) {
            ((HighAlchBotPanel) currentBotPanel).setButtonText(text);
        }
    }

    public void updateConnectionStatus() {
        // Update the status label with current connection state
        if (statusLabel != null) {
            updateStatusLabel();
        }
    }
}