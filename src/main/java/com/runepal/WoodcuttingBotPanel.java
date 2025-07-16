package com.runepal;

import net.runelite.client.config.ConfigManager;
import net.runelite.client.ui.PluginPanel;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import java.awt.BorderLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;

public class WoodcuttingBotPanel extends PluginPanel {
    private final JLabel statusLabel = new JLabel("Status: IDLE");
    private final JButton toggleButton = new JButton("Start");
    private final RunepalPlugin plugin;
    private final BotConfig config;
    private final ConfigManager configManager;
    
    // Configuration controls
    private final JComboBox<WoodcuttingMode> woodcuttingModeComboBox;
    private final JTextField treeTypesField;

    public WoodcuttingBotPanel(RunepalPlugin plugin, BotConfig config, ConfigManager configManager) {
        super();
        this.plugin = plugin;
        this.config = config;
        this.configManager = configManager;
        
        // Initialize configuration controls
        this.woodcuttingModeComboBox = new JComboBox<>(WoodcuttingMode.values());
        this.treeTypesField = new JTextField(20);
        
        // Add tooltips to help users
        this.treeTypesField.setToolTipText("Comma-separated list of tree types (e.g., Oak, Willow, Yew)");
        
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
        final JComboBox<Banks> bankComboBox;
        JPanel configPanel = new JPanel(new GridBagLayout());
        configPanel.setBorder(BorderFactory.createTitledBorder("Woodcutting Configuration"));
        
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.anchor = GridBagConstraints.WEST;
        
        // Woodcutting Mode
        gbc.gridx = 0; gbc.gridy = 0;
        configPanel.add(new JLabel("Woodcutting Mode:"), gbc);
        gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL;
        configPanel.add(woodcuttingModeComboBox, gbc);
        
        // Tree Types
        gbc.gridx = 0; gbc.gridy = 1; gbc.fill = GridBagConstraints.NONE;
        configPanel.add(new JLabel("Tree Types:"), gbc);
        gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL;
        configPanel.add(treeTypesField, gbc);

        // Create the dropdown for bank selection
        bankComboBox = new JComboBox<>(Banks.values());
        bankComboBox.setSelectedItem(Banks.valueOf(config.woodcuttingBank()));
        
        // Dropdown panel
        JPanel dropdownPanel = new JPanel(new BorderLayout());
        dropdownPanel.setBorder(BorderFactory.createTitledBorder("Select Bank"));
        dropdownPanel.add(bankComboBox, BorderLayout.CENTER);

        gbc.gridx = 0;
        gbc.gridy = 2;
        gbc.gridwidth = 2;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        configPanel.add(dropdownPanel, gbc);
        
        // Add event listeners to save changes
        bankComboBox.addActionListener(e -> {
            Banks selectedBank = (Banks) bankComboBox.getSelectedItem();
            if (selectedBank != null) {
                configManager.setConfiguration("runepal", "woodcuttingBank", selectedBank.name());
            }
        });

        woodcuttingModeComboBox.addActionListener(e -> {
            WoodcuttingMode selectedMode = (WoodcuttingMode) woodcuttingModeComboBox.getSelectedItem();
            if (selectedMode != null) {
                configManager.setConfiguration("runepal", "woodcuttingMode", selectedMode);
            }
        });
        
        // Save changes when user presses Enter or when field loses focus
        treeTypesField.addActionListener(e -> {
            configManager.setConfiguration("runepal", "treeTypes", treeTypesField.getText());
        });
        treeTypesField.addFocusListener(new java.awt.event.FocusAdapter() {
            @Override
            public void focusLost(java.awt.event.FocusEvent e) {
                configManager.setConfiguration("runepal", "treeTypes", treeTypesField.getText());
            }
        });
        
        return configPanel;
    }
    
    private void loadConfigurationValues() {
        woodcuttingModeComboBox.setSelectedItem(config.woodcuttingMode());
        treeTypesField.setText(config.treeTypes());
    }

    public void setStatus(String status) {
        statusLabel.setText("Status: " + status);
    }

    public void setButtonText(String text) {
        toggleButton.setText(text);
    }
}