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

public class MiningBotPanel extends PluginPanel {
    private final JLabel statusLabel = new JLabel("Status: IDLE");
    private final JButton toggleButton = new JButton("Start");
    private final RunepalPlugin plugin;
    private final BotConfig config;
    private final ConfigManager configManager;
    
    // Configuration controls
    private final JComboBox<MiningMode> miningModeComboBox;
    private final JTextField rockTypesField;
    private final JTextField oreIdsField;

    public MiningBotPanel(RunepalPlugin plugin, BotConfig config, ConfigManager configManager) {
        super();
        this.plugin = plugin;
        this.config = config;
        this.configManager = configManager;
        
        // Initialize configuration controls
        this.miningModeComboBox = new JComboBox<>(MiningMode.values());
        this.rockTypesField = new JTextField(20);
        this.oreIdsField = new JTextField(20);
        
        // Add tooltips to help users
        this.rockTypesField.setToolTipText("Comma-separated rock object IDs (e.g., 11161,10943 for copper)");
        this.oreIdsField.setToolTipText("Comma-separated ore item IDs (e.g., 436 for copper ore)");
        
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
        configPanel.setBorder(BorderFactory.createTitledBorder("Mining Configuration"));
        
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.anchor = GridBagConstraints.WEST;
        
        // Mining Mode
        gbc.gridx = 0; gbc.gridy = 0;
        configPanel.add(new JLabel("Mining Mode:"), gbc);
        gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL;
        configPanel.add(miningModeComboBox, gbc);
        
        // Rock IDs
        gbc.gridx = 0; gbc.gridy = 1; gbc.fill = GridBagConstraints.NONE;
        configPanel.add(new JLabel("Rock Types:"), gbc);
        gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL;
        configPanel.add(rockTypesField, gbc);

        // Create the dropdown for bot type selection
        bankComboBox = new JComboBox<>(Banks.values());
        bankComboBox.setSelectedItem(config.miningBank());
        
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
                configManager.setConfiguration("runepal", "miningBank", selectedBank);
            }
        });

        miningModeComboBox.addActionListener(e -> {
            MiningMode selectedMode = (MiningMode) miningModeComboBox.getSelectedItem();
            if (selectedMode != null) {
                configManager.setConfiguration("runepal", "miningMode", selectedMode);
            }
        });
        
        // Save changes when user presses Enter or when field loses focus
        rockTypesField.addActionListener(e -> {
            configManager.setConfiguration("runepal", "rockTypes", rockTypesField.getText());
        });
        rockTypesField.addFocusListener(new java.awt.event.FocusAdapter() {
            @Override
            public void focusLost(java.awt.event.FocusEvent e) {
                configManager.setConfiguration("runepal", "rockTypes", rockTypesField.getText());
            }
        });
        
        oreIdsField.addActionListener(e -> {
            configManager.setConfiguration("runepal", "oreIds", oreIdsField.getText());
        });
        oreIdsField.addFocusListener(new java.awt.event.FocusAdapter() {
            @Override
            public void focusLost(java.awt.event.FocusEvent e) {
                configManager.setConfiguration("runepal", "oreIds", oreIdsField.getText());
            }
        });
        
        return configPanel;
    }
    
    private void loadConfigurationValues() {
        miningModeComboBox.setSelectedItem(config.miningMode());
        rockTypesField.setText(config.rockTypes());
//        oreIdsField.setText(config.oreIds());
    }

    public void setStatus(String status) {
        statusLabel.setText("Status: " + status);
    }

    public void setButtonText(String text) {
        toggleButton.setText(text);
    }


} 