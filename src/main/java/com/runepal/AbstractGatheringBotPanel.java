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

public abstract class AbstractGatheringBotPanel<T extends Enum<T>> extends PluginPanel implements BotStatusPanel {
    protected final RunepalPlugin plugin;
    protected final BotConfig config;
    protected final ConfigManager configManager;

    private final JLabel statusLabel = new JLabel("Status: IDLE");
    private final JButton toggleButton = new JButton("Start");
    private final JComboBox<T> modeComboBox;
    private final JTextField resourceTypesField = new JTextField(20);
    private final JComboBox<Banks> bankComboBox = new JComboBox<>(Banks.values());

    protected AbstractGatheringBotPanel(RunepalPlugin plugin,
                                        BotConfig config,
                                        ConfigManager configManager,
                                        T[] modeValues) {
        this.plugin = plugin;
        this.config = config;
        this.configManager = configManager;
        this.modeComboBox = new JComboBox<>(modeValues);

        setLayout(new BorderLayout());

        JPanel configPanel = createConfigurationPanel();
        JPanel statusPanel = new JPanel(new BorderLayout());
        statusPanel.setBorder(BorderFactory.createTitledBorder("Status"));
        statusPanel.add(statusLabel, BorderLayout.CENTER);

        JPanel buttonPanel = new JPanel(new BorderLayout());
        buttonPanel.add(toggleButton, BorderLayout.CENTER);

        add(configPanel, BorderLayout.NORTH);
        add(statusPanel, BorderLayout.CENTER);
        add(buttonPanel, BorderLayout.SOUTH);

        loadConfigurationValues();

        toggleButton.addActionListener(e -> configManager.setConfiguration("runepal", "startBot", !config.startBot()));
    }

    private JPanel createConfigurationPanel() {
        JPanel configPanel = new JPanel(new GridBagLayout());
        configPanel.setBorder(BorderFactory.createTitledBorder(getConfigurationTitle()));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.anchor = GridBagConstraints.WEST;

        gbc.gridx = 0;
        gbc.gridy = 0;
        configPanel.add(new JLabel(getModeLabel()), gbc);
        gbc.gridx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        configPanel.add(modeComboBox, gbc);

        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.fill = GridBagConstraints.NONE;
        configPanel.add(new JLabel(getResourceTypesLabel()), gbc);
        gbc.gridx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        configPanel.add(resourceTypesField, gbc);

        JPanel dropdownPanel = new JPanel(new BorderLayout());
        dropdownPanel.setBorder(BorderFactory.createTitledBorder("Select Bank"));
        dropdownPanel.add(bankComboBox, BorderLayout.CENTER);

        gbc.gridx = 0;
        gbc.gridy = 2;
        gbc.gridwidth = 2;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        configPanel.add(dropdownPanel, gbc);

        modeComboBox.addActionListener(e -> {
            int selectedIndex = modeComboBox.getSelectedIndex();
            if (selectedIndex >= 0) {
                T selectedMode = modeComboBox.getItemAt(selectedIndex);
                saveMode(selectedMode);
            }
        });

        resourceTypesField.addActionListener(e -> saveResourceTypes(resourceTypesField.getText()));
        resourceTypesField.addFocusListener(new java.awt.event.FocusAdapter() {
            @Override
            public void focusLost(java.awt.event.FocusEvent e) {
                saveResourceTypes(resourceTypesField.getText());
            }
        });

        bankComboBox.addActionListener(e -> {
            Banks selectedBank = (Banks) bankComboBox.getSelectedItem();
            if (selectedBank != null) {
                saveBank(selectedBank);
            }
        });

        return configPanel;
    }

    private void loadConfigurationValues() {
        modeComboBox.setSelectedItem(getConfiguredMode());
        resourceTypesField.setText(getConfiguredResourceTypes());
        try {
            bankComboBox.setSelectedItem(Banks.valueOf(getConfiguredBank()));
        } catch (IllegalArgumentException ignored) {
            bankComboBox.setSelectedItem(Banks.VARROCK_EAST);
        }
    }

    @Override
    public void setStatus(String status) {
        statusLabel.setText("Status: " + status);
    }

    @Override
    public void setButtonText(String text) {
        toggleButton.setText(text);
    }

    protected abstract String getConfigurationTitle();

    protected abstract String getModeLabel();

    protected abstract String getResourceTypesLabel();

    protected abstract T getConfiguredMode();

    protected abstract String getConfiguredResourceTypes();

    protected abstract String getConfiguredBank();

    protected abstract void saveMode(T mode);

    protected abstract void saveResourceTypes(String value);

    protected abstract void saveBank(Banks bank);
}
