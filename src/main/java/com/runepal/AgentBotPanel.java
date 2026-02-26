package com.runepal;

import com.google.gson.JsonObject;
import com.runepal.agent.AgentService;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.ui.PluginPanel;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.BoxLayout;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.HierarchyEvent;

public class AgentBotPanel extends PluginPanel implements BotStatusPanel {
    private final RunepalPlugin plugin;
    private final BotConfig config;
    private final ConfigManager configManager;

    private final JTextArea goalArea = new JTextArea(3, 24);
    private final JLabel statusLabel = new JLabel("Agent idle", SwingConstants.CENTER);
    private final JLabel decisionLabel = new JLabel("Decision: (none)");
    private final JLabel planningLabel = new JLabel("Planning: false");
    private final JLabel skillLabel = new JLabel("Template: (none)");
    private final JLabel scriptLabel = new JLabel("Script: (none)");
    private final JLabel pendingScriptLabel = new JLabel("Pending script: (none)");

    private final JButton planNowButton = new JButton("Plan Now");
    private final JButton stopButton = new JButton("Stop Automation");
    private final JButton setGoalButton = new JButton("Set Goal");
    private final JButton approveScriptButton = new JButton("Approve & Run Script");

    private final JCheckBox enableAgentCheckBox = new JCheckBox("Enable Local Agent", false);
    private final JCheckBox enableLlmCheckBox = new JCheckBox("Enable LLM Planning", false);
    private final JCheckBox requireApprovalCheckBox = new JCheckBox("Require Script Approval", true);

    private Timer refreshTimer;

    public AgentBotPanel(RunepalPlugin plugin, BotConfig config, ConfigManager configManager) {
        super();
        this.plugin = plugin;
        this.config = config;
        this.configManager = configManager;

        setLayout(new BorderLayout());
        add(buildContent(), BorderLayout.NORTH);

        bindActions();
        refreshFromRuntime();
        startRefreshTimer();

        addHierarchyListener(e -> {
            if ((e.getChangeFlags() & HierarchyEvent.DISPLAYABILITY_CHANGED) == 0) {
                return;
            }
            if (!isDisplayable() && refreshTimer != null) {
                refreshTimer.stop();
            }
        });
    }

    private JPanel buildContent() {
        JPanel root = new JPanel();
        root.setLayout(new BorderLayout());

        JPanel goalPanel = new JPanel(new BorderLayout());
        goalPanel.setBorder(BorderFactory.createTitledBorder("Agent Goal"));
        goalArea.setLineWrap(true);
        goalArea.setWrapStyleWord(true);
        goalPanel.add(new JScrollPane(goalArea), BorderLayout.CENTER);

        JPanel goalButtons = new JPanel(new FlowLayout(FlowLayout.CENTER));
        setGoalButton.setPreferredSize(new Dimension(120, 32));
        planNowButton.setPreferredSize(new Dimension(120, 32));
        goalButtons.add(setGoalButton);
        goalButtons.add(planNowButton);
        goalPanel.add(goalButtons, BorderLayout.SOUTH);

        JPanel toggles = new JPanel();
        toggles.setLayout(new BoxLayout(toggles, BoxLayout.Y_AXIS));
        toggles.setBorder(BorderFactory.createTitledBorder("Agent Settings"));
        toggles.add(enableAgentCheckBox);
        toggles.add(enableLlmCheckBox);
        toggles.add(requireApprovalCheckBox);

        JPanel statusPanel = new JPanel(new GridBagLayout());
        statusPanel.setBorder(BorderFactory.createTitledBorder("Status"));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(4, 6, 4, 6);
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;

        statusPanel.add(statusLabel, gbc);
        gbc.gridy++;
        statusPanel.add(planningLabel, gbc);
        gbc.gridy++;
        statusPanel.add(decisionLabel, gbc);
        gbc.gridy++;
        statusPanel.add(skillLabel, gbc);
        gbc.gridy++;
        statusPanel.add(scriptLabel, gbc);
        gbc.gridy++;
        statusPanel.add(pendingScriptLabel, gbc);

        JPanel controls = new JPanel(new FlowLayout(FlowLayout.CENTER));
        controls.setBorder(BorderFactory.createTitledBorder("Control"));
        stopButton.setPreferredSize(new Dimension(240, 36));
        approveScriptButton.setPreferredSize(new Dimension(240, 36));
        approveScriptButton.setEnabled(false);
        controls.add(approveScriptButton);
        controls.add(stopButton);

        JPanel stacked = new JPanel();
        stacked.setLayout(new BorderLayout());
        stacked.add(toggles, BorderLayout.NORTH);
        stacked.add(goalPanel, BorderLayout.CENTER);
        stacked.add(controls, BorderLayout.SOUTH);

        root.add(stacked, BorderLayout.NORTH);
        root.add(statusPanel, BorderLayout.CENTER);
        return root;
    }

    private void bindActions() {
        enableAgentCheckBox.addActionListener(e -> {
            configManager.setConfiguration("runepal", "agentEnable", enableAgentCheckBox.isSelected());
        });

        enableLlmCheckBox.addActionListener(e -> {
            configManager.setConfiguration("runepal", "llmEnable", enableLlmCheckBox.isSelected());
        });

        requireApprovalCheckBox.addActionListener(e -> {
            configManager.setConfiguration("runepal", "llmRequireScriptApproval", requireApprovalCheckBox.isSelected());
        });

        setGoalButton.addActionListener(e -> {
            AgentService agentService = plugin.getAgentService();
            if (agentService == null) {
                JOptionPane.showMessageDialog(this,
                        "Agent service is not initialized yet",
                        "Agent",
                        JOptionPane.WARNING_MESSAGE);
                return;
            }

            String goal = goalArea.getText().trim();
            if (goal.isEmpty()) {
                JOptionPane.showMessageDialog(this,
                        "Goal cannot be empty",
                        "Agent",
                        JOptionPane.WARNING_MESSAGE);
                return;
            }

            agentService.setGoalFromUi(goal);
            refreshFromRuntime();
        });

        planNowButton.addActionListener(e -> {
            AgentService agentService = plugin.getAgentService();
            if (agentService == null) {
                JOptionPane.showMessageDialog(this,
                        "Agent service is not initialized yet",
                        "Agent",
                        JOptionPane.WARNING_MESSAGE);
                return;
            }

            boolean started = agentService.planNowFromUi();
            if (!started && agentService.isPlanning()) {
                JOptionPane.showMessageDialog(this,
                        "Planner is already running",
                        "Agent",
                        JOptionPane.INFORMATION_MESSAGE);
            }
            refreshFromRuntime();
        });

        stopButton.addActionListener(e -> {
            AgentService agentService = plugin.getAgentService();
            if (agentService != null) {
                agentService.stopAllFromUi();
            }

            // Do not clear tasks on the Swing thread; let the next GameTick stop safely.
            configManager.setConfiguration("runepal", "startBot", false);
            refreshFromRuntime();
        });

        approveScriptButton.addActionListener(e -> {
            AgentService agentService = plugin.getAgentService();
            if (agentService == null) {
                JOptionPane.showMessageDialog(this,
                        "Agent service is not initialized yet",
                        "Agent",
                        JOptionPane.WARNING_MESSAGE);
                return;
            }

            boolean started = agentService.approvePendingScriptFromUi();
            if (!started) {
                JOptionPane.showMessageDialog(this,
                        "No pending script to approve",
                        "Agent",
                        JOptionPane.INFORMATION_MESSAGE);
            }
            refreshFromRuntime();
        });
    }

    private void startRefreshTimer() {
        refreshTimer = new Timer(1000, e -> refreshFromRuntime());
        refreshTimer.setRepeats(true);
        refreshTimer.start();
    }

    private void refreshFromRuntime() {
        SwingUtilities.invokeLater(() -> {
            enableAgentCheckBox.setSelected(config.agentEnable());
            enableLlmCheckBox.setSelected(config.llmEnable());
            requireApprovalCheckBox.setSelected(config.llmRequireScriptApproval());

            AgentService agentService = plugin.getAgentService();
            if (agentService == null) {
                statusLabel.setText("Agent service unavailable");
                statusLabel.setForeground(java.awt.Color.RED);
                return;
            }

            JsonObject goal = agentService.getGoalSnapshot();
            if (goal != null && goal.has("goal")) {
                String text = goal.get("goal").getAsString();
                if (!text.equals(goalArea.getText().trim()) && !goalArea.hasFocus()) {
                    goalArea.setText(text);
                }
            }

            planningLabel.setText("Planning: " + agentService.isPlanning());

            JsonObject decision = agentService.getDecisionSnapshot();
            if (decision != null) {
                String type = decision.has("decisionType") ? decision.get("decisionType").getAsString() : "";
                String reason = decision.has("reason") ? decision.get("reason").getAsString() : "";
                decisionLabel.setText("Decision: " + type + (reason.isEmpty() ? "" : " - " + reason));
            }

            JsonObject skill = agentService.getSkillStatusSnapshot();
            if (skill != null) {
                String activeSkill = skill.has("activeSkill") ? skill.get("activeSkill").getAsString() : "";
                skillLabel.setText("Template: " + (activeSkill.isEmpty() ? "(none)" : activeSkill));
            }

            JsonObject script = agentService.getScriptStatusSnapshot();
            if (script != null) {
                String activeScript = script.has("activeScript") ? script.get("activeScript").getAsString() : "";
                scriptLabel.setText("Script: " + (activeScript.isEmpty() ? "(none)" : activeScript));
            }

            JsonObject pending = agentService.getPendingScriptSnapshot();
            boolean hasPending = pending != null && pending.has("present") && pending.get("present").getAsBoolean();
            if (hasPending) {
                String name = pending.has("name") ? pending.get("name").getAsString() : "";
                pendingScriptLabel.setText("Pending script: " + (name.isEmpty() ? "(unnamed)" : name));
            } else {
                pendingScriptLabel.setText("Pending script: (none)");
            }
            approveScriptButton.setEnabled(hasPending);

            if (config.startBot()) {
                statusLabel.setText("Automation running");
                statusLabel.setForeground(java.awt.Color.GREEN);
            } else {
                statusLabel.setText("Automation idle");
                statusLabel.setForeground(java.awt.Color.LIGHT_GRAY);
            }
        });
    }

    @Override
    public void setStatus(String status) {
        // Keep the existing panel integration: show current state.
        if (status != null && !status.trim().isEmpty()) {
            SwingUtilities.invokeLater(() -> statusLabel.setText(status));
        }
    }

    @Override
    public void setButtonText(String text) {
        // Bot runtime drives this (Start/Stop). Use it to keep the stop button accurate.
        if (text == null) {
            return;
        }

        SwingUtilities.invokeLater(() -> {
            if (text.toLowerCase().contains("stop")) {
                stopButton.setText("Stop Automation");
            } else {
                stopButton.setText("Stop Automation");
            }
        });
    }
}
