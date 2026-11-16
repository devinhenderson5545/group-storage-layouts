package com.docsultan.groupstorage;

import java.util.*;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.clan.ClanID;
import net.runelite.api.clan.ClanSettings;
import net.runelite.client.party.PartyMember;
import net.runelite.client.party.PartyService;
import net.runelite.client.util.Text;

/** Gates the protocol on the current account, GIM roster and RuneLite party. */
final class PartyLayoutSync
{
    private final GroupStoragePlugin plugin;
    private final Client client;
    private final PartyService party;
    private LayoutSync state;
    private String context="", hostName="", detail="Party sync is off.";
    private boolean enabled;
    private long nextTick;
    private final Set<Long> trusted=new HashSet<>();
    private boolean sendFailed;
    volatile long generation;

    PartyLayoutSync(GroupStoragePlugin plugin,Client client,PartyService party)
    { this.plugin=plugin; this.client=client; this.party=party; }

    void configure(boolean enabled,String host)
    {
        this.enabled=enabled; hostName=host; reset(); nextTick=0;
    }
    boolean enabled() { return enabled; }
    boolean canReplace() { update(); return !enabled || isLocalHost(); }
    boolean canEdit() { update(); return !enabled || isLocalHost() || (state!=null && state.ready() && !state.pending()); }
    void replace(StorageLayout value) { if(state!=null) state.replace(value); }
    boolean move(int source,int target,int item)
    {
        update();
        if(state!=null) return state.move(source,target,item);
        StorageLayout saved=plugin.savedLayout();
        if(!enabled || !isLocalHost() || saved==null || source<0 || target<0
            || source>=StorageLayout.MAX_SLOTS || target>=StorageLayout.MAX_SLOTS
            || source==target || item<=0 || saved.itemAt(source)!=item) return false;
        StorageLayout edited=new StorageLayout(saved.items());
        edited.swap(source,target); plugin.acceptSyncedLayout(edited); return true;
    }
    void observe(Collection<Integer> ids)
    {
        update();
        if(state!=null) { state.observe(ids); return; }
        StorageLayout saved=plugin.savedLayout();
        if(enabled && isLocalHost() && saved!=null)
        {
            StorageLayout edited=new StorageLayout(saved.items());
            if(edited.remember(ids,plugin.layoutColumns())) plugin.acceptSyncedLayout(edited);
        }
    }
    void requestLayout()
    {
        update();
        if(state!=null) state.requestLayout();
        renderStatus();
    }
    private boolean isLocalHost()
    {
        return client.getGameState()==GameState.LOGGED_IN && client.getLocalPlayer()!=null
            && !normalize(hostName).isEmpty()
            && normalize(hostName).equals(normalize(client.getLocalPlayer().getName()));
    }

    void tick()
    {
        long now=System.currentTimeMillis();
        if(now<nextTick) return;
        nextTick=now+1000; update();
        if(state!=null) state.tick();
        renderStatus();
    }
    void receive(GroupStorageLayoutSyncMessage m,long expectedGeneration)
    {
        if(expectedGeneration!=generation) return;
        update();
        if(expectedGeneration!=generation || state==null || !trusted.contains(m.getMemberId())) return;
        // Reject IDs outside this client's game cache before asking ItemManager to render them.
        int itemCount=client.getItemCount();
        if(m.observed!=null)
        {
            if(m.observed.length>StorageLayout.MAX_SLOTS) return;
            for(int id:m.observed) if(id<=0 || id>=itemCount) return;
        }
        if("snapshot".equals(m.kind))
        {
            try { for(int id:StorageLayout.decode(m.layout).items()) if(id>=itemCount) return; }
            catch(IllegalArgumentException ex) { return; }
        }
        state.receive(m); renderStatus();
    }
    private void reset() { state=null; context=""; trusted.clear(); generation++; }
    private void unavailable(String reason)
    {
        if(state!=null || !context.isEmpty()) reset();
        detail=reason;
    }
    private void update()
    {
        if(!enabled) { unavailable("Party sync is off. Layout edits are local."); return; }
        if(client.getGameState()!=GameState.LOGGED_IN || client.getLocalPlayer()==null)
        { unavailable("Sync waiting for login."); return; }
        ClanSettings clan=client.getClanSettings(ClanID.GROUP_IRONMAN);
        if(clan==null || clan.getName()==null || clan.getMembers()==null)
        { unavailable("Sync waiting for your Group Ironman roster."); return; }
        if(!party.isInParty() || party.getLocalMember()==null)
        { unavailable("Enable RuneLite Party and join the same private party on all three clients."); return; }
        Set<String> roster=new HashSet<>();
        clan.getMembers().forEach(member -> roster.add(normalize(member.getName())));
        PartyMember local=party.getLocalMember(), leader=null;
        Set<Long> allowed=new HashSet<>();
        for(PartyMember member:new ArrayList<>(party.getMembers()))
        {
            String name=normalize(member.getDisplayName());
            if(!member.isLoggedIn() || !roster.contains(name))
            { unavailable("Sync paused: every party member must be logged into this GIM group. Use a private group party."); return; }
            if(name.equals(normalize(hostName)))
            {
                if(leader!=null) { unavailable("Sync paused: duplicate host name in party."); return; }
                leader=member;
            }
            allowed.add(member.getMemberId());
        }
        if(!normalize(local.getDisplayName()).equals(normalize(client.getLocalPlayer().getName())))
        { unavailable("Waiting for Party to update your character name."); return; }
        if(leader==null) { unavailable("Host "+hostName+" is offline or absent. Saved layout remains available; shared edits are paused."); return; }
        String group=normalize(clan.getName());
        String key=party.getPartyId()+":"+local.getMemberId()+":"+leader.getMemberId()+":"+group;
        trusted.clear(); trusted.addAll(allowed);
        if(!key.equals(context))
        {
            generation++; context=key;
            state=new LayoutSync(local.getMemberId()==leader.getMemberId(),local.getMemberId(),leader.getMemberId(),group,
                plugin.savedLayout(),this::send,plugin::acceptSyncedLayout,plugin::syncNotice,System::currentTimeMillis);
            state.setColumns(plugin::layoutColumns);
            plugin.syncContextChanged(); // Re-observe items already visible when a party reconnects.
        }
    }
    private void send(GroupStorageLayoutSyncMessage message)
    {
        try { party.send(message); sendFailed=false; }
        catch(RuntimeException ex)
        {
            if(!sendFailed) plugin.syncNotice("Party connection unavailable. Sync will retry; saved layout is retained.");
            sendFailed=true;
        }
    }
    private void renderStatus()
    {
        if(state!=null)
            detail=state.isHost()?(state.hasLayout()?"Hosting shared layout as "+hostName+".":
                "You are the layout host, but have no saved layout yet. Open group storage and capture its arrangement, or import a layout."):
                state.hostNeedsLayout()?"Connected to "+hostName+", but they have no saved layout. Ask them to capture or import once. Your saved layout is kept; shared edits are paused.":
                !state.ready()?"Waiting for "+hostName+" to send a layout. Shared edits are paused. On the host: enable this plugin and sync, apply the same host name, and capture or import a layout if none is saved.":
                state.pending()?"Waiting for the host to confirm your drag.":"Synced with "+hostName+".";
        String status=enabled && state==null && isLocalHost()
            ? "Host edits are saved locally and will sync when the party connection is ready. "+detail : detail;
        plugin.syncStatus(status,enabled,hostName);
    }
    static String normalize(String name)
    { return name==null?"":Text.removeTags(name).replace('\u00a0',' ').replace('_',' ').trim().toLowerCase(Locale.ROOT); }
}
