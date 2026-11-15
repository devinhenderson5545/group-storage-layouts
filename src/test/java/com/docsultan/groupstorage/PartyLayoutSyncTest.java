package com.docsultan.groupstorage;

import java.util.*;
import org.junit.*;
import org.mockito.ArgumentCaptor;
import net.runelite.api.*;
import net.runelite.api.clan.*;
import net.runelite.client.party.*;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class PartyLayoutSyncTest
{
    Client client=mock(Client.class);
    GroupStoragePlugin plugin=mock(GroupStoragePlugin.class);
    PartyService party=mock(PartyService.class);
    ClanSettings clan=mock(ClanSettings.class);
    Player player=mock(Player.class);
    PartyLayoutSync adapter=new PartyLayoutSync(plugin,client,party);
    PartyMember host=member(1,"Doc Sultan"), follower=member(2,"Teammate");

    static PartyMember member(long id,String name)
    {
        PartyMember m=mock(PartyMember.class);
        when(m.getMemberId()).thenReturn(id); when(m.getDisplayName()).thenReturn(name); when(m.isLoggedIn()).thenReturn(true); return m;
    }
    @Before public void setup()
    {
        when(client.getGameState()).thenReturn(GameState.LOGGED_IN); when(client.getLocalPlayer()).thenReturn(player);
        when(player.getName()).thenReturn("Teammate"); when(client.getItemCount()).thenReturn(40000);
        when(client.getClanSettings(ClanID.GROUP_IRONMAN)).thenReturn(clan); when(clan.getName()).thenReturn("Group");
        ClanMember a=mock(ClanMember.class),b=mock(ClanMember.class);
        when(a.getName()).thenReturn("Doc Sultan"); when(b.getName()).thenReturn("Teammate");
        when(clan.getMembers()).thenReturn(Arrays.asList(a,b));
        when(party.isInParty()).thenReturn(true); when(party.getPartyId()).thenReturn(55L);
        when(party.getLocalMember()).thenReturn(follower); when(party.getMembers()).thenReturn(Arrays.asList(host,follower));
        when(plugin.savedLayout()).thenReturn(new StorageLayout(Arrays.asList(100,200)));
    }
    GroupStorageLayoutSyncMessage handshake()
    {
        adapter.configure(true,"Doc Sultan"); adapter.tick();
        ArgumentCaptor<GroupStorageLayoutSyncMessage> sent=ArgumentCaptor.forClass(GroupStorageLayoutSyncMessage.class);
        verify(party).send(sent.capture());
        GroupStorageLayoutSyncMessage reply=new GroupStorageLayoutSyncMessage(); reply.kind="snapshot"; reply.group="group";
        reply.session="host-session"; reply.token=sent.getValue().token; reply.layout="GSL1:200,100"; reply.setMemberId(1);
        return reply;
    }
    @Test public void validGroupHandshakeAppliesHostLayout()
    {
        GroupStorageLayoutSyncMessage m=handshake(); adapter.receive(m,adapter.generation);
        assertTrue(adapter.canEdit()); assertFalse(adapter.canReplace()); verify(plugin).acceptSyncedLayout(any(StorageLayout.class));
    }
    @Test public void hostWithoutLayoutGetsSetupInstructionsAndRepliesToFollower()
    {
        when(player.getName()).thenReturn("Doc Sultan"); when(party.getLocalMember()).thenReturn(host);
        when(plugin.savedLayout()).thenReturn(null);
        adapter.configure(true,"Doc Sultan"); adapter.tick();
        verify(plugin).syncStatus(contains("no saved layout yet"),eq(true),eq("Doc Sultan"));
        GroupStorageLayoutSyncMessage request=new GroupStorageLayoutSyncMessage();
        request.kind="get"; request.group="group"; request.token="fresh"; request.setMemberId(2);
        adapter.receive(request,adapter.generation);
        ArgumentCaptor<GroupStorageLayoutSyncMessage> sent=ArgumentCaptor.forClass(GroupStorageLayoutSyncMessage.class);
        verify(party).send(sent.capture());
        assertEquals("no-layout",sent.getValue().kind); assertEquals("fresh",sent.getValue().token);
        assertEquals(2,sent.getValue().recipient); assertTrue(adapter.canReplace());
    }
    @Test public void followerExplainsMissingHostLayoutWithoutApplyingAnything()
    {
        GroupStorageLayoutSyncMessage m=handshake(); m.kind="no-layout"; m.layout=null;
        adapter.receive(m,adapter.generation);
        verify(plugin).syncStatus(contains("they have no saved layout"),eq(true),eq("Doc Sultan"));
        verify(plugin,never()).acceptSyncedLayout(any()); assertFalse(adapter.canEdit());
    }
    @Test public void outsiderInPartyPausesAllSharing()
    {
        PartyMember outsider=member(3,"Outsider");
        when(party.getMembers()).thenReturn(Arrays.asList(host,follower,outsider));
        adapter.configure(true,"Doc Sultan"); adapter.tick();
        verify(party,never()).send(any(GroupStorageLayoutSyncMessage.class)); assertFalse(adapter.canEdit());
    }
    @Test public void untrustedSenderCannotApplySnapshot()
    {
        GroupStorageLayoutSyncMessage m=handshake(); m.setMemberId(99); adapter.receive(m,adapter.generation);
        verify(plugin,never()).acceptSyncedLayout(any()); assertFalse(adapter.canEdit());
    }
    @Test public void queuedMessageFromPriorProfileIsRejected()
    {
        GroupStorageLayoutSyncMessage m=handshake(); long old=adapter.generation;
        adapter.configure(true,"Doc Sultan"); adapter.receive(m,old);
        verify(plugin,never()).acceptSyncedLayout(any());
    }
    @Test public void changingPartyRejectsOldMessage()
    {
        GroupStorageLayoutSyncMessage m=handshake(); long old=adapter.generation;
        when(party.getPartyId()).thenReturn(66L); adapter.receive(m,old);
        verify(plugin,never()).acceptSyncedLayout(any());
    }
    @Test public void hostLeavingDisablesSharedEditing()
    {
        GroupStorageLayoutSyncMessage m=handshake(); adapter.receive(m,adapter.generation); assertTrue(adapter.canEdit());
        when(party.getMembers()).thenReturn(Collections.singletonList(follower)); assertFalse(adapter.canEdit());
    }
    @Test public void invalidGameItemCannotReachRendering()
    {
        GroupStorageLayoutSyncMessage m=handshake(); m.layout="GSL1:999999"; adapter.receive(m,adapter.generation);
        verify(plugin,never()).acceptSyncedLayout(any()); assertFalse(adapter.canEdit());
    }
    @Test public void transportFailureDoesNotDiscardLocalLayoutOrThrow()
    {
        doThrow(new IllegalStateException("disconnected")).when(party).send(any(GroupStorageLayoutSyncMessage.class));
        adapter.configure(true,"Doc Sultan"); adapter.tick();
        assertFalse(adapter.canEdit()); verify(plugin).syncNotice(contains("connection unavailable"));
        verify(plugin,never()).acceptSyncedLayout(any());
    }
    @Test public void hostEditsOutsidePartyThenRejoinsWithLatestSavedLayout()
    {
        when(player.getName()).thenReturn("Doc Sultan"); when(party.getLocalMember()).thenReturn(host);
        when(party.isInParty()).thenReturn(false);
        java.util.concurrent.atomic.AtomicReference<StorageLayout> saved=new java.util.concurrent.atomic.AtomicReference<>(new StorageLayout(Arrays.asList(100,200)));
        when(plugin.savedLayout()).thenAnswer(call -> saved.get());
        doAnswer(call -> { saved.set(call.getArgument(0)); return null; }).when(plugin).acceptSyncedLayout(any());
        adapter.configure(true,"Doc Sultan"); adapter.tick();
        assertTrue(adapter.canEdit()); assertTrue(adapter.canReplace());
        assertTrue(adapter.move(0,5,100));
        adapter.observe(Arrays.asList(200,300));
        assertEquals("GSL1:-1,200,-1,-1,-1,100,300",saved.get().encode());
        verify(party,never()).send(any(GroupStorageLayoutSyncMessage.class));
        // A new adapter represents restart: only the persisted layout is retained.
        adapter=new PartyLayoutSync(plugin,client,party); adapter.configure(true,"Doc Sultan");
        when(party.isInParty()).thenReturn(true); adapter.tick();
        GroupStorageLayoutSyncMessage request=new GroupStorageLayoutSyncMessage();
        request.kind="get"; request.group="group"; request.token="fresh-join"; request.setMemberId(2);
        adapter.receive(request,adapter.generation);
        ArgumentCaptor<GroupStorageLayoutSyncMessage> sent=ArgumentCaptor.forClass(GroupStorageLayoutSyncMessage.class);
        verify(party).send(sent.capture());
        assertEquals(saved.get().encode(),sent.getValue().layout);
        assertEquals("fresh-join",sent.getValue().token);
        assertTrue(adapter.move(5,0,100)); // live changes still use the shared protocol
        verify(party,times(2)).send(any(GroupStorageLayoutSyncMessage.class));
    }
    @Test public void followerCannotEditOrReplaceOutsideParty()
    {
        when(party.isInParty()).thenReturn(false); adapter.configure(true,"Doc Sultan"); adapter.tick();
        assertFalse(adapter.canEdit()); assertFalse(adapter.canReplace()); assertFalse(adapter.move(0,1,100));
        adapter.observe(Arrays.asList(300));
        verify(plugin,never()).acceptSyncedLayout(any()); verify(party,never()).send(any(GroupStorageLayoutSyncMessage.class));
    }
    @Test public void hostCanEditAfterLeavingAndCannotEditAsDifferentCharacter()
    {
        when(player.getName()).thenReturn("Doc Sultan"); when(party.getLocalMember()).thenReturn(host);
        adapter.configure(true,"Doc Sultan"); adapter.tick();
        when(party.isInParty()).thenReturn(false);
        assertTrue(adapter.move(0,3,100)); verify(plugin).acceptSyncedLayout(any());
        when(player.getName()).thenReturn("Teammate");
        assertFalse(adapter.canReplace()); assertFalse(adapter.move(0,4,100));
        when(client.getGameState()).thenReturn(GameState.LOGIN_SCREEN);
        when(player.getName()).thenReturn("Doc Sultan"); assertFalse(adapter.canEdit());
        verify(party,never()).send(any(GroupStorageLayoutSyncMessage.class));
    }
}
