package com.example;

import net.runelite.client.config.ConfigManager;
import net.runelite.client.ui.PluginPanel;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.BorderLayout;

public class MiningBotPanel extends PluginPanel {
    private final JLabel statusLabel = new JLabel("Status: IDLE");
    private final JButton toggleButton = new JButton("Start");

    public MiningBotPanel(MiningBotPlugin plugin, MiningBotConfig config, ConfigManager configManager) {
        super();
        setLayout(new BorderLayout());

        JPanel topPanel = new JPanel();
        topPanel.setLayout(new BorderLayout());
        topPanel.add(statusLabel, BorderLayout.CENTER);
        topPanel.add(toggleButton, BorderLayout.SOUTH);

        add(topPanel, BorderLayout.NORTH);

        toggleButton.addActionListener(e -> {
            configManager.setConfiguration("miningbot", "startBot", !config.startBot());
        });
    }

    public void setStatus(String status) {
        statusLabel.setText("Status: " + status);
    }

    public void setButtonText(String text) {
        toggleButton.setText(text);
    }
} 