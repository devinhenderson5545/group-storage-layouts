package com.docsultan.groupstorage;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class LayoutSyncTest
{
    long now=1000;
    final List<Node> nodes=new ArrayList<>();
    final Deque<GroupStorageLayoutSyncMessage> network=new ArrayDeque<>();
    final class Node
    {
        final long id;
        final LayoutSync sync;
        StorageLayout saved;
        final List<String> notices=new ArrayList<>();
        int commits;
        Node(long id,boolean host,Integer... initial)
        {
            this.id=id; saved=initial==null?null:new StorageLayout(Arrays.asList(initial)); nodes.add(this);
            sync=new LayoutSync(host,id,1,"group",saved,m -> { m.setMemberId(id); network.add(m); },
                layout -> { saved=layout; commits++; },notices::add,() -> now);
        }
    }
    void flush()
    {
        int count=0;
        while(!network.isEmpty())
        {
            assertTrue("No rebroadcast loop",++count<100);
            GroupStorageLayoutSyncMessage m=network.remove();
            for(Node node:new ArrayList<>(nodes)) node.sync.receive(m);
        }
    }
    void connect(Node... followers) { for(Node n:followers) n.sync.tick(); flush(); }
    void equal(Node... all) { for(Node n:all) assertEquals(all[0].saved.encode(),n.saved.encode()); }

    @Test public void missingHostLayoutIsReportedWithoutReplacingFollowersAndCaptureRecovers()
    {
        Node a=new Node(1,true,(Integer[])null), b=new Node(2,false,999), c=new Node(3,false,333);
        connect(b,c);
        assertFalse(a.sync.hasLayout());
        for(Node n:Arrays.asList(b,c))
        {
            assertTrue(n.sync.hostNeedsLayout()); assertFalse(n.sync.ready());
            assertEquals(0,n.commits); assertFalse(n.sync.move(0,1,n.saved.itemAt(0)));
        }
        assertEquals(999,b.saved.itemAt(0)); assertEquals(333,c.saved.itemAt(0));
        a.sync.replace(new StorageLayout(Arrays.asList(100,200))); flush();
        now+=LayoutSync.POLL_MS; connect(b,c);
        equal(a,b,c); assertTrue(b.sync.ready()); assertFalse(b.sync.hostNeedsLayout());
        assertTrue(c.sync.move(0,2,100)); flush(); equal(a,b,c);
    }
    @Test public void missingLayoutReplyMustBeFreshAndFromHostAndExpires()
    {
        Node a=new Node(1,true,(Integer[])null), b=new Node(2,false,999);
        b.sync.tick(); a.sync.receive(network.remove()); GroupStorageLayoutSyncMessage reply=network.remove();
        reply.setMemberId(3); b.sync.receive(reply); assertFalse(b.sync.hostNeedsLayout());
        reply.setMemberId(1); now+=LayoutSync.POLL_MS; b.sync.tick();
        b.sync.receive(reply); assertFalse(b.sync.hostNeedsLayout());
        flush(); assertTrue(b.sync.hostNeedsLayout());
        now+=LayoutSync.TIMEOUT_MS; assertFalse(b.sync.hostNeedsLayout());
        assertEquals(999,b.saved.itemAt(0)); assertEquals(0,b.commits);
    }
    @Test public void delayedMissingLayoutReplyCannotPauseNewerSnapshot()
    {
        Node a=new Node(1,true,(Integer[])null), b=new Node(2,false,999);
        b.sync.tick(); GroupStorageLayoutSyncMessage request=network.remove();
        a.sync.receive(request); GroupStorageLayoutSyncMessage delayed=network.remove();
        a.sync.replace(new StorageLayout(Arrays.asList(100,200))); flush();
        a.sync.receive(request); flush(); assertTrue(b.sync.ready());
        b.sync.receive(delayed); assertTrue(b.sync.ready()); assertFalse(b.sync.hostNeedsLayout()); equal(a,b);
    }
    @Test public void restartedHostWithoutLayoutPausesFollowerAndKeepsLastGoodCopy()
    {
        Node a=new Node(1,true,100,200), b=new Node(2,false,999); connect(b);
        nodes.remove(a); Node restarted=new Node(1,true,(Integer[])null);
        now+=LayoutSync.POLL_MS; connect(b);
        assertTrue(b.sync.hostNeedsLayout()); assertFalse(b.sync.ready()); assertEquals(100,b.saved.itemAt(0));
        restarted.sync.replace(new StorageLayout(Arrays.asList(300,400))); flush();
        now+=LayoutSync.POLL_MS; connect(b); equal(restarted,b); assertTrue(b.sync.ready());
    }

    @Test public void threeClientsJoinAndAnyMemberCanDrag()
    {
        Node a=new Node(1,true,100,200,300), b=new Node(2,false,999), c=new Node(3,false,333);
        connect(b,c); equal(a,b,c);
        assertTrue(b.sync.move(0,10,100)); assertEquals(100,b.saved.itemAt(0));
        flush(); equal(a,b,c); assertEquals(100,c.saved.itemAt(10));
        assertTrue(c.sync.move(1,2,200)); flush(); equal(a,b,c);
        assertTrue(a.sync.move(10,0,100)); flush(); equal(a,b,c);
        assertFalse(b.sync.pending()); assertFalse(c.sync.pending());
    }
    @Test public void simultaneousDragsRejectStaleRequestWithoutOverwriting()
    {
        Node a=new Node(1,true,100,200,300), b=new Node(2,false), c=new Node(3,false);
        connect(b,c);
        assertTrue(b.sync.move(0,5,100)); assertTrue(c.sync.move(1,6,200)); flush();
        equal(a,b,c); assertEquals(100,a.saved.itemAt(5)); assertEquals(200,a.saved.itemAt(1));
        assertEquals(1,c.notices.size()); assertFalse(c.sync.pending());
        assertTrue(c.sync.move(1,6,200)); flush(); equal(a,b,c);
    }
    @Test public void duplicateMoveCannotSwapBack()
    {
        Node a=new Node(1,true,100,200), b=new Node(2,false); connect(b);
        b.sync.move(0,1,100); GroupStorageLayoutSyncMessage move=network.peek(); network.add(move); flush();
        equal(a,b); assertEquals(100,a.saved.itemAt(1)); assertEquals(1,a.sync.revision());
    }
    @Test public void oldBroadcastCannotRollBackNewRevision()
    {
        Node a=new Node(1,true,100,200), b=new Node(2,false); connect(b);
        a.sync.move(0,1,100); GroupStorageLayoutSyncMessage old=network.peek(); flush();
        a.sync.move(1,3,100); flush(); b.sync.receive(old);
        equal(a,b); assertEquals(100,b.saved.itemAt(3));
    }
    @Test public void droppedBroadcastCatchesUpOnPollWithoutRepeatedWrites()
    {
        Node a=new Node(1,true,100,200), b=new Node(2,false); connect(b);
        a.sync.move(0,1,100); network.clear();
        now+=5000; b.sync.tick(); flush(); equal(a,b);
        int commits=b.commits; now+=5000; b.sync.tick(); flush(); assertEquals(commits,b.commits);
    }
    @Test public void hostOfflinePausesEditsButKeepsLayout()
    {
        Node a=new Node(1,true,100,200), b=new Node(2,false); connect(b);
        now+=15000; assertFalse(b.sync.ready()); assertFalse(b.sync.move(0,1,100));
        assertEquals(100,b.saved.itemAt(0));
        b.sync.tick(); flush(); assertTrue(b.sync.ready()); equal(a,b);
    }
    @Test public void restartedHostNeedsFreshHandshakeAndOldSessionIsRejected()
    {
        Node a=new Node(1,true,100,200), b=new Node(2,false); connect(b);
        a.sync.move(0,1,100); GroupStorageLayoutSyncMessage old=network.peek(); flush();
        nodes.remove(a); Node restarted=new Node(1,true,300,400);
        restarted.sync.replace(restarted.saved); flush(); assertEquals(100,b.saved.itemAt(1));
        now+=5000; b.sync.tick(); flush(); equal(restarted,b);
        b.sync.receive(old); equal(restarted,b);
    }
    @Test public void staleChallengeResponseCannotInitializeNewFollowerSession()
    {
        Node a=new Node(1,true,100), b=new Node(2,false,999);
        b.sync.tick(); a.sync.receive(network.remove()); GroupStorageLayoutSyncMessage old=network.remove();
        now+=5000; b.sync.tick(); b.sync.receive(old); assertFalse(b.sync.ready());
        flush(); equal(a,b);
    }
    @Test public void missingAcknowledgementExpiresAndRefreshes()
    {
        Node a=new Node(1,true,100,200), b=new Node(2,false); connect(b);
        b.sync.move(0,1,100); network.clear(); assertFalse(b.sync.move(0,2,100));
        now+=15000; b.sync.tick(); flush(); assertFalse(b.sync.pending()); equal(a,b);
        assertEquals(1,b.notices.size()); assertTrue(b.sync.move(0,1,100)); flush(); equal(a,b);
    }
    @Test public void newcomerObservationsAppendOnceWithoutFillingGaps()
    {
        Node a=new Node(1,true,100,-1,200), b=new Node(2,false), c=new Node(3,false);
        b.sync.observe(Arrays.asList(100,300)); c.sync.observe(Arrays.asList(300,400));
        connect(b,c); equal(a,b,c);
        assertEquals(Arrays.asList(100,-1,200,300,400),a.saved.items());
        long revision=a.sync.revision(); now+=5000; connect(b,c); assertEquals(revision,a.sync.revision());
    }
    @Test public void malformedAndWrongHostPacketsAreIgnored()
    {
        Node a=new Node(1,true,100,200), b=new Node(2,false); connect(b);
        a.sync.move(0,1,100); GroupStorageLayoutSyncMessage m=network.remove();
        m.setMemberId(3); b.sync.receive(m); assertEquals(100,b.saved.itemAt(0));
        m.setMemberId(1); m.protocol=2; b.sync.receive(m); assertEquals(100,b.saved.itemAt(0));
        m.protocol=1; m.group="other"; b.sync.receive(m); assertEquals(100,b.saved.itemAt(0));
        m.group="group"; m.layout="GSL1:100,100"; b.sync.receive(m); assertEquals(100,b.saved.itemAt(0));
        m.layout="GSL1:200,100"; m.revision=-1; b.sync.receive(m); assertEquals(100,b.saved.itemAt(0));
    }
    @Test public void invalidMoveCannotExpandOrCorruptLayout()
    {
        Node a=new Node(1,true,100,200), b=new Node(2,false); connect(b);
        b.sync.move(0,1,100); GroupStorageLayoutSyncMessage m=network.remove();
        m.target=2048; a.sync.receive(m); flush(); equal(a,b); assertEquals(100,a.saved.itemAt(0));
        assertFalse(a.sync.move(-1,1,100)); assertFalse(a.sync.move(0,1,200));
    }
    @Test public void fullLayoutObservationsAreAtomic()
    {
        Integer[] ids=new Integer[2048]; for(int i=0;i<ids.length;i++) ids[i]=i+1;
        Node a=new Node(1,true,ids), b=new Node(2,false); b.sync.observe(Arrays.asList(9000,9001));
        connect(b); equal(a,b); assertEquals(2048,a.saved.size()); assertEquals(0,a.sync.revision());
    }
    @Test public void followersCannotReplaceHostLayout()
    {
        Node b=new Node(2,false,100);
        try { b.sync.replace(new StorageLayout()); fail(); } catch(IllegalStateException expected) { }
        assertEquals(100,b.saved.itemAt(0));
    }
}
