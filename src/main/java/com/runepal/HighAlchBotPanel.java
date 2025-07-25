package com.runepal;

import net.runelite.client.config.ConfigManager;
import net.runelite.client.ui.PluginPanel;
import javax.swing.*;
import java.awt.*;

public class HighAlchBotPanel extends PluginPanel {
    private final JLabel statusLabel = new JLabel("IDLE");
    private final JButton toggleButton = new JButton("Start");

    public HighAlchBotPanel(RunepalPlugin plugin, BotConfig config, ConfigManager configManager) {
        super();

        setLayout(new BorderLayout());

        // Create main content panel with vertical layout
        JPanel contentPanel = new JPanel();
        contentPanel.setLayout(new BoxLayout(contentPanel, BoxLayout.Y_AXIS));

        // Add control section
        contentPanel.add(createControlPanel());
        contentPanel.add(Box.createVerticalStrut(10));

        // Add status section
        contentPanel.add(createStatusPanel());

        add(contentPanel, BorderLayout.NORTH);

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

    public void setStatus(String status) {
        statusLabel.setText(status);
    }

    public void setButtonText(String text) {
        toggleButton.setText(text);
    }
}