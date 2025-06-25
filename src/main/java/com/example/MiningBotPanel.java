package com.example;

import net.runelite.client.config.ConfigManager;
import net.runelite.client.ui.PluginPanel;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.GridLayout;

public class MiningBotPanel extends PluginPanel {
    private final JLabel statusLabel = new JLabel("Status: IDLE");
    private final JLabel connectionLabel = new JLabel("Connection: DISCONNECTED");
    private final JButton toggleButton = new JButton("Start");
    private final JButton connectButton = new JButton("Connect");
    private final JButton reconnectButton = new JButton("Reconnect");
    private final MiningBotPlugin plugin;

    public MiningBotPanel(MiningBotPlugin plugin, MiningBotConfig config, ConfigManager configManager) {
        super();
        this.plugin = plugin;
        setLayout(new BorderLayout());

        // Status panel
        JPanel statusPanel = new JPanel(new GridLayout(2, 1));
        statusPanel.add(statusLabel);
        statusPanel.add(connectionLabel);

        // Button panel
        JPanel buttonPanel = new JPanel(new GridLayout(3, 1));
        buttonPanel.add(connectButton);
        buttonPanel.add(reconnectButton);
        buttonPanel.add(toggleButton);

        add(statusPanel, BorderLayout.NORTH);
        add(buttonPanel, BorderLayout.CENTER);

        // Button actions
        connectButton.addActionListener(e -> {
            plugin.connectAutomation();
            updateConnectionStatus();
        });

        reconnectButton.addActionListener(e -> {
            plugin.reconnectAutomation();
            updateConnectionStatus();
        });

        toggleButton.addActionListener(e -> {
            configManager.setConfiguration("miningbot", "startBot", !config.startBot());
        });

        // Initialize connection status
        updateConnectionStatus();
    }

    public void setStatus(String status) {
        statusLabel.setText("Status: " + status);
    }

    public void setButtonText(String text) {
        toggleButton.setText(text);
    }

    public void updateConnectionStatus() {
        if (plugin.isAutomationConnected()) {
            connectionLabel.setText("Connection: CONNECTED");
            connectionLabel.setForeground(Color.GREEN);
            connectButton.setEnabled(false);
            reconnectButton.setEnabled(false);
        } else {
            connectionLabel.setText("Connection: DISCONNECTED");
            connectionLabel.setForeground(Color.RED);
            connectButton.setEnabled(true);
            reconnectButton.setEnabled(true);
        }
    }
} 