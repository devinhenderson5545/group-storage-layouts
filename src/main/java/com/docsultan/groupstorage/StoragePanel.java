package com.docsultan.groupstorage;

import java.awt.*;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import net.runelite.client.ui.PluginPanel;

final class StoragePanel extends PluginPanel
{
    private final JLabel status=new JLabel();
    private final JTextArea share=new JTextArea(5,18);
    private final JCheckBox arrange=new JCheckBox("Arrange layout (drag to swap)");
    private final JCheckBox syncEnabled=new JCheckBox("Automatically sync with party");
    private final JTextField host=new JTextField("",16);
    private final JLabel syncStatus=new JLabel();
    private String lastSync="";
    StoragePanel(GroupStoragePlugin plugin)
    {
        setLayout(new BoxLayout(this,BoxLayout.Y_AXIS));
        setBorder(new EmptyBorder(10,10,10,10));
        add(new JLabel("Group Storage Layouts"));
        add(Box.createVerticalStrut(12));
        add(status);
        add(Box.createVerticalStrut(12));
        JButton capture=new JButton("Capture current arrangement");
        capture.addActionListener(e -> plugin.capture()); add(capture);
        JButton addItems=new JButton("Add new storage items");
        addItems.setToolTipText("Append items currently in storage without replacing your saved layout. Followers ask the host to save shared positions.");
        addItems.addActionListener(e -> plugin.addNewStorageItems()); add(addItems);
        JCheckBox enabled=new JCheckBox("Show saved layout",true);
        enabled.addActionListener(e -> plugin.setEnabledView(enabled.isSelected())); add(enabled);
        arrange.addActionListener(e -> plugin.setArrange(arrange.isSelected())); add(arrange);
        add(new JLabel("<html><br>New items start on the next row below your last item or placeholder. Trailing empty rows are skipped.<br>Use <b>Add new storage items</b> to rescan without replacing your layout.<br><br>Arrange: drag items or faded placeholders.<br>Occupied slots swap; empty slots keep gaps.<br><br>Turn Arrange off to withdraw/deposit.<br>Use the game's Save button as usual.<br><br></html>"));
        add(new JLabel("<html><b>Shared layout setup</b><br>1. Choose one host. Their saved layout is used.<br>2. Host only: capture storage once, or import the layout you want to share. Skip this if already saved.<br>3. Everyone: join the same private RuneLite Party (GIM membership alone is not enough).<br>4. Everyone: enable sync below, enter that host's character name, then Apply.<br><br></html>"));
        add(syncEnabled); add(new JLabel("Same host name on everyone's client:")); add(host);
        JButton connect=new JButton("Apply sync settings");
        connect.addActionListener(e -> plugin.configureSync(syncEnabled.isSelected(),host.getText())); add(connect);
        add(syncStatus);
        add(new JLabel("<html><br>Host should see <b>Hosting shared layout</b>.<br>Everyone else should see <b>Synced with...</b><br>Then everyone can enable Arrange and drag.<br>Host edits outside the party sync on rejoining.<br>Capturing again replaces the saved arrangement.<br><br></html>"));
        JButton backup=new JButton("Restore pre-sync layout (sync off)");
        backup.addActionListener(e -> plugin.restoreBeforeSync()); add(backup);
        JButton export=new JButton("Export layout into text box");
        export.addActionListener(e -> plugin.exportLayout(text -> share.setText(text))); add(export);
        share.setLineWrap(true); share.setWrapStyleWord(true);
        add(new JScrollPane(share));
        JButton importButton=new JButton("Import layout from text box");
        importButton.addActionListener(e -> plugin.importLayout(share.getText())); add(importButton);
        add(new JLabel("<html><br>Manual export/import is still available.<br>While synced, only the host can import<br>or capture a replacement layout.<br><br>Different charges/doses use separate slots.<br>Search temporarily uses the native view.</html>"));
        JButton diagnostics=new JButton("Log storage diagnostics");
        diagnostics.addActionListener(e -> plugin.diagnostics()); add(diagnostics);
    }
    void status(String text,boolean editing)
    {
        SwingUtilities.invokeLater(() -> { status.setText("<html>"+escape(text)+"</html>"); arrange.setSelected(editing); });
    }
    void syncStatus(String text,boolean active,String hostName)
    {
        String key=active+":"+hostName;
        SwingUtilities.invokeLater(() -> {
            syncStatus.setText("<html>"+escape(text)+"</html>");
            // Don't overwrite a host name or checkbox the user is currently editing.
            if(!key.equals(lastSync)) { syncEnabled.setSelected(active); host.setText(hostName); lastSync=key; }
        });
    }
    private static String escape(String s) { return s.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\n","<br>"); }
}
