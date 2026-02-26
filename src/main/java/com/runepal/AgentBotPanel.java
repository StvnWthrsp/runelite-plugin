package com.runepal;

import com.google.gson.JsonObject;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
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
import javax.swing.ScrollPaneConstants;
import javax.swing.JTextArea;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import java.awt.BorderLayout;
import java.awt.Dimension;
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
    private final JLabel debugLabel = new JLabel("Debug: (none)");
    private final JLabel skillLabel = new JLabel("Template: (none)");
    private final JLabel scriptLabel = new JLabel("Script: (none)");
    private final JLabel pendingScriptLabel = new JLabel("Pending script: (none)");
    private final JLabel memoryLabel = new JLabel("Memory: (none)");

    private final JButton planNowButton = new JButton("Plan Now");
    private final JButton stopButton = new JButton("Stop Automation");
    private final JButton setGoalButton = new JButton("Set Goal");
    private final JButton approveScriptButton = new JButton("Approve & Run Script");
    private final JButton debugNowButton = new JButton("Debug Now");
    private final JButton wikiTestButton = new JButton("Wiki Test");
    private final JButton snapshotToolButton = new JButton("Tool: Snapshot");
    private final JButton captureToolButton = new JButton("Tool: Capture");

    private final JCheckBox enableAgentCheckBox = new JCheckBox("Enable Local Agent", false);
    private final JCheckBox enableLlmCheckBox = new JCheckBox("Enable LLM Planning", false);
    private final JCheckBox requireApprovalCheckBox = new JCheckBox("Require Script Approval", true);

    private final JTextArea answerArea = new JTextArea(4, 24);
    private final JTextArea traceArea = new JTextArea(8, 24);
    private final JTextArea pendingScriptArea = new JTextArea(8, 24);
    private final JTextArea toolResultArea = new JTextArea(8, 24);

    private Timer refreshTimer;

    public AgentBotPanel(RunepalPlugin plugin, BotConfig config, ConfigManager configManager) {
        super();
        this.plugin = plugin;
        this.config = config;
        this.configManager = configManager;

        setLayout(new BorderLayout());
        JScrollPane container = new JScrollPane(buildContent());
        container.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        add(container, BorderLayout.CENTER);

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

        JPanel goalButtons = new JPanel();
        goalButtons.setLayout(new BoxLayout(goalButtons, BoxLayout.Y_AXIS));
        setGoalButton.setPreferredSize(new Dimension(220, 32));
        planNowButton.setPreferredSize(new Dimension(220, 32));
        setGoalButton.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32));
        planNowButton.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32));
        setGoalButton.setAlignmentX(CENTER_ALIGNMENT);
        planNowButton.setAlignmentX(CENTER_ALIGNMENT);
        goalButtons.add(setGoalButton);
        goalButtons.add(new JLabel(" "));
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
        statusPanel.add(debugLabel, gbc);
        gbc.gridy++;
        statusPanel.add(decisionLabel, gbc);
        gbc.gridy++;
        statusPanel.add(skillLabel, gbc);
        gbc.gridy++;
        statusPanel.add(scriptLabel, gbc);
        gbc.gridy++;
        statusPanel.add(pendingScriptLabel, gbc);
        gbc.gridy++;
        statusPanel.add(memoryLabel, gbc);

        JPanel controls = new JPanel();
        controls.setLayout(new BoxLayout(controls, BoxLayout.Y_AXIS));
        controls.setBorder(BorderFactory.createTitledBorder("Control"));
        stopButton.setPreferredSize(new Dimension(240, 36));
        debugNowButton.setPreferredSize(new Dimension(240, 36));
        approveScriptButton.setPreferredSize(new Dimension(240, 36));
        stopButton.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));
        debugNowButton.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));
        approveScriptButton.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));
        stopButton.setAlignmentX(CENTER_ALIGNMENT);
        debugNowButton.setAlignmentX(CENTER_ALIGNMENT);
        approveScriptButton.setAlignmentX(CENTER_ALIGNMENT);
        approveScriptButton.setEnabled(false);
        controls.add(approveScriptButton);
        controls.add(new JLabel(" "));
        controls.add(debugNowButton);
        controls.add(new JLabel(" "));
        controls.add(stopButton);

        JPanel stacked = new JPanel();
        stacked.setLayout(new BorderLayout());
        stacked.add(toggles, BorderLayout.NORTH);
        stacked.add(goalPanel, BorderLayout.CENTER);
        stacked.add(controls, BorderLayout.SOUTH);

        JPanel observabilityPanel = new JPanel(new GridBagLayout());
        observabilityPanel.setBorder(BorderFactory.createTitledBorder("Agent Observability"));
        GridBagConstraints obs = new GridBagConstraints();
        obs.gridx = 0;
        obs.gridy = 0;
        obs.weightx = 1.0;
        obs.fill = GridBagConstraints.HORIZONTAL;
        obs.insets = new Insets(4, 6, 4, 6);

        answerArea.setEditable(false);
        answerArea.setLineWrap(true);
        answerArea.setWrapStyleWord(true);
        observabilityPanel.add(new JLabel("Answer"), obs);
        obs.gridy++;
        observabilityPanel.add(new JScrollPane(answerArea), obs);

        traceArea.setEditable(false);
        traceArea.setLineWrap(true);
        traceArea.setWrapStyleWord(true);
        observabilityPanel.add(new JLabel("Tool / Trace Log"), obs);
        obs.gridy++;
        observabilityPanel.add(new JScrollPane(traceArea), obs);

        pendingScriptArea.setEditable(false);
        pendingScriptArea.setLineWrap(true);
        pendingScriptArea.setWrapStyleWord(true);
        observabilityPanel.add(new JLabel("Pending Script JSON"), obs);
        obs.gridy++;
        observabilityPanel.add(new JScrollPane(pendingScriptArea), obs);

        toolResultArea.setEditable(false);
        toolResultArea.setLineWrap(true);
        toolResultArea.setWrapStyleWord(true);
        observabilityPanel.add(new JLabel("Tool Result"), obs);
        obs.gridy++;
        observabilityPanel.add(new JScrollPane(toolResultArea), obs);

        JPanel toolButtons = new JPanel();
        toolButtons.setLayout(new BoxLayout(toolButtons, BoxLayout.Y_AXIS));
        wikiTestButton.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32));
        snapshotToolButton.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32));
        captureToolButton.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32));
        wikiTestButton.setAlignmentX(CENTER_ALIGNMENT);
        snapshotToolButton.setAlignmentX(CENTER_ALIGNMENT);
        captureToolButton.setAlignmentX(CENTER_ALIGNMENT);
        toolButtons.add(wikiTestButton);
        toolButtons.add(new JLabel(" "));
        toolButtons.add(snapshotToolButton);
        toolButtons.add(new JLabel(" "));
        toolButtons.add(captureToolButton);
        obs.gridy++;
        observabilityPanel.add(toolButtons, obs);

        root.add(stacked, BorderLayout.NORTH);
        root.add(statusPanel, BorderLayout.CENTER);
        root.add(observabilityPanel, BorderLayout.SOUTH);
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

        debugNowButton.addActionListener(e -> {
            AgentService agentService = plugin.getAgentService();
            if (agentService == null) {
                return;
            }

            boolean started = agentService.triggerDebugFromUi();
            if (!started) {
                JOptionPane.showMessageDialog(this,
                        "Debug could not start (bot may be idle or planner busy)",
                        "Agent",
                        JOptionPane.INFORMATION_MESSAGE);
            }
            refreshFromRuntime();
        });

        wikiTestButton.addActionListener(e -> {
            AgentService agentService = plugin.getAgentService();
            if (agentService == null) {
                return;
            }
            JsonObject result = agentService.runWikiSmokeTest();
            toolResultArea.setText(result.toString());
            refreshFromRuntime();
        });

        snapshotToolButton.addActionListener(e -> {
            AgentService agentService = plugin.getAgentService();
            if (agentService == null) {
                return;
            }
            JsonObject result = agentService.runToolFromUi("game.snapshot", new JsonObject());
            toolResultArea.setText(result.toString());
            refreshFromRuntime();
        });

        captureToolButton.addActionListener(e -> {
            AgentService agentService = plugin.getAgentService();
            if (agentService == null) {
                return;
            }
            JsonObject args = new JsonObject();
            args.addProperty("maxWidth", 640);
            JsonObject result = agentService.runToolFromUi("vision.capture", args);
            toolResultArea.setText(result.toString());
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
            String debugReason = agentService.getLastDebugReason();
            debugLabel.setText("Debug: " + (debugReason == null || debugReason.isEmpty() ? "(none)" : ellipsize(debugReason, 72)));

            JsonObject decision = agentService.getDecisionSnapshot();
            if (decision != null) {
                String type = decision.has("decisionType") ? decision.get("decisionType").getAsString() : "";
                String reason = decision.has("reason") ? decision.get("reason").getAsString() : "";
                decisionLabel.setText("Decision: " + type + (reason.isEmpty() ? "" : " - " + ellipsize(reason, 72)));
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
                pendingScriptLabel.setText("Pending script: " + (name.isEmpty() ? "(unnamed)" : ellipsize(name, 72)));
                pendingScriptArea.setText(pending.has("script") ? pending.get("script").toString() : "");
            } else {
                pendingScriptLabel.setText("Pending script: (none)");
                pendingScriptArea.setText("");
            }
            approveScriptButton.setEnabled(hasPending);
            debugNowButton.setEnabled(config.startBot());

            String answer = agentService.getLatestAnswer();
            answerArea.setText(answer == null ? "" : answer);

            JsonObject trace = agentService.getTraceSnapshot(20);
            traceArea.setText(formatTrace(trace));

            String memoryTitle = agentService.getLatestMemoryTitle();
            memoryLabel.setText("Memory: " + (memoryTitle == null || memoryTitle.isEmpty() ? "(none)" : ellipsize(memoryTitle, 72)));

            if (config.startBot()) {
                statusLabel.setText("Automation running");
                statusLabel.setForeground(java.awt.Color.GREEN);
            } else {
                statusLabel.setText("Automation idle");
                statusLabel.setForeground(java.awt.Color.LIGHT_GRAY);
            }
        });
    }

    private String formatTrace(JsonObject trace) {
        if (trace == null || !trace.has("events") || !trace.get("events").isJsonArray()) {
            return "";
        }

        JsonArray events = trace.getAsJsonArray("events");
        StringBuilder builder = new StringBuilder();
        for (JsonElement eventElement : events) {
            if (!eventElement.isJsonObject()) {
                continue;
            }
            JsonObject event = eventElement.getAsJsonObject();
            String timestamp = event.has("timestamp") ? event.get("timestamp").getAsString() : "";
            String category = event.has("category") ? event.get("category").getAsString() : "event";
            String message = event.has("message") ? event.get("message").getAsString() : "";
            builder.append('[')
                    .append(timestamp)
                    .append("] ")
                    .append(category)
                    .append(": ")
                    .append(message)
                    .append('\n');
        }
        return builder.toString();
    }

    private String ellipsize(String value, int maxLength) {
        if (value == null) {
            return "";
        }
        int safeMax = Math.max(8, maxLength);
        if (value.length() <= safeMax) {
            return value;
        }
        return value.substring(0, safeMax - 3) + "...";
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
