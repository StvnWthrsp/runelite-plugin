package com.runepal;

import net.runelite.client.config.ConfigManager;
import net.runelite.client.ui.PluginPanel;
import javax.swing.*;
import java.awt.*;

public class SandCrabBotPanel extends PluginPanel {
    private final JLabel statusLabel = new JLabel("Status: IDLE");
    private final JButton toggleButton = new JButton("Start");
    private final RunepalPlugin plugin;
    private final BotConfig config;
    private final ConfigManager configManager;
    
    // Configuration controls
    private final JComboBox<SandCrabTask.FoodType> foodTypeComboBox;
    private final JSpinner foodQuantitySpinner;
    private final JComboBox<SandCrabTask.PotionType> potionTypeComboBox;
    private final JSpinner potionQuantitySpinner;
    private final JSpinner crabCountSpinner;
    private final JSpinner eatAtHpSpinner;
    private final JComboBox<String> inventoryActionComboBox;
    private final JSpinner minFoodCountSpinner;
    private final JSpinner minPotionCountSpinner;

    public SandCrabBotPanel(RunepalPlugin plugin, BotConfig config, ConfigManager configManager) {
        super();
        this.plugin = plugin;
        this.config = config;
        this.configManager = configManager;
        
        // Initialize configuration controls
        this.foodTypeComboBox = new JComboBox<>(SandCrabTask.FoodType.values());
        this.foodQuantitySpinner = new JSpinner(new SpinnerNumberModel(config.sandCrabFoodQuantity(), 0, 28, 1));
        this.potionTypeComboBox = new JComboBox<>(SandCrabTask.PotionType.values());
        this.potionQuantitySpinner = new JSpinner(new SpinnerNumberModel(config.sandCrabPotionQuantity(), 0, 28, 1));
        this.crabCountSpinner = new JSpinner(new SpinnerNumberModel(config.sandCrabCount(), 1, 4, 1));
        this.eatAtHpSpinner = new JSpinner(new SpinnerNumberModel(config.sandCrabEatAtHp(), 1, 99, 1));
        this.inventoryActionComboBox = new JComboBox<>(new String[]{"BANK", "LOGOUT"});
        this.minFoodCountSpinner = new JSpinner(new SpinnerNumberModel(config.sandCrabMinFoodCount(), 0, 28, 1));
        this.minPotionCountSpinner = new JSpinner(new SpinnerNumberModel(config.sandCrabMinPotionCount(), 0, 28, 1));
        
        setLayout(new BorderLayout());
        
        // Create main content panel with vertical layout
        JPanel contentPanel = new JPanel();
        contentPanel.setLayout(new BoxLayout(contentPanel, BoxLayout.Y_AXIS));
        
        // Add configuration sections
        contentPanel.add(createBasicConfigurationPanel());
        contentPanel.add(Box.createVerticalStrut(5));
        contentPanel.add(createSupplyConfigurationPanel());
        contentPanel.add(Box.createVerticalStrut(5));
        contentPanel.add(createBankingConfigurationPanel());
        contentPanel.add(Box.createVerticalStrut(10));
        
        // Add control section
        contentPanel.add(createControlPanel());
        contentPanel.add(Box.createVerticalStrut(10));
        
        // Add status section
        contentPanel.add(createStatusPanel());
        
        add(contentPanel, BorderLayout.NORTH);

        // Load current configuration values
        loadConfigurationValues();

        // Button actions
        toggleButton.addActionListener(e -> {
            configManager.setConfiguration("runepal", "startBot", !config.startBot());
        });
    }
    
    private JPanel createBasicConfigurationPanel() {
        JPanel configPanel = new JPanel(new GridBagLayout());
//        configPanel.setBorder(BorderFactory.createTitledBorder("Basic Sand Crab Configuration"));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(3, 5, 3, 5);
        
        // Crab Count label
        gbc.gridx = 0; gbc.gridy = 0; gbc.anchor = GridBagConstraints.WEST;
        configPanel.add(new JLabel("Crab Count:"), gbc);
        
        // Crab Count spinner
        gbc.gridx = 1; gbc.gridy = 0; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1.0;
        crabCountSpinner.setToolTipText("Number of sand crabs to engage simultaneously (1-4)");
        configPanel.add(crabCountSpinner, gbc);
        
        // Eat at HP label
        gbc.gridx = 0; gbc.gridy = 1; gbc.fill = GridBagConstraints.NONE; gbc.weightx = 0;
        configPanel.add(new JLabel("Eat at HP:"), gbc);
        
        // Eat at HP spinner
        gbc.gridx = 1; gbc.gridy = 1; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1.0;
        eatAtHpSpinner.setToolTipText("HP threshold for eating food (1-99)");
        configPanel.add(eatAtHpSpinner, gbc);
        
        // Add event listeners
        crabCountSpinner.addChangeListener(e -> saveConfiguration());
        eatAtHpSpinner.addChangeListener(e -> saveConfiguration());
        
        return configPanel;
    }
    
    private JPanel createSupplyConfigurationPanel() {
        JPanel supplyPanel = new JPanel(new GridBagLayout());
//        supplyPanel.setBorder(BorderFactory.createTitledBorder("Supply Configuration"));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(3, 5, 3, 5);
        
        // Food Type label
        gbc.gridx = 0; gbc.gridy = 0; gbc.anchor = GridBagConstraints.WEST;
        supplyPanel.add(new JLabel("Food Type:"), gbc);
        
        // Food Type combo box
        gbc.gridx = 1; gbc.gridy = 0; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1.0;
        foodTypeComboBox.setToolTipText("Type of food to use for healing");
        supplyPanel.add(foodTypeComboBox, gbc);
        
        // Food Quantity label
        gbc.gridx = 0; gbc.gridy = 1; gbc.fill = GridBagConstraints.NONE; gbc.weightx = 0;
        supplyPanel.add(new JLabel("Food Quantity:"), gbc);
        
        // Food Quantity spinner
        gbc.gridx = 1; gbc.gridy = 1; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1.0;
        foodQuantitySpinner.setToolTipText("Number of food items to withdraw (0-28)");
        supplyPanel.add(foodQuantitySpinner, gbc);
        
        // Potion Type label
        gbc.gridx = 0; gbc.gridy = 2; gbc.fill = GridBagConstraints.NONE; gbc.weightx = 0;
        supplyPanel.add(new JLabel("Potion Type:"), gbc);
        
        // Potion Type combo box
        gbc.gridx = 1; gbc.gridy = 2; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1.0;
        potionTypeComboBox.setToolTipText("Type of potion to use for stat boosts");
        supplyPanel.add(potionTypeComboBox, gbc);
        
        // Potion Quantity label
        gbc.gridx = 0; gbc.gridy = 3; gbc.fill = GridBagConstraints.NONE; gbc.weightx = 0;
        supplyPanel.add(new JLabel("Potion Quantity:"), gbc);
        
        // Potion Quantity spinner
        gbc.gridx = 1; gbc.gridy = 3; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1.0;
        potionQuantitySpinner.setToolTipText("Number of potion items to withdraw (0-28)");
        supplyPanel.add(potionQuantitySpinner, gbc);
        
        // Add event listeners
        foodTypeComboBox.addActionListener(e -> saveConfiguration());
        foodQuantitySpinner.addChangeListener(e -> saveConfiguration());
        potionTypeComboBox.addActionListener(e -> saveConfiguration());
        potionQuantitySpinner.addChangeListener(e -> saveConfiguration());
        
        return supplyPanel;
    }
    
    private JPanel createBankingConfigurationPanel() {
        JPanel bankPanel = new JPanel(new GridBagLayout());
//        bankPanel.setBorder(BorderFactory.createTitledBorder("Banking Settings"));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(3, 5, 3, 5);
        
        // Inventory Action label
        gbc.gridx = 0; gbc.gridy = 0; gbc.anchor = GridBagConstraints.WEST;
        bankPanel.add(new JLabel("When Out of Supplies:"), gbc);
        
        // Inventory Action combo box
        gbc.gridx = 1; gbc.gridy = 0; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1.0;
        inventoryActionComboBox.setToolTipText("Action to take when consumables are depleted");
        bankPanel.add(inventoryActionComboBox, gbc);
        
        // Min Food Count label
        gbc.gridx = 0; gbc.gridy = 1; gbc.fill = GridBagConstraints.NONE; gbc.weightx = 0;
        bankPanel.add(new JLabel("Min Food Count:"), gbc);
        
        // Min Food Count spinner
        gbc.gridx = 1; gbc.gridy = 1; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1.0;
        minFoodCountSpinner.setToolTipText("Minimum food count before banking (0-28)");
        bankPanel.add(minFoodCountSpinner, gbc);
        
        // Min Potion Count label
        gbc.gridx = 0; gbc.gridy = 2; gbc.fill = GridBagConstraints.NONE; gbc.weightx = 0;
        bankPanel.add(new JLabel("Min Potion Count:"), gbc);
        
        // Min Potion Count spinner
        gbc.gridx = 1; gbc.gridy = 2; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1.0;
        minPotionCountSpinner.setToolTipText("Minimum potion count before banking (0-28)");
        bankPanel.add(minPotionCountSpinner, gbc);
        
        // Add event listeners
        inventoryActionComboBox.addActionListener(e -> saveConfiguration());
        minFoodCountSpinner.addChangeListener(e -> saveConfiguration());
        minPotionCountSpinner.addChangeListener(e -> saveConfiguration());
        
        return bankPanel;
    }
    
    private JPanel createControlPanel() {
        JPanel controlPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        controlPanel.setBorder(BorderFactory.createTitledBorder("Bot Control"));
        
        toggleButton.setPreferredSize(new Dimension(200, 40));
        controlPanel.add(toggleButton);
        
        return controlPanel;
    }
    
    private JPanel createStatusPanel() {
        JPanel statusPanel = new JPanel(new BorderLayout());
        statusPanel.setBorder(BorderFactory.createTitledBorder("Status"));
        
        statusLabel.setHorizontalAlignment(SwingConstants.CENTER);
        statusPanel.add(statusLabel, BorderLayout.CENTER);
        
        return statusPanel;
    }
    
    private void saveConfiguration() {
        SandCrabTask.FoodType selectedFood = (SandCrabTask.FoodType) foodTypeComboBox.getSelectedItem();
        SandCrabTask.PotionType selectedPotion = (SandCrabTask.PotionType) potionTypeComboBox.getSelectedItem();
        
        configManager.setConfiguration("runepal", "sandCrabFood", selectedFood != null ? selectedFood.name() : "COOKED_KARAMBWAN");
        configManager.setConfiguration("runepal", "sandCrabFoodQuantity", (Integer) foodQuantitySpinner.getValue());
        configManager.setConfiguration("runepal", "sandCrabPotion", selectedPotion != null ? selectedPotion.name() : "NONE");
        configManager.setConfiguration("runepal", "sandCrabPotionQuantity", (Integer) potionQuantitySpinner.getValue());
        configManager.setConfiguration("runepal", "sandCrabCount", (Integer) crabCountSpinner.getValue());
        configManager.setConfiguration("runepal", "sandCrabEatAtHp", (Integer) eatAtHpSpinner.getValue());
        configManager.setConfiguration("runepal", "sandCrabInventoryAction", (String) inventoryActionComboBox.getSelectedItem());
        configManager.setConfiguration("runepal", "sandCrabMinFoodCount", (Integer) minFoodCountSpinner.getValue());
        configManager.setConfiguration("runepal", "sandCrabMinPotionCount", (Integer) minPotionCountSpinner.getValue());
    }
    
    private void loadConfigurationValues() {
        SandCrabTask.FoodType configuredFood = SandCrabTask.FoodType.fromString(config.sandCrabFood());
        SandCrabTask.PotionType configuredPotion = SandCrabTask.PotionType.fromString(config.sandCrabPotion());
        
        foodTypeComboBox.setSelectedItem(configuredFood);
        foodQuantitySpinner.setValue(config.sandCrabFoodQuantity());
        potionTypeComboBox.setSelectedItem(configuredPotion);
        potionQuantitySpinner.setValue(config.sandCrabPotionQuantity());
        crabCountSpinner.setValue(config.sandCrabCount());
        eatAtHpSpinner.setValue(config.sandCrabEatAtHp());
        inventoryActionComboBox.setSelectedItem(config.sandCrabInventoryAction());
        minFoodCountSpinner.setValue(config.sandCrabMinFoodCount());
        minPotionCountSpinner.setValue(config.sandCrabMinPotionCount());
    }

    public void setStatus(String status) {
        statusLabel.setText(status);
    }

    public void setButtonText(String text) {
        toggleButton.setText(text);
    }
}