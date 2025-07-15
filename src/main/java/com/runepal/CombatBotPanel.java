package com.runepal;

import net.runelite.client.config.ConfigManager;
import net.runelite.client.ui.PluginPanel;
import javax.swing.*;
import java.awt.*;

public class CombatBotPanel extends PluginPanel {
    private final RunepalPlugin plugin;
    private final BotConfig config;
    private final ConfigManager configManager;
    
    private JButton startStopButton;
    private JLabel statusLabel;
    private JTextField npcNamesField;
    private JSpinner healthPercentSpinner;
    
    // Potion settings
    private JCheckBox usePrayerPotionsCheckBox;
    private JSpinner prayerPotionThresholdSpinner;
    private JCheckBox useCombatPotionsCheckBox;
    private JCheckBox useAntipoisonCheckBox;
    
    // Prayer settings
    private JCheckBox usePrayersCheckBox;
    private JComboBox<String> offensivePrayerComboBox;
    private JComboBox<String> defensivePrayerComboBox;
    private JSpinner prayerPointThresholdSpinner;
    
    // Banking settings
    private JSpinner minFoodCountSpinner;
    private JSpinner minPrayerPotionsSpinner;
    private JSpinner minCombatPotionsSpinner;
    
    // Loot settings
    private JCheckBox autoLootCheckBox;
    private JSpinner lootValueThresholdSpinner;
    
    private boolean isRunning = false;

    public CombatBotPanel(RunepalPlugin plugin, BotConfig config, ConfigManager configManager) {
        super();
        this.plugin = plugin;
        this.config = config;
        this.configManager = configManager;
        
        setLayout(new BorderLayout());
        
        // Create main content panel
        JPanel contentPanel = new JPanel();
        contentPanel.setLayout(new BoxLayout(contentPanel, BoxLayout.Y_AXIS));
        contentPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        
        // Add configuration sections
        contentPanel.add(createBasicConfigurationPanel());
        contentPanel.add(Box.createVerticalStrut(5));
        contentPanel.add(createPotionConfigurationPanel());
        contentPanel.add(Box.createVerticalStrut(5));
        contentPanel.add(createPrayerConfigurationPanel());
        contentPanel.add(Box.createVerticalStrut(5));
        contentPanel.add(createBankingConfigurationPanel());
        contentPanel.add(Box.createVerticalStrut(5));
        contentPanel.add(createLootConfigurationPanel());
        contentPanel.add(Box.createVerticalStrut(10));
        
        // Add control section
        contentPanel.add(createControlPanel());
        contentPanel.add(Box.createVerticalStrut(10));
        
        // Add status section
        contentPanel.add(createStatusPanel());
        
        add(contentPanel, BorderLayout.NORTH);
        
        // Initialize UI state
        updateUIFromConfig();
    }
    
    private JPanel createBasicConfigurationPanel() {
        JPanel configPanel = new JPanel(new GridBagLayout());
        configPanel.setBorder(BorderFactory.createTitledBorder("Basic Combat Configuration"));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        
        // NPC Names field
        gbc.gridx = 0; gbc.gridy = 0; gbc.anchor = GridBagConstraints.WEST;
        configPanel.add(new JLabel("Target NPCs:"), gbc);
        
        gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1.0;
        npcNamesField = new JTextField(config.combatNpcNames(), 20);
        npcNamesField.setToolTipText("Comma-separated list of NPC names (e.g., Goblin,Cow,Rat)");
        configPanel.add(npcNamesField, gbc);
        
        // Health percentage spinner
        gbc.gridx = 0; gbc.gridy = 1; gbc.fill = GridBagConstraints.NONE; gbc.weightx = 0;
        configPanel.add(new JLabel("Eat at HP %:"), gbc);
        
        gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1.0;
        healthPercentSpinner = new JSpinner(new SpinnerNumberModel(config.combatEatAtHealthPercent(), 1, 99, 1));
        healthPercentSpinner.setToolTipText("Health percentage at which to eat food (1-99%)");
        configPanel.add(healthPercentSpinner, gbc);
        
        return configPanel;
    }
    
    private JPanel createPotionConfigurationPanel() {
        JPanel potionPanel = new JPanel(new GridBagLayout());
        potionPanel.setBorder(BorderFactory.createTitledBorder("Potion Settings"));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(3, 5, 3, 5);
        
        // Prayer potions checkbox
        gbc.gridx = 0; gbc.gridy = 0; gbc.anchor = GridBagConstraints.WEST; gbc.fill = GridBagConstraints.NONE; gbc.weightx = 0;
        usePrayerPotionsCheckBox = new JCheckBox("Use Prayer Potions", config.combatUsePrayerPotions());
        potionPanel.add(usePrayerPotionsCheckBox, gbc);
        
        // Prayer potion threshold
        gbc.gridx = 0; gbc.gridy = 1; gbc.anchor = GridBagConstraints.WEST;
        potionPanel.add(new JLabel("Prayer Potion Threshold:"), gbc);
        
        gbc.gridx = 0; gbc.gridy = 2; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1.0;
        prayerPotionThresholdSpinner = new JSpinner(new SpinnerNumberModel(config.combatPrayerPotionThreshold(), 1, 99, 1));
        prayerPotionThresholdSpinner.setToolTipText("Prayer % at which to drink prayer potion");
        potionPanel.add(prayerPotionThresholdSpinner, gbc);
        
        // Combat potions
        gbc.gridx = 0; gbc.gridy = 3; gbc.fill = GridBagConstraints.NONE; gbc.weightx = 0;
        useCombatPotionsCheckBox = new JCheckBox("Use Combat Potions", config.combatUseCombatPotions());
        useCombatPotionsCheckBox.setToolTipText("Auto-consume super combat potions");
        potionPanel.add(useCombatPotionsCheckBox, gbc);
        
        // Antipoison
        gbc.gridx = 0; gbc.gridy = 4;
        useAntipoisonCheckBox = new JCheckBox("Use Antipoison", config.combatUseAntipoison());
        useAntipoisonCheckBox.setToolTipText("Auto-consume antipoison when poisoned");
        potionPanel.add(useAntipoisonCheckBox, gbc);
        
        return potionPanel;
    }
    
    private JPanel createPrayerConfigurationPanel() {
        JPanel prayerPanel = new JPanel(new GridBagLayout());
        prayerPanel.setBorder(BorderFactory.createTitledBorder("Prayer Settings"));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(3, 5, 3, 5);
        
        // Use prayers checkbox
        gbc.gridx = 0; gbc.gridy = 0; gbc.anchor = GridBagConstraints.WEST; gbc.fill = GridBagConstraints.NONE; gbc.weightx = 0;
        usePrayersCheckBox = new JCheckBox("Use Prayers", config.combatUsePrayers());
        prayerPanel.add(usePrayersCheckBox, gbc);
        
        // Prayer point threshold label
        gbc.gridx = 0; gbc.gridy = 1; gbc.anchor = GridBagConstraints.WEST;
        prayerPanel.add(new JLabel("Min Prayer %:"), gbc);
        
        // Prayer point threshold spinner
        gbc.gridx = 0; gbc.gridy = 2; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1.0;
        prayerPointThresholdSpinner = new JSpinner(new SpinnerNumberModel(config.combatPrayerPointThreshold(), 1, 99, 1));
        prayerPointThresholdSpinner.setToolTipText("Prayer % below which to deactivate prayers");
        prayerPanel.add(prayerPointThresholdSpinner, gbc);
        
        // Offensive prayer label
        gbc.gridx = 0; gbc.gridy = 3; gbc.fill = GridBagConstraints.NONE; gbc.weightx = 0;
        prayerPanel.add(new JLabel("Offensive:"), gbc);
        
        // Offensive prayer combobox
        gbc.gridx = 0; gbc.gridy = 4; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1.0;
        String[] offensivePrayers = {"None", "Ultimate Strength", "Incredible Reflexes", "Chivalry", "Piety"};
        offensivePrayerComboBox = new JComboBox<>(offensivePrayers);
        offensivePrayerComboBox.setSelectedItem(config.combatOffensivePrayer());
        prayerPanel.add(offensivePrayerComboBox, gbc);
        
        // Defensive prayer label
        gbc.gridx = 0; gbc.gridy = 5; gbc.fill = GridBagConstraints.NONE; gbc.weightx = 0;
        prayerPanel.add(new JLabel("Defensive:"), gbc);
        
        // Defensive prayer combobox
        gbc.gridx = 0; gbc.gridy = 6; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1.0;
        String[] defensivePrayers = {"None", "Protect from Melee", "Protect from Missiles", "Protect from Magic"};
        defensivePrayerComboBox = new JComboBox<>(defensivePrayers);
        defensivePrayerComboBox.setSelectedItem(config.combatDefensivePrayer());
        prayerPanel.add(defensivePrayerComboBox, gbc);
        
        return prayerPanel;
    }
    
    private JPanel createBankingConfigurationPanel() {
        JPanel bankPanel = new JPanel(new GridBagLayout());
        bankPanel.setBorder(BorderFactory.createTitledBorder("Banking Settings"));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(3, 5, 3, 5);
        
        // Min food count
        gbc.gridx = 0; gbc.gridy = 0; gbc.anchor = GridBagConstraints.WEST;
        bankPanel.add(new JLabel("Min Food:"), gbc);
        
        gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1.0;
        minFoodCountSpinner = new JSpinner(new SpinnerNumberModel(config.combatMinFoodCount(), 1, 28, 1));
        minFoodCountSpinner.setToolTipText("Minimum food count before banking");
        bankPanel.add(minFoodCountSpinner, gbc);
        
        // Min prayer potions
        gbc.gridx = 0; gbc.gridy = 1; gbc.fill = GridBagConstraints.NONE; gbc.weightx = 0;
        bankPanel.add(new JLabel("Min Prayer Pots:"), gbc);
        
        gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1.0;
        minPrayerPotionsSpinner = new JSpinner(new SpinnerNumberModel(config.combatMinPrayerPotions(), 0, 28, 1));
        minPrayerPotionsSpinner.setToolTipText("Minimum prayer potions before banking");
        bankPanel.add(minPrayerPotionsSpinner, gbc);
        
        // Min combat potions
        gbc.gridx = 0; gbc.gridy = 2; gbc.fill = GridBagConstraints.NONE; gbc.weightx = 0;
        bankPanel.add(new JLabel("Min Combat Pots:"), gbc);
        
        gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1.0;
        minCombatPotionsSpinner = new JSpinner(new SpinnerNumberModel(config.combatMinCombatPotions(), 0, 28, 1));
        minCombatPotionsSpinner.setToolTipText("Minimum combat potions before banking");
        bankPanel.add(minCombatPotionsSpinner, gbc);
        
        return bankPanel;
    }
    
    private JPanel createLootConfigurationPanel() {
        JPanel lootPanel = new JPanel(new GridBagLayout());
        lootPanel.setBorder(BorderFactory.createTitledBorder("Loot Settings"));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(3, 5, 3, 5);
        
        // Auto loot checkbox
        gbc.gridx = 0; gbc.gridy = 0; gbc.anchor = GridBagConstraints.WEST; gbc.fill = GridBagConstraints.NONE; gbc.weightx = 0;
        autoLootCheckBox = new JCheckBox("Auto Loot", config.combatAutoLoot());
        lootPanel.add(autoLootCheckBox, gbc);
        
        // Loot value threshold label
        gbc.gridx = 0; gbc.gridy = 1; gbc.anchor = GridBagConstraints.WEST;
        lootPanel.add(new JLabel("Min Value:"), gbc);
        
        // Loot value threshold spinner
        gbc.gridx = 0; gbc.gridy = 2; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1.0;
        lootValueThresholdSpinner = new JSpinner(new SpinnerNumberModel(config.combatLootValueThreshold(), 0, 100000, 50));
        lootValueThresholdSpinner.setToolTipText("Minimum GP value of items to loot");
        lootPanel.add(lootValueThresholdSpinner, gbc);
        
        // Save button for all configurations
        gbc.gridx = 0; gbc.gridy = 3; gbc.fill = GridBagConstraints.HORIZONTAL;
        JButton saveButton = new JButton("Save All Configuration");
        saveButton.addActionListener(e -> saveConfiguration());
        lootPanel.add(saveButton, gbc);
        
        return lootPanel;
    }
    
    private JPanel createControlPanel() {
        JPanel controlPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        controlPanel.setBorder(BorderFactory.createTitledBorder("Bot Control"));
        
        startStopButton = new JButton("Start Combat Bot");
        startStopButton.setPreferredSize(new Dimension(200, 40));
        startStopButton.addActionListener(e -> toggleBot());
        
        controlPanel.add(startStopButton);
        
        return controlPanel;
    }
    
    private JPanel createStatusPanel() {
        JPanel statusPanel = new JPanel(new BorderLayout());
        statusPanel.setBorder(BorderFactory.createTitledBorder("Status"));
        
        statusLabel = new JLabel("Combat bot is stopped");
        statusLabel.setHorizontalAlignment(SwingConstants.CENTER);
        statusLabel.setForeground(Color.RED);
        
        statusPanel.add(statusLabel, BorderLayout.CENTER);
        
        return statusPanel;
    }
    
    private void updateUIFromConfig() {
        // Basic settings
        npcNamesField.setText(config.combatNpcNames());
        healthPercentSpinner.setValue(config.combatEatAtHealthPercent());
        
        // Potion settings
        usePrayerPotionsCheckBox.setSelected(config.combatUsePrayerPotions());
        prayerPotionThresholdSpinner.setValue(config.combatPrayerPotionThreshold());
        useCombatPotionsCheckBox.setSelected(config.combatUseCombatPotions());
        useAntipoisonCheckBox.setSelected(config.combatUseAntipoison());
        
        // Prayer settings
        usePrayersCheckBox.setSelected(config.combatUsePrayers());
        offensivePrayerComboBox.setSelectedItem(config.combatOffensivePrayer());
        defensivePrayerComboBox.setSelectedItem(config.combatDefensivePrayer());
        prayerPointThresholdSpinner.setValue(config.combatPrayerPointThreshold());
        
        // Banking settings
        minFoodCountSpinner.setValue(config.combatMinFoodCount());
        minPrayerPotionsSpinner.setValue(config.combatMinPrayerPotions());
        minCombatPotionsSpinner.setValue(config.combatMinCombatPotions());
        
        // Loot settings
        autoLootCheckBox.setSelected(config.combatAutoLoot());
        lootValueThresholdSpinner.setValue(config.combatLootValueThreshold());
    }
    
    private void saveConfiguration() {
        try {
            // Basic settings
            String npcNames = npcNamesField.getText().trim();
            configManager.setConfiguration("runepal", "combatNpcNames", npcNames);
            
            int healthPercent = (Integer) healthPercentSpinner.getValue();
            configManager.setConfiguration("runepal", "combatEatAtHealthPercent", healthPercent);
            
            // Potion settings
            configManager.setConfiguration("runepal", "combatUsePrayerPotions", usePrayerPotionsCheckBox.isSelected());
            configManager.setConfiguration("runepal", "combatPrayerPotionThreshold", (Integer) prayerPotionThresholdSpinner.getValue());
            configManager.setConfiguration("runepal", "combatUseCombatPotions", useCombatPotionsCheckBox.isSelected());
            configManager.setConfiguration("runepal", "combatUseAntipoison", useAntipoisonCheckBox.isSelected());
            
            // Prayer settings
            configManager.setConfiguration("runepal", "combatUsePrayers", usePrayersCheckBox.isSelected());
            configManager.setConfiguration("runepal", "combatOffensivePrayer", (String) offensivePrayerComboBox.getSelectedItem());
            configManager.setConfiguration("runepal", "combatDefensivePrayer", (String) defensivePrayerComboBox.getSelectedItem());
            configManager.setConfiguration("runepal", "combatPrayerPointThreshold", (Integer) prayerPointThresholdSpinner.getValue());
            
            // Banking settings
            configManager.setConfiguration("runepal", "combatMinFoodCount", (Integer) minFoodCountSpinner.getValue());
            configManager.setConfiguration("runepal", "combatMinPrayerPotions", (Integer) minPrayerPotionsSpinner.getValue());
            configManager.setConfiguration("runepal", "combatMinCombatPotions", (Integer) minCombatPotionsSpinner.getValue());
            
            // Loot settings
            configManager.setConfiguration("runepal", "combatAutoLoot", autoLootCheckBox.isSelected());
            configManager.setConfiguration("runepal", "combatLootValueThreshold", (Integer) lootValueThresholdSpinner.getValue());
            
            JOptionPane.showMessageDialog(this, 
                "Configuration saved successfully!", 
                "Success", 
                JOptionPane.INFORMATION_MESSAGE);
                
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, 
                "Error saving configuration: " + e.getMessage(), 
                "Error", 
                JOptionPane.ERROR_MESSAGE);
        }
    }
    
    private void toggleBot() {        
        if (!isRunning) {
            // Validate configuration before starting
            String npcNames = npcNamesField.getText().trim();
            if (npcNames.isEmpty()) {
                JOptionPane.showMessageDialog(this, 
                    "Please specify at least one NPC name to target!", 
                    "Configuration Error", 
                    JOptionPane.WARNING_MESSAGE);
                return;
            }
            
            // Save current configuration
            saveConfiguration();
            
            // Start the bot
            startBot();
        } else {
            // Stop the bot
            stopBot();
        }
    }
    
    private void startBot() {
        // Set bot type and start
        configManager.setConfiguration("runepal", "botType", BotType.COMBAT_BOT);
        configManager.setConfiguration("runepal", "startBot", true);
        
        isRunning = true;
        startStopButton.setText("Stop Combat Bot");
        statusLabel.setText("Combat bot is running...");
        statusLabel.setForeground(Color.GREEN);
        
        // Disable configuration controls while running
        npcNamesField.setEnabled(false);
        healthPercentSpinner.setEnabled(false);
    }
    
    private void stopBot() {
        configManager.setConfiguration("runepal", "startBot", false);
        plugin.stopBot();
        
        isRunning = false;
        startStopButton.setText("Start Combat Bot");
        statusLabel.setText("Combat bot is stopped");
        statusLabel.setForeground(Color.RED);
        
        // Re-enable configuration controls
        npcNamesField.setEnabled(true);
        healthPercentSpinner.setEnabled(true);
    }
    
    // Public methods for external status updates
    public void setStatus(String status) {
        if (statusLabel != null) {
            SwingUtilities.invokeLater(() -> {
                if (isRunning) {
                    statusLabel.setText("Combat bot: " + status);
                }
            });
        }
    }
    
    public void setButtonText(String text) {
        if (startStopButton != null) {
            SwingUtilities.invokeLater(() -> {
                if (text.toLowerCase().contains("stop")) {
                    isRunning = true;
                    startStopButton.setText("Stop Combat Bot");
                    statusLabel.setForeground(Color.GREEN);
                    npcNamesField.setEnabled(false);
                    healthPercentSpinner.setEnabled(false);
                } else {
                    isRunning = false;
                    startStopButton.setText("Start Combat Bot");
                    statusLabel.setForeground(Color.RED);
                    npcNamesField.setEnabled(true);
                    healthPercentSpinner.setEnabled(true);
                }
            });
        }
    }
    
    public void onBotStopped() {
        SwingUtilities.invokeLater(() -> {
            isRunning = false;
            startStopButton.setText("Start Combat Bot");
            statusLabel.setText("Combat bot is stopped");
            statusLabel.setForeground(Color.RED);
            npcNamesField.setEnabled(true);
            healthPercentSpinner.setEnabled(true);
        });
    }
} 