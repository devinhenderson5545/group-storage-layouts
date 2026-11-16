package com.docsultan.groupstorage;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.*;
import java.util.List;
import java.util.function.Consumer;
import javax.inject.Inject;
import javax.swing.SwingUtilities;
import net.runelite.api.*;
import net.runelite.api.events.*;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.game.ItemManager;
import net.runelite.client.input.MouseManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.party.PartyService;
import net.runelite.client.party.WSClient;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.ui.overlay.OverlayManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@PluginDescriptor(name="Group Storage Layouts", description="Saved visual layouts and placeholders for group storage", tags={"group","ironman","storage","placeholder"})
public class GroupStoragePlugin extends Plugin
{
    private static final Logger log=LoggerFactory.getLogger(GroupStoragePlugin.class);
    private static final String CONFIG="groupstoragelayouts";
    private static final int ITEMS=InterfaceID.SharedBank.ITEMS;
    @Inject private Client client;
    @Inject private ClientThread clientThread;
    @Inject private ConfigManager config;
    @Inject private ItemManager itemManager;
    @Inject private ClientToolbar toolbar;
    @Inject private OverlayManager overlays;
    @Inject private MouseManager mouseManager;
    @Inject private PartyService party;
    @Inject private WSClient partySocket;

    volatile StorageView view;
    StorageMouse mouse;
    private StorageOverlay overlay;
    private StoragePanel panel;
    private NavigationButton navigation;
    private StorageLayout layout;
    private StorageLayout displayLayout;
    private PartyLayoutSync sync;
    private GridGeometry grid;
    private String profile;
    private String lastStatus="";
    private boolean enabled=true, arranging=false, stopped=false;
    private long epoch;
    private Widget appliedContainer;
    private final List<WidgetPlacement> placements=new ArrayList<>();
    private int nativeScrollHeight;
    private Map<Integer,BufferedImage> missing=Collections.emptyMap();
    private int visibleSlots;
    private int[] appliedItemIds=new int[0], appliedItemQuantities=new int[0];
    private Widget[] appliedItemWidgets=new Widget[0];

    @Override protected void startUp()
    {
        stopped=false;
        mouse=new StorageMouse(this);
        overlay=new StorageOverlay(this);
        panel=new StoragePanel(this);
        sync=new PartyLayoutSync(this,client,party);
        partySocket.registerMessage(GroupStorageLayoutSyncMessage.class);
        BufferedImage icon=new BufferedImage(16,16,BufferedImage.TYPE_INT_ARGB);
        Graphics2D g=icon.createGraphics();
        g.setColor(new Color(210,170,75));
        for(int y=1;y<15;y+=5) for(int x=1;x<15;x+=5) g.drawRect(x,y,3,3);
        g.dispose();
        navigation=NavigationButton.builder().tooltip("Group Storage Layouts").icon(icon).priority(6).panel(panel).build();
        toolbar.addNavigation(navigation);
        overlays.add(overlay); mouseManager.registerMouseListener(mouse);
        log.info("Group Storage Layouts loaded; gold grid sidebar panel available");
        clientThread.invokeLater(() -> { loadProfile(); status("Open group storage, then capture its current arrangement."); });
    }

    @Override protected void shutDown()
    {
        stopped=true;
        partySocket.unregisterMessage(GroupStorageLayoutSyncMessage.class);
        mouseManager.unregisterMouseListener(mouse); overlays.remove(overlay); toolbar.removeNavigation(navigation);
        clientThread.invokeLater(() -> { if(sync!=null) sync.configure(false,""); restore(); profile=null; });
    }

    private void loadProfile()
    {
        String key=config.getRSProfileKey();
        if(Objects.equals(key,profile)) return;
        restore(); profile=key; layout=null; grid=null; arranging=false;
        if(sync!=null) sync.configure(false,"");
        if(key==null) return;
        try
        {
            String saved=config.getRSProfileConfiguration(CONFIG,"layout");
            if(saved!=null) layout=StorageLayout.decode(saved);
            String savedGrid=config.getRSProfileConfiguration(CONFIG,"grid");
            if(savedGrid!=null)
            {
                String[] values=savedGrid.split(",");
                if(values.length==7)
                {
                    int[] n=Arrays.stream(values).mapToInt(Integer::parseInt).toArray();
                    if(n[0]>=0 && n[1]>=0 && n[2]>0 && n[2]<=200 && n[3]>0 && n[3]<=200
                        && n[4]>0 && n[4]<=32 && n[5]>0 && n[5]<=n[2] && n[6]>0 && n[6]<=n[3])
                        grid=new GridGeometry(n[0],n[1],n[2],n[3],n[4],n[5],n[6]);
                }
            }
        }
        catch(IllegalArgumentException ex) { layout=null; grid=null; status("Saved layout could not be read: "+ex.getMessage()); }
        if(sync!=null)
        {
            String host=config.getRSProfileConfiguration(CONFIG,"syncHost");
            sync.configure("true".equals(config.getRSProfileConfiguration(CONFIG,"syncEnabled")),host==null?"":host);
        }
    }

    @Subscribe public void onScriptPreFired(ScriptPreFired event)
    {
        if(event.getScriptId()==ScriptID.GROUP_IRONMAN_STORAGE_BUILD) restore();
    }

    @Subscribe public void onScriptPostFired(ScriptPostFired event)
    {
        if(event.getScriptId()==ScriptID.GROUP_IRONMAN_STORAGE_BUILD && !stopped)
        {
            loadProfile(); apply();
        }
    }

    @Subscribe public void onClientTick(ClientTick event)
    {
        if(stopped) return;
        loadProfile();
        if(sync!=null) sync.tick();
        Widget container=client.getWidget(ITEMS);
        if(client.getGameState()!=GameState.LOGGED_IN || container==null || container.isHidden())
        {
            if(appliedContainer!=null) restore();
            return;
        }
        if(appliedContainer!=null && appliedContainer!=container) restore();
        // Deposits/withdrawals can update an existing container without the build
        // script firing. Rebuild the visual mapping from current native indices.
        if(appliedContainer!=null && storageContentsChanged()) restore();
        if(appliedContainer==null && enabled && layout!=null) apply();
        if(appliedContainer!=null) refreshView();
    }

    @Subscribe public void onMenuOptionClicked(MenuOptionClicked event)
    {
        if(appliedContainer==null || event.getParam1()!=ITEMS) return;
        if(arranging) { event.consume(); return; }
        if(!event.getMenuOption().toLowerCase(Locale.ROOT).startsWith("withdraw")) return;
        // If a menu was opened before a container update, reject its stale item rather than withdrawing a different one.
        ItemContainer storage=client.getItemContainer(InventoryID.INV_GROUP_TEMP);
        int idx=event.getParam0();
        Item item=storage==null?null:storage.getItem(idx);
        Widget child=appliedContainer.getChild(idx);
        if(item==null || child==null || item.getId()!=child.getItemId()
            || (event.getItemId()>0 && item.getId()!=event.getItemId()))
        {
            event.consume(); status("Storage changed while the menu was open. Please click the item again.");
        }
    }

    void capture()
    {
        clientThread.invokeLater(() -> {
            try
            {
                loadProfile();
                if(sync!=null && !sync.canReplace()) { status("Only the configured layout host can capture or import while sync is enabled."); return; }
                restore();
                NativeStorage nativeStorage=readNative();
                grid=inferGrid(nativeStorage);
                List<Integer> initial=new ArrayList<>();
                for(Widget w:nativeStorage.live.values())
                {
                    int pos=grid.slotAt(w.getOriginalX(),w.getOriginalY());
                    if(pos<0) throw new IllegalArgumentException("Cannot map native storage position");
                    while(initial.size()<=pos) initial.add(-1);
                    if(initial.get(pos)>0) throw new IllegalArgumentException("Two items occupy the same visual slot");
                    initial.set(pos,w.getItemId());
                }
                layout=new StorageLayout(initial); enabled=true; save();
                if(sync!=null && sync.enabled()) sync.replace(layout);
                apply();
            }
            catch(IllegalArgumentException ex) { status(ex.getMessage()); }
        });
    }

    void addNewStorageItems()
    {
        clientThread.invokeLater(() -> {
            try
            {
                loadProfile();
                if(layout==null) { status("No saved layout yet. Capture or import one first, or wait for your host to sync."); return; }
                restore();
                NativeStorage n=readNative();
                grid=inferGrid(n);
                long additions=n.live.keySet().stream().filter(id -> layout.positionOf(id)<0).count();
                if(sync!=null && sync.enabled())
                {
                    sync.observe(n.live.keySet());
                    sync.requestLayout();
                }
                else if(layout.remember(n.live.keySet(),layoutColumns())) save();
                boolean awaitingHost=n.live.keySet().stream().anyMatch(id -> layout.positionOf(id)<0);
                apply();
                status(awaitingHost?"New items are waiting for the layout host. They appear at the bottom with Show saved layout enabled. Join the same RuneLite Party to save shared positions.":
                    additions==0?"All current storage items are already in the saved layout.":
                    "Added "+additions+" new item types at the bottom. Existing positions and placeholders were kept.");
            }
            catch(IllegalArgumentException ex) { status(ex.getMessage()); }
        });
    }

    void setEnabledView(boolean value)
    {
        clientThread.invokeLater(() -> { enabled=value; arranging=false; restore(); if(value) apply(); else status("Native storage view. Your saved layout is kept."); });
    }
    void setArrange(boolean value)
    {
        clientThread.invokeLater(() -> {
            // A checkbox click changes Swing state before we accept/reject it.
            // Always send the actual state back, even after a repeated rejection.
            lastStatus=null;
            if(value && sync!=null && !sync.canEdit()) { arranging=false; if(mouse!=null) mouse.cancel(); refreshView(); status("Shared editing is paused. Wait for the host to sync or acknowledge your last drag."); return; }
            arranging=value && enabled && layout!=null;
            if(mouse!=null) mouse.cancel();
            refreshView();
            status(arranging?"Arrange mode: drag to swap positions. Storage item clicks are disabled.":
                value && layout==null?"No saved layout yet. Capture or import one; if following a host, wait for sync.":
                value && !enabled?"Enable Show saved layout before arranging.":"Normal storage clicks enabled. Saved positions stay fixed.");
        });
    }
    void move(int source,int target,int itemId,long expectedEpoch)
    {
        clientThread.invokeLater(() -> {
            if(!arranging || view==null || view.epoch!=expectedEpoch || layout.itemAt(source)!=itemId) return;
            if(sync!=null && sync.enabled())
            {
                if(!sync.move(source,target,itemId)) status("Drag not sent: waiting for host sync, or another edit is pending.");
                return;
            }
            layout.swap(source,target); save(); restore(); apply();
            log.info("Group storage layout drag: item {} from {} to {}",itemId,source,target);
        });
    }
    void exportLayout(Consumer<String> result)
    {
        clientThread.invokeLater(() -> {
            if(layout==null) { status("Capture a layout first."); return; }
            String text=layout.encode(); SwingUtilities.invokeLater(() -> result.accept(text));
        });
    }
    void importLayout(String text)
    {
        clientThread.invokeLater(() -> {
            try
            {
                loadProfile();
                if(sync!=null && !sync.canReplace()) { status("Only the configured layout host can capture or import while sync is enabled."); return; }
                if(profile==null) throw new IllegalArgumentException("Log in before importing a layout.");
                StorageLayout incoming=StorageLayout.decode(text);
                for(int id:incoming.items())
                    if(id>0 && (itemManager.getItemComposition(id)==null || "null".equals(itemManager.getItemComposition(id).getName())))
                        throw new IllegalArgumentException("Unknown item ID: "+id);
                layout=incoming; save();
                if(sync!=null && sync.enabled()) sync.replace(layout);
                restore(); apply();
                status("Layout imported. Open storage to use it.");
            }
            catch(IllegalArgumentException ex) { status("Import rejected: "+ex.getMessage()); }
        });
    }

    private void save()
    {
        if(profile==null) throw new IllegalArgumentException("Log in before saving a layout.");
        config.setRSProfileConfiguration(CONFIG,"layout",layout.encode());
        if(grid!=null)
            config.setRSProfileConfiguration(CONFIG,"grid",String.format(Locale.ROOT,"%d,%d,%d,%d,%d,%d,%d",
                grid.startX,grid.startY,grid.stepX,grid.stepY,grid.columns,grid.itemWidth,grid.itemHeight));
    }

    private NativeStorage readNative()
    {
        Widget parent=client.getWidget(ITEMS);
        ItemContainer storage=client.getItemContainer(InventoryID.INV_GROUP_TEMP);
        if(client.getGameState()!=GameState.LOGGED_IN || parent==null || parent.isHidden() || storage==null || parent.getChildren()==null)
            throw new IllegalArgumentException("Open group storage first.");
        Map<Integer,Widget> live=new LinkedHashMap<>();
        List<Widget> cells=new ArrayList<>();
        Item[] items=storage.getItems();
        for(int i=0;i<items.length;i++)
        {
            Widget w=parent.getChild(i);
            Item item=items[i];
            if(w!=null && w.getOriginalWidth()>0 && w.getOriginalHeight()>0) cells.add(w);
            if(item.getId()<=0 || item.getQuantity()<=0) continue;
            if(w==null || w.getIndex()!=i || w.getItemId()!=item.getId() || w.getItemQuantity()!=item.getQuantity())
                throw new IllegalArgumentException("Waiting for storage items to finish updating. Native view retained.");
            if(w.isSelfHidden()) throw new IllegalArgumentException("Search/filter active: using native storage view. Clear search to restore the layout.");
            if(live.put(item.getId(),w)!=null) throw new IllegalArgumentException("Duplicate native stacks detected; native view retained.");
        }
        return new NativeStorage(parent,live,cells);
    }

    private GridGeometry inferGrid(NativeStorage nativeStorage)
    {
        // A sparse native container can leave every other column empty. Re-inferring the
        // pitch from only those items would double the spacing and silently move placeholders.
        if(grid!=null)
        {
            int columns=1+(nativeStorage.parent.getWidth()-grid.startX-grid.itemWidth)/grid.stepX;
            if(columns<1 || columns>32) throw new IllegalArgumentException("Storage width changed unexpectedly. Native view retained.");
            for(Widget w:nativeStorage.live.values())
                if(w.getOriginalWidth()!=grid.itemWidth || w.getOriginalHeight()!=grid.itemHeight
                    || w.getOriginalX()<grid.startX || w.getOriginalY()<grid.startY
                    || (w.getOriginalX()-grid.startX)%grid.stepX!=0 || (w.getOriginalY()-grid.startY)%grid.stepY!=0)
                    throw new IllegalArgumentException("Storage grid changed unexpectedly. Native view retained.");
            return new GridGeometry(grid.startX,grid.startY,grid.stepX,grid.stepY,columns,grid.itemWidth,grid.itemHeight);
        }
        List<Rectangle> rectangles=new ArrayList<>();
        for(Widget w:nativeStorage.cells)
            if(!w.isSelfHidden()) rectangles.add(new Rectangle(w.getOriginalX(),w.getOriginalY(),w.getOriginalWidth(),w.getOriginalHeight()));
        return GridGeometry.infer(rectangles,nativeStorage.parent.getWidth());
    }

    private void apply()
    {
        if(stopped || !enabled || layout==null || appliedContainer!=null) return;
        try
        {
            NativeStorage n=readNative();
            grid=inferGrid(n);
            if(sync!=null && sync.enabled()) sync.observe(n.live.keySet());
            else if(layout.remember(n.live.keySet(),layoutColumns())) save();
            // Unknown items remain usable while waiting for the host to assign shared slots.
            // This temporary append is never saved or sent as an authoritative layout.
            displayLayout=new StorageLayout(layout.items());
            displayLayout.remember(n.live.keySet(),layoutColumns());
            appliedContainer=n.parent; nativeScrollHeight=n.parent.getScrollHeight();
            missing=new HashMap<>();
            for(int id:displayLayout.items()) if(id>0 && !n.live.containsKey(id)) missing.put(id,itemManager.getImage(id));
            for(Widget w:n.cells)
            {
                WidgetPlacement p=new WidgetPlacement(w); placements.add(p);
                if(n.live.get(w.getItemId())==w) p.place(grid.cell(displayLayout.positionOf(w.getItemId())));
                else w.setHidden(true);
            }
            visibleSlots=Math.min(StorageLayout.MAX_SLOTS,Math.max(displayLayout.size(),grid.columns)+grid.columns*2);
            n.parent.setScrollHeight(Math.max(n.parent.getHeight(),grid.scrollHeight(visibleSlots)));
            n.parent.revalidateScroll();
            rememberAppliedContents();
            epoch++;
            refreshView();
            status(arranging?"Arrange mode: drag to swap positions. Storage item clicks are disabled.":
                n.live.size()+" stored item types; "+missing.size()+" placeholders. Turn Arrange on to move them.");
            log.debug("Applied group layout: {} live, {} missing, {} columns, native children unchanged",n.live.size(),missing.size(),grid.columns);
        }
        catch(IllegalArgumentException ex) { restore(); status(ex.getMessage()); }
        catch(RuntimeException ex) { restore(); enabled=false; status("Layout paused after an interface error. Native view restored."); log.warn("Group storage layout paused",ex); }
    }

    private boolean storageContentsChanged()
    {
        ItemContainer storage=client.getItemContainer(InventoryID.INV_GROUP_TEMP);
        if(storage==null) return true;
        Item[] items=storage.getItems();
        if(items.length!=appliedItemIds.length) return true;
        for(int i=0;i<items.length;i++)
            if(items[i].getId()!=appliedItemIds[i] || items[i].getQuantity()!=appliedItemQuantities[i]
                || appliedContainer.getChild(i)!=appliedItemWidgets[i]) return true;
        return false;
    }

    private void rememberAppliedContents()
    {
        Item[] items=client.getItemContainer(InventoryID.INV_GROUP_TEMP).getItems();
        appliedItemIds=new int[items.length]; appliedItemQuantities=new int[items.length];
        appliedItemWidgets=new Widget[items.length];
        for(int i=0;i<items.length;i++)
        {
            appliedItemIds[i]=items[i].getId(); appliedItemQuantities[i]=items[i].getQuantity();
            appliedItemWidgets[i]=appliedContainer.getChild(i);
        }
    }

    private void refreshView()
    {
        if(appliedContainer==null || appliedContainer.isHidden() || layout==null) { view=null; return; }
        view=new StorageView(appliedContainer.getBounds(),appliedContainer.getScrollY(),visibleSlots,grid,displayLayout.items(),missing,epoch,arranging);
    }

    private void restore()
    {
        view=null; epoch++;
        if(mouse!=null) mouse.cancel();
        for(WidgetPlacement placement:placements) placement.restore();
        placements.clear();
        if(appliedContainer!=null)
        {
            appliedContainer.setScrollHeight(nativeScrollHeight);
            appliedContainer.setScrollY(Math.max(0,Math.min(appliedContainer.getScrollY(),nativeScrollHeight-appliedContainer.getHeight())));
            appliedContainer.revalidateScroll();
        }
        appliedContainer=null; missing=Collections.emptyMap();
    }

    private void status(String text)
    {
        String key=text+arranging;
        if(key.equals(lastStatus)) return;
        lastStatus=key;
        if(panel!=null) panel.status(text,arranging);
    }

    @Subscribe public void onGroupStorageLayoutSyncMessage(GroupStorageLayoutSyncMessage message)
    {
        if(sync==null || stopped) return;
        long generation=sync.generation;
        clientThread.invokeLater(() -> {
            if(!stopped && sync!=null) { loadProfile(); sync.receive(message,generation); }
        });
    }

    StorageLayout savedLayout() { return layout; }
    int layoutColumns() { return grid==null?8:grid.columns; }
    void syncContextChanged() { restore(); }
    void acceptSyncedLayout(StorageLayout incoming)
    {
        // Runs only on the client thread. Rebuild on the next tick, cancelling any stale drag.
        layout=incoming; save(); restore();
    }
    void syncNotice(String text) { status(text); }
    void syncStatus(String text,boolean active,String host)
    { if(panel!=null) panel.syncStatus(text,active,host); }

    void configureSync(boolean active,String host)
    {
        clientThread.invokeLater(() -> {
            loadProfile();
            if(profile==null) { status("Log in before configuring group sync."); return; }
            String name=host==null?"":host.trim();
            if(name.isEmpty() || name.length()>12) { status("Enter the layout host's character name (up to 12 characters)."); return; }
            if(active && sync!=null && !sync.enabled())
                config.setRSProfileConfiguration(CONFIG,"layoutBeforeSync",layout==null?"GSL1:":layout.encode());
            config.setRSProfileConfiguration(CONFIG,"syncHost",name);
            config.setRSProfileConfiguration(CONFIG,"syncEnabled",Boolean.toString(active));
            arranging=false; restore();
            if(sync!=null) { sync.configure(active,name); sync.tick(); }
            lastStatus=null;
            status("Normal storage clicks enabled. Arrange mode is off.");
            apply();
        });
    }

    void restoreBeforeSync()
    {
        clientThread.invokeLater(() -> {
            loadProfile();
            if(profile==null) { status("Log in first."); return; }
            String backup=config.getRSProfileConfiguration(CONFIG,"layoutBeforeSync");
            if(backup==null) { status("No pre-sync backup exists for this character."); return; }
            try
            {
                StorageLayout restored=StorageLayout.decode(backup);
                config.setRSProfileConfiguration(CONFIG,"syncEnabled","false");
                if(sync!=null)
                {
                    String host=config.getRSProfileConfiguration(CONFIG,"syncHost");
                    sync.configure(false,host==null?"":host); sync.tick();
                }
                arranging=false; layout=restored; save(); restore(); apply();
                status("Pre-sync layout restored locally. Party sync is now off.");
            }
            catch(IllegalArgumentException ex) { status("Backup could not be read."); }
        });
    }

    void diagnostics()
    {
        clientThread.invokeLater(() -> {
            Widget parent=client.getWidget(ITEMS);
            ItemContainer storage=client.getItemContainer(InventoryID.INV_GROUP_TEMP);
            log.info("GSL diagnostics: open={}, applied={}, arrange={}, containerSize={}, layoutSlots={}",
                parent!=null && !parent.isHidden(),appliedContainer!=null,arranging,storage==null?-1:storage.size(),layout==null?-1:layout.size());
            if(parent!=null && parent.getChildren()!=null)
            {
                log.info("GSL parent bounds={} scroll={}/{} children={}",parent.getBounds(),parent.getScrollY(),parent.getScrollHeight(),parent.getChildren().length);
                for(Widget w:parent.getChildren()) if(w!=null && w.getIndex()<20)
                    log.info("GSL widget index={} id={} qty={} pos={},{} size={},{} hidden={} op={}",w.getIndex(),w.getItemId(),w.getItemQuantity(),w.getOriginalX(),w.getOriginalY(),w.getOriginalWidth(),w.getOriginalHeight(),w.isSelfHidden(),Arrays.toString(w.getOnOpListener()));
            }
            status("Storage diagnostics written to the development client log.");
        });
    }

    private static final class NativeStorage
    {
        final Widget parent;
        final Map<Integer,Widget> live;
        final List<Widget> cells;
        NativeStorage(Widget parent,Map<Integer,Widget> live,List<Widget> cells) { this.parent=parent; this.live=live; this.cells=cells; }
    }
}
