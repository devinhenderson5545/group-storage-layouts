package com.docsultan.groupstorage;

import java.awt.Canvas;
import java.awt.Rectangle;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.util.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.mockito.*;
import org.mockito.junit.MockitoJUnitRunner;
import net.runelite.api.*;
import net.runelite.api.events.*;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.game.ItemManager;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.Silent.class)
public class GroupStorageIntegrationTest
{
    @Mock Client client;
    @Mock ClientThread thread;
    @Mock ConfigManager config;
    @Mock ItemManager itemManager;
    @InjectMocks GroupStoragePlugin plugin;
    Widget parent;
    ItemContainer storage;
    Widget[] children;
    Item[] items;
    final Map<Widget,int[]> geometry=new IdentityHashMap<>();

    @Before public void setup()
    {
        when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
        when(config.getRSProfileKey()).thenReturn("test-profile");
        doAnswer(call -> { call.getArgument(0,Runnable.class).run(); return null; }).when(thread).invokeLater(any(Runnable.class));
        when(itemManager.getImage(anyInt())).thenReturn(mock(net.runelite.client.util.AsyncBufferedImage.class));
        parent=mock(Widget.class); storage=mock(ItemContainer.class);
        when(client.getWidget(InterfaceID.SharedBank.ITEMS)).thenReturn(parent);
        when(client.getItemContainer(InventoryID.INV_GROUP_TEMP)).thenReturn(storage);
        when(parent.getWidth()).thenReturn(392); when(parent.getHeight()).thenReturn(200);
        when(parent.getBounds()).thenReturn(new Rectangle(200,100,392,200));
        when(parent.getScrollHeight()).thenReturn(200);
        when(parent.getChildren()).thenAnswer(a -> children);
        when(parent.getChild(anyInt())).thenAnswer(a -> { int i=a.getArgument(0); return i>=0 && i<children.length?children[i]:null; });
        when(storage.getItems()).thenAnswer(a -> items);
        when(storage.getItem(anyInt())).thenAnswer(a -> { int i=a.getArgument(0); return i>=0 && i<items.length?items[i]:null; });
        when(storage.size()).thenAnswer(a -> items.length);
        plugin.mouse=new StorageMouse(plugin);
        nativeItems(100,200,300,400,500,600,700,800,900);
    }

    @Test public void repeatedArrangeClicksWithoutLayoutResetCheckbox() throws Exception
    {
        StoragePanel[] panel=new StoragePanel[1];
        javax.swing.SwingUtilities.invokeAndWait(() -> panel[0]=new StoragePanel(plugin));
        java.lang.reflect.Field field=GroupStoragePlugin.class.getDeclaredField("panel");
        field.setAccessible(true); field.set(plugin,panel[0]);
        java.lang.reflect.Field checkbox=StoragePanel.class.getDeclaredField("arrange");
        checkbox.setAccessible(true);
        javax.swing.JCheckBox arrange=(javax.swing.JCheckBox)checkbox.get(panel[0]);
        for(int attempt=0;attempt<2;attempt++)
        {
            javax.swing.SwingUtilities.invokeAndWait(arrange::doClick);
            javax.swing.SwingUtilities.invokeAndWait(() -> assertFalse(arrange.isSelected()));
        }
    }

    @Test public void applyingSyncWithStorageClosedResetsArrangeCheckbox() throws Exception
    {
        plugin.capture();
        StoragePanel[] panel=new StoragePanel[1];
        javax.swing.SwingUtilities.invokeAndWait(() -> panel[0]=new StoragePanel(plugin));
        java.lang.reflect.Field field=GroupStoragePlugin.class.getDeclaredField("panel");
        field.setAccessible(true); field.set(plugin,panel[0]);
        java.lang.reflect.Field checkbox=StoragePanel.class.getDeclaredField("arrange");
        checkbox.setAccessible(true);
        javax.swing.JCheckBox arrange=(javax.swing.JCheckBox)checkbox.get(panel[0]);
        javax.swing.SwingUtilities.invokeAndWait(arrange::doClick);
        javax.swing.SwingUtilities.invokeAndWait(() -> assertTrue(arrange.isSelected()));
        when(parent.isHidden()).thenReturn(true);
        plugin.configureSync(true,"Doc Sultan");
        javax.swing.SwingUtilities.invokeAndWait(() -> assertFalse(arrange.isSelected()));
    }

    private void nativeItems(int... ids)
    {
        children=new Widget[ids.length]; items=new Item[ids.length];
        for(int i=0;i<ids.length;i++)
        {
            Widget w=mock(Widget.class); children[i]=w; items[i]=new Item(ids[i],1);
            int[] xy={8+(i%8)*48,(i/8)*36}; geometry.put(w,xy);
            when(w.getIndex()).thenReturn(i); when(w.getItemId()).thenReturn(ids[i]); when(w.getItemQuantity()).thenReturn(1);
            when(w.getOriginalWidth()).thenReturn(36); when(w.getOriginalHeight()).thenReturn(32);
            when(w.getOriginalX()).thenAnswer(a -> xy[0]); when(w.getOriginalY()).thenAnswer(a -> xy[1]);
            doAnswer(a -> { xy[0]=a.getArgument(0); return w; }).when(w).setOriginalX(anyInt());
            doAnswer(a -> { xy[1]=a.getArgument(0); return w; }).when(w).setOriginalY(anyInt());
        }
    }
    private void rebuild(int... ids)
    {
        plugin.onScriptPreFired(new ScriptPreFired(ScriptID.GROUP_IRONMAN_STORAGE_BUILD));
        nativeItems(ids);
        plugin.onScriptPostFired(new ScriptPostFired(ScriptID.GROUP_IRONMAN_STORAGE_BUILD));
    }
    private MenuOptionClicked withdraw(int slot,int id)
    {
        MenuEntry entry=mock(MenuEntry.class);
        when(entry.getParam1()).thenReturn(InterfaceID.SharedBank.ITEMS);
        when(entry.getParam0()).thenReturn(slot); when(entry.getItemId()).thenReturn(id);
        when(entry.getOption()).thenReturn("Withdraw-1");
        MenuOptionClicked clicked=new MenuOptionClicked(entry); plugin.onMenuOptionClicked(clicked); return clicked;
    }

    @Test public void disconnectedHostCanCaptureDragAndRememberNewItemsWithSyncEnabled() throws Exception
    {
        Player player=mock(Player.class); when(player.getName()).thenReturn("Doc Sultan");
        when(client.getLocalPlayer()).thenReturn(player);
        net.runelite.client.party.PartyService party=mock(net.runelite.client.party.PartyService.class);
        PartyLayoutSync sync=new PartyLayoutSync(plugin,client,party);
        java.lang.reflect.Field field=GroupStoragePlugin.class.getDeclaredField("sync"); field.setAccessible(true); field.set(plugin,sync);
        plugin.configureSync(true,"Doc Sultan"); plugin.capture();
        assertNotNull(plugin.view); plugin.setArrange(true);
        plugin.move(0,10,100,plugin.view.epoch); plugin.onClientTick(new ClientTick());
        assertEquals(100,plugin.view.itemAt(10));
        verify(config).setRSProfileConfiguration("groupstoragelayouts","layout","GSL1:-1,200,300,400,500,600,700,800,900,-1,100");
        rebuild(100,200,300,400,500,600,700,800,900,1000);
        assertEquals(1000,plugin.savedLayout().itemAt(11));
        plugin.setArrange(false); assertFalse(withdraw(0,100).isConsumed());
        verify(party,never()).send(any(GroupStorageLayoutSyncMessage.class)); assertTrue(sync.enabled());
    }

    @Test public void receivedLayoutPreservesNativeClickIndices()
    {
        plugin.capture();
        plugin.acceptSyncedLayout(new StorageLayout(Arrays.asList(200,100,300,400,500,600,700,800,900)));
        assertNull(plugin.view); plugin.onClientTick(new ClientTick());
        assertEquals(200,plugin.view.itemAt(0)); assertEquals(100,plugin.view.itemAt(1));
        assertArrayEquals(new int[]{8,0},geometry.get(children[1]));
        assertFalse(withdraw(1,200).isConsumed()); assertTrue(withdraw(0,200).isConsumed());
    }

    @Test public void followerShowsNewItemsWithoutSavingLocalAppendAndWaitsForHostDrag() throws Exception
    {
        plugin.capture();
        net.runelite.client.party.PartyService party=mock(net.runelite.client.party.PartyService.class);
        net.runelite.client.party.PartyMember host=PartyLayoutSyncTest.member(1,"Doc Sultan");
        net.runelite.client.party.PartyMember local=PartyLayoutSyncTest.member(2,"Teammate");
        Player player=mock(Player.class); when(player.getName()).thenReturn("Teammate");
        when(client.getLocalPlayer()).thenReturn(player); when(client.getItemCount()).thenReturn(40000);
        net.runelite.api.clan.ClanSettings clan=mock(net.runelite.api.clan.ClanSettings.class);
        net.runelite.api.clan.ClanMember a=mock(net.runelite.api.clan.ClanMember.class), b=mock(net.runelite.api.clan.ClanMember.class);
        when(a.getName()).thenReturn("Doc Sultan"); when(b.getName()).thenReturn("Teammate");
        when(clan.getMembers()).thenReturn(Arrays.asList(a,b)); when(clan.getName()).thenReturn("Group");
        when(client.getClanSettings(net.runelite.api.clan.ClanID.GROUP_IRONMAN)).thenReturn(clan);
        when(party.isInParty()).thenReturn(true); when(party.getPartyId()).thenReturn(55L);
        when(party.getLocalMember()).thenReturn(local); when(party.getMembers()).thenReturn(Arrays.asList(host,local));
        PartyLayoutSync sync=new PartyLayoutSync(plugin,client,party);
        java.lang.reflect.Field field=GroupStoragePlugin.class.getDeclaredField("sync"); field.setAccessible(true); field.set(plugin,sync);
        plugin.configureSync(true,"Doc Sultan");
        verify(config).setRSProfileConfiguration("groupstoragelayouts","layoutBeforeSync","GSL1:100,200,300,400,500,600,700,800,900");
        ArgumentCaptor<GroupStorageLayoutSyncMessage> sent=ArgumentCaptor.forClass(GroupStorageLayoutSyncMessage.class);
        verify(party).send(sent.capture());
        GroupStorageLayoutSyncMessage reply=new GroupStorageLayoutSyncMessage();
        reply.kind="snapshot"; reply.group="group"; reply.session="host"; reply.token=sent.getValue().token;
        reply.layout="GSL1:200,100"; reply.setMemberId(1);
        plugin.onGroupStorageLayoutSyncMessage(reply); plugin.onClientTick(new ClientTick());
        assertEquals("GSL1:200,100",plugin.savedLayout().encode());
        assertEquals(300,plugin.view.itemAt(2)); // visible overflow, not a private saved position
        plugin.setArrange(true); plugin.move(0,10,200,plugin.view.epoch);
        assertEquals(200,plugin.view.itemAt(0)); // waits for acknowledgement
        verify(party,times(2)).send(sent.capture());
        reply.token=sent.getValue().token; reply.revision=1;
        reply.layout="GSL1:-1,100,300,400,500,600,700,800,900,-1,200";
        plugin.onGroupStorageLayoutSyncMessage(reply); plugin.onClientTick(new ClientTick());
        assertEquals(200,plugin.view.itemAt(10)); assertArrayEquals(new int[]{104,36},geometry.get(children[1]));
        plugin.setArrange(false); assertFalse(withdraw(1,200).isConsumed());
    }

    @Test public void nativeIndexSurvivesWithdrawalReturnAndVisualMove()
    {
        plugin.capture(); assertNotNull(plugin.view);
        plugin.setArrange(true); plugin.move(1,10,200,plugin.view.epoch); plugin.setArrange(false);
        rebuild(100,900,300,400,500,600,700,800);
        assertTrue(plugin.view.missing.containsKey(200)); assertEquals(200,plugin.view.itemAt(10));
        rebuild(200,100,900,300,400,500,600,700,800);
        assertFalse(plugin.view.missing.containsKey(200));
        assertArrayEquals(new int[]{104,36},geometry.get(children[0])); // visual position 10, native index 0
        assertFalse(withdraw(0,200).isConsumed());
        for(Widget w:children) { verify(w,never()).setItemId(anyInt()); verify(w,never()).setItemQuantity(anyInt()); }
    }
    @Test public void staleRightClickMenuIsRejectedAfterNativeSlotChanges()
    {
        plugin.capture(); rebuild(200,100,300,400,500,600,700,800,900);
        assertTrue(withdraw(0,100).isConsumed()); assertFalse(withdraw(0,200).isConsumed());
    }
    @Test public void emptyStorageRetainsAllPlaceholdersAndReturnPositions()
    {
        plugin.capture(); rebuild();
        assertEquals(9,plugin.view.missing.size());
        rebuild(900);
        assertEquals(900,plugin.view.itemAt(8)); assertArrayEquals(new int[]{8,36},geometry.get(children[0]));
    }
    @Test public void filteringNeverRearrangesSearchResults()
    {
        plugin.capture(); plugin.onScriptPreFired(new ScriptPreFired(ScriptID.GROUP_IRONMAN_STORAGE_BUILD));
        when(children[1].isSelfHidden()).thenReturn(true);
        plugin.onScriptPostFired(new ScriptPostFired(ScriptID.GROUP_IRONMAN_STORAGE_BUILD));
        assertNull(plugin.view);
        assertArrayEquals(new int[]{56,0},geometry.get(children[1]));
    }
    @Test public void originalViewRestoresPositionsAndKeepsSavedLayout()
    {
        plugin.capture(); plugin.setArrange(true); plugin.move(0,10,100,plugin.view.epoch);
        plugin.setEnabledView(false); assertNull(plugin.view);
        assertArrayEquals(new int[]{8,0},geometry.get(children[0]));
        plugin.setEnabledView(true); assertArrayEquals(new int[]{104,36},geometry.get(children[0]));
    }
    @Test public void arrangeModeConsumesStorageClicks()
    {
        plugin.capture(); plugin.setArrange(true); assertTrue(withdraw(0,100).isConsumed());
        Canvas canvas=new Canvas();
        MouseEvent press=new MouseEvent(canvas,MouseEvent.MOUSE_PRESSED,1,0,215,110,1,false,MouseEvent.BUTTON1);
        MouseEvent release=new MouseEvent(canvas,MouseEvent.MOUSE_RELEASED,2,0,310,150,1,false,MouseEvent.BUTTON1);
        plugin.mouse.mousePressed(press); plugin.mouse.mouseReleased(release);
        assertTrue(press.isConsumed()); assertTrue(release.isConsumed()); assertEquals(100,plugin.view.itemAt(10));
    }
    @Test public void placeholderCanBeDraggedWithoutAnyNativeItem()
    {
        plugin.capture(); rebuild(200,300,400,500,600,700,800,900); plugin.setArrange(true);
        Canvas canvas=new Canvas();
        plugin.mouse.mousePressed(new MouseEvent(canvas,MouseEvent.MOUSE_PRESSED,1,0,215,110,1,false,MouseEvent.BUTTON1));
        plugin.mouse.mouseReleased(new MouseEvent(canvas,MouseEvent.MOUSE_RELEASED,2,0,310,150,1,false,MouseEvent.BUTTON1));
        assertEquals(100,plugin.view.itemAt(10)); assertEquals(-1,plugin.view.itemAt(0));
    }
    @Test public void dragCancelledByContainerRebuildCannotMoveAnotherItem()
    {
        plugin.capture(); plugin.setArrange(true);
        Canvas canvas=new Canvas();
        plugin.mouse.mousePressed(new MouseEvent(canvas,MouseEvent.MOUSE_PRESSED,1,0,215,110,1,false,MouseEvent.BUTTON1));
        rebuild(200,300,400,500,600,700,800,900);
        MouseEvent release=new MouseEvent(canvas,MouseEvent.MOUSE_RELEASED,2,0,310,150,1,false,MouseEvent.BUTTON1);
        plugin.mouse.mouseReleased(release);
        assertTrue(release.isConsumed()); assertEquals(100,plugin.view.itemAt(0));
    }
    @Test public void profileSwitchNeverReusesOtherCharactersLayout()
    {
        plugin.capture(); when(config.getRSProfileKey()).thenReturn("another-profile");
        plugin.onClientTick(new ClientTick()); assertNull(plugin.view);
        assertArrayEquals(new int[]{8,0},geometry.get(children[0]));
    }
    @Test public void sparseNativeSlotsNeverChangeTheLearnedGridSpacing()
    {
        plugin.capture(); plugin.onScriptPreFired(new ScriptPreFired(ScriptID.GROUP_IRONMAN_STORAGE_BUILD));
        // The server leaves gaps after withdrawing the middle columns; indices remain native.
        for(int i:new int[]{1,3,4,5,6,7})
        {
            items[i]=new Item(-1,0);
            when(children[i].getItemId()).thenReturn(-1);
            when(children[i].getItemQuantity()).thenReturn(0);
            when(children[i].isSelfHidden()).thenReturn(true);
        }
        plugin.onScriptPostFired(new ScriptPostFired(ScriptID.GROUP_IRONMAN_STORAGE_BUILD));
        assertEquals(48,plugin.view.grid.stepX); assertEquals(8,plugin.view.grid.columns);
        assertEquals(6,plugin.view.missing.size());
        assertArrayEquals(new int[]{104,0},geometry.get(children[2]));
    }
}
