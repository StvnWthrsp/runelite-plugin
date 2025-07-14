package com.runepal;

import net.runelite.client.config.ConfigManager;
import net.runelite.client.ui.PluginPanel;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;

public class FishingBotPanel extends PluginPanel {
    private final JLabel statusLabel = new JLabel("Status: IDLE");
    private final JButton toggleButton = new JButton("Start");
    private final RunepalPlugin plugin;
    private final BotConfig config;
    private final ConfigManager configManager;
    
    // Configuration controls
    private final JComboBox<FishingSpot> fishingSpotComboBox;
    private final JComboBox<FishingArea> fishingAreaComboBox;
    private final JCheckBox cookFishCheckBox;
    private final JComboBox<FishingMode> fishingModeComboBox;

    public FishingBotPanel(RunepalPlugin plugin, BotConfig config, ConfigManager configManager) {
        super();
        this.plugin = plugin;
        this.config = config;
        this.configManager = configManager;
        
        // Initialize configuration controls
        this.fishingSpotComboBox = new JComboBox<>(FishingSpot.values());
        this.fishingAreaComboBox = new JComboBox<>(FishingArea.values());
        this.cookFishCheckBox = new JCheckBox("Cook Fish");
        this.fishingModeComboBox = new JComboBox<>(FishingMode.values());
        
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
        configPanel.setBorder(BorderFactory.createTitledBorder("Fishing Configuration"));
        
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.anchor = GridBagConstraints.WEST;
        
        // Fishing Spot
        gbc.gridx = 0; gbc.gridy = 0;
        configPanel.add(new JLabel("Fishing Spot:"), gbc);
        gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL;
        configPanel.add(fishingSpotComboBox, gbc);
        
        // Fishing Area
        gbc.gridx = 0; gbc.gridy = 1; gbc.fill = GridBagConstraints.NONE;
        configPanel.add(new JLabel("Fishing Area:"), gbc);
        gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL;
        configPanel.add(fishingAreaComboBox, gbc);
        
        // Cook Fish checkbox
        gbc.gridx = 0; gbc.gridy = 2; gbc.fill = GridBagConstraints.NONE;
        gbc.gridwidth = 2;
        configPanel.add(cookFishCheckBox, gbc);
        
        // Fishing Mode
        gbc.gridx = 0; gbc.gridy = 3; gbc.gridwidth = 1;
        configPanel.add(new JLabel("Mode:"), gbc);
        gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL;
        configPanel.add(fishingModeComboBox, gbc);
        
        // Add event listeners to save changes
        fishingSpotComboBox.addActionListener(e -> {
            FishingSpot selectedSpot = (FishingSpot) fishingSpotComboBox.getSelectedItem();
            if (selectedSpot != null) {
                configManager.setConfiguration("runepal", "fishingSpot", selectedSpot);
            }
        });

        fishingAreaComboBox.addActionListener(e -> {
            FishingArea selectedArea = (FishingArea) fishingAreaComboBox.getSelectedItem();
            if (selectedArea != null) {
                configManager.setConfiguration("runepal", "fishingArea", selectedArea);
            }
        });

        cookFishCheckBox.addActionListener(e -> {
            configManager.setConfiguration("runepal", "cookFish", cookFishCheckBox.isSelected());
        });

        fishingModeComboBox.addActionListener(e -> {
            FishingMode selectedMode = (FishingMode) fishingModeComboBox.getSelectedItem();
            if (selectedMode != null) {
                configManager.setConfiguration("runepal", "fishingMode", selectedMode);
            }
        });
        
        return configPanel;
    }
    
    private void loadConfigurationValues() {
        fishingSpotComboBox.setSelectedItem(config.fishingSpot());
        fishingAreaComboBox.setSelectedItem(config.fishingArea());
        cookFishCheckBox.setSelected(config.cookFish());
        fishingModeComboBox.setSelectedItem(config.fishingMode());
    }

    public void setStatus(String status) {
        statusLabel.setText("Status: " + status);
    }

    public void setButtonText(String text) {
        toggleButton.setText(text);
    }
}