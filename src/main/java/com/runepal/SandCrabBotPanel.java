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

        // Configuration panel
        JPanel configPanel = createConfigurationPanel();
        
        // Status panel
        JPanel statusPanel = new JPanel(new BorderLayout());
        statusPanel.setBorder(BorderFactory.createTitledBorder("Status"));
        statusPanel.add(statusLabel, BorderLayout.CENTER);

        // Button panel
        JPanel buttonPanel = new JPanel(new BorderLayout());
        buttonPanel.add(toggleButton, BorderLayout.CENTER);

        add(configPanel, BorderLayout.NORTH);
        add(statusPanel, BorderLayout.CENTER);
        add(buttonPanel, BorderLayout.SOUTH);

        // Load current configuration values
        loadConfigurationValues();

        // Button actions
        toggleButton.addActionListener(e -> {
            configManager.setConfiguration("runepal", "startBot", !config.startBot());
        });
    }
    
    private JPanel createConfigurationPanel() {
        JPanel configPanel = new JPanel(new GridBagLayout());
        configPanel.setBorder(BorderFactory.createTitledBorder("Sand Crab Configuration"));
        
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.anchor = GridBagConstraints.WEST;
        
        // Food Type
        gbc.gridx = 0; gbc.gridy = 0;
        configPanel.add(new JLabel("Food Type:"), gbc);
        gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL;
        foodTypeComboBox.setToolTipText("Type of food to use for healing");
        configPanel.add(foodTypeComboBox, gbc);
        
        // Food Quantity
        gbc.gridx = 0; gbc.gridy = 1; gbc.fill = GridBagConstraints.NONE;
        configPanel.add(new JLabel("Food Quantity:"), gbc);
        gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL;
        foodQuantitySpinner.setToolTipText("Number of food items to withdraw (0-28)");
        configPanel.add(foodQuantitySpinner, gbc);
        
        // Potion Type
        gbc.gridx = 0; gbc.gridy = 2; gbc.fill = GridBagConstraints.NONE;
        configPanel.add(new JLabel("Potion Type:"), gbc);
        gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL;
        potionTypeComboBox.setToolTipText("Type of potion to use for stat boosts");
        configPanel.add(potionTypeComboBox, gbc);
        
        // Potion Quantity
        gbc.gridx = 0; gbc.gridy = 3; gbc.fill = GridBagConstraints.NONE;
        configPanel.add(new JLabel("Potion Quantity:"), gbc);
        gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL;
        potionQuantitySpinner.setToolTipText("Number of potion items to withdraw (0-28)");
        configPanel.add(potionQuantitySpinner, gbc);
        
        // Crab Count
        gbc.gridx = 0; gbc.gridy = 4; gbc.fill = GridBagConstraints.NONE;
        configPanel.add(new JLabel("Crab Count:"), gbc);
        gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL;
        crabCountSpinner.setToolTipText("Number of sand crabs to engage simultaneously (1-4)");
        configPanel.add(crabCountSpinner, gbc);
        
        // Eat at HP
        gbc.gridx = 0; gbc.gridy = 5; gbc.fill = GridBagConstraints.NONE;
        configPanel.add(new JLabel("Eat at HP:"), gbc);
        gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL;
        eatAtHpSpinner.setToolTipText("HP threshold for eating food (1-99)");
        configPanel.add(eatAtHpSpinner, gbc);
        
        // Inventory Action
        gbc.gridx = 0; gbc.gridy = 6; gbc.fill = GridBagConstraints.NONE;
        configPanel.add(new JLabel("When Out of Supplies:"), gbc);
        gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL;
        inventoryActionComboBox.setToolTipText("Action to take when consumables are depleted");
        configPanel.add(inventoryActionComboBox, gbc);
        
        // Min Food Count
        gbc.gridx = 0; gbc.gridy = 7; gbc.fill = GridBagConstraints.NONE;
        configPanel.add(new JLabel("Min Food Count:"), gbc);
        gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL;
        minFoodCountSpinner.setToolTipText("Minimum food count before banking (0-28)");
        configPanel.add(minFoodCountSpinner, gbc);
        
        // Min Potion Count
        gbc.gridx = 0; gbc.gridy = 8; gbc.fill = GridBagConstraints.NONE;
        configPanel.add(new JLabel("Min Potion Count:"), gbc);
        gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL;
        minPotionCountSpinner.setToolTipText("Minimum potion count before banking (0-28)");
        configPanel.add(minPotionCountSpinner, gbc);
        
        // Add event listeners to save changes
        foodTypeComboBox.addActionListener(e -> saveConfiguration());
        foodQuantitySpinner.addChangeListener(e -> saveConfiguration());
        potionTypeComboBox.addActionListener(e -> saveConfiguration());

        potionQuantitySpinner.addChangeListener(e -> saveConfiguration());
        crabCountSpinner.addChangeListener(e -> saveConfiguration());
        eatAtHpSpinner.addChangeListener(e -> saveConfiguration());
        minFoodCountSpinner.addChangeListener(e -> saveConfiguration());
        minPotionCountSpinner.addChangeListener(e -> saveConfiguration());

        inventoryActionComboBox.addActionListener(e -> saveConfiguration());
        
        return configPanel;
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
        statusLabel.setText("Status: " + status);
    }

    public void setButtonText(String text) {
        toggleButton.setText(text);
    }
}