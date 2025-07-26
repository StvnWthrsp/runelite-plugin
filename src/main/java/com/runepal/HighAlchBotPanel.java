package com.runepal;

import net.runelite.client.config.ConfigManager;
import net.runelite.client.ui.PluginPanel;
import javax.swing.*;
import java.awt.*;

public class HighAlchBotPanel extends PluginPanel {
    private final JLabel statusLabel = new JLabel("IDLE");
    private final JButton toggleButton = new JButton("Start");
    private final JSpinner highAlchSpinner;
    private final ConfigManager configManager;
    private final BotConfig config;

    public HighAlchBotPanel(RunepalPlugin plugin, BotConfig config, ConfigManager configManager) {
        super();
        this.configManager = configManager;
        this.config = config;

        this.highAlchSpinner = new JSpinner(new SpinnerNumberModel(config.highAlchItemId(), 0, Integer.MAX_VALUE, 1));

        setLayout(new BorderLayout());

        // Create main content panel with vertical layout
        JPanel contentPanel = new JPanel();
        contentPanel.setLayout(new BoxLayout(contentPanel, BoxLayout.Y_AXIS));

        // Add control section
        contentPanel.add(createBasicConfigurationPanel());
        contentPanel.add(Box.createVerticalStrut(10));
        contentPanel.add(createControlPanel());
        contentPanel.add(Box.createVerticalStrut(10));

        // Add status section
        contentPanel.add(createStatusPanel());

        add(contentPanel, BorderLayout.NORTH);

        loadConfigurationValues();

        // Button actions
        toggleButton.addActionListener(e -> {
            configManager.setConfiguration("runepal", "startBot", !config.startBot());
        });
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

    private JPanel createBasicConfigurationPanel() {
        JPanel configPanel = new JPanel(new GridBagLayout());
//        configPanel.setBorder(BorderFactory.createTitledBorder("Basic Sand Crab Configuration"));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(3, 5, 3, 5);

        // Crab Count label
        gbc.gridx = 0; gbc.gridy = 0; gbc.anchor = GridBagConstraints.WEST;
        configPanel.add(new JLabel("Item ID:"), gbc);

        // Crab Count spinner
        gbc.gridx = 1; gbc.gridy = 0; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1.0;
        highAlchSpinner.setToolTipText("ID of Item to cast High Level Alchemy on");
        configPanel.add(highAlchSpinner, gbc);

        // Add event listeners
        highAlchSpinner.addChangeListener(e -> saveConfiguration());

        return configPanel;
    }

    private void saveConfiguration() {
        configManager.setConfiguration("runepal", "highAlchItemId", (Integer) highAlchSpinner.getValue());
    }

    private void loadConfigurationValues() {
        highAlchSpinner.setValue(config.highAlchItemId());
    }

    public void setStatus(String status) {
        statusLabel.setText(status);
    }

    public void setButtonText(String text) {
        toggleButton.setText(text);
    }
}