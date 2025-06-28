package com.example;

import net.runelite.client.config.ConfigManager;
import net.runelite.client.ui.PluginPanel;
import javax.swing.*;
import java.awt.*;

public class CombatBotPanel extends PluginPanel {
    private final MiningBotPlugin plugin;
    private final BotConfig config;
    private final ConfigManager configManager;
    
    private JButton startStopButton;
    private JLabel statusLabel;
    private JTextField npcNamesField;
    private JSpinner healthPercentSpinner;
    
    private boolean isRunning = false;

    public CombatBotPanel(MiningBotPlugin plugin, BotConfig config, ConfigManager configManager) {
        super();
        this.plugin = plugin;
        this.config = config;
        this.configManager = configManager;
        
        setLayout(new BorderLayout());
        
        // Create main content panel
        JPanel contentPanel = new JPanel();
        contentPanel.setLayout(new BoxLayout(contentPanel, BoxLayout.Y_AXIS));
        contentPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        
        // Add configuration section
        contentPanel.add(createConfigurationPanel());
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
    
    private JPanel createConfigurationPanel() {
        JPanel configPanel = new JPanel(new GridBagLayout());
        configPanel.setBorder(BorderFactory.createTitledBorder("Combat Configuration"));
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
        
        // Save button
        gbc.gridx = 0; gbc.gridy = 2; gbc.gridwidth = 2; gbc.fill = GridBagConstraints.HORIZONTAL;
        JButton saveButton = new JButton("Save Configuration");
        saveButton.addActionListener(e -> saveConfiguration());
        configPanel.add(saveButton, gbc);
        
        return configPanel;
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
        npcNamesField.setText(config.combatNpcNames());
        healthPercentSpinner.setValue(config.combatEatAtHealthPercent());
    }
    
    private void saveConfiguration() {
        try {
            // Save NPC names
            String npcNames = npcNamesField.getText().trim();
            configManager.setConfiguration("generalbot", "combatNpcNames", npcNames);
            
            // Save health percentage
            int healthPercent = (Integer) healthPercentSpinner.getValue();
            configManager.setConfiguration("generalbot", "combatEatAtHealthPercent", healthPercent);
            
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
        if (!plugin.isAutomationConnected()) {
            JOptionPane.showMessageDialog(this, 
                "Please connect to the automation server first!", 
                "Connection Required", 
                JOptionPane.WARNING_MESSAGE);
            return;
        }
        
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
        configManager.setConfiguration("generalbot", "botType", BotType.COMBAT_BOT);
        configManager.setConfiguration("generalbot", "startBot", true);
        
        isRunning = true;
        startStopButton.setText("Stop Combat Bot");
        statusLabel.setText("Combat bot is running...");
        statusLabel.setForeground(Color.GREEN);
        
        // Disable configuration controls while running
        npcNamesField.setEnabled(false);
        healthPercentSpinner.setEnabled(false);
    }
    
    private void stopBot() {
        configManager.setConfiguration("generalbot", "startBot", false);
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