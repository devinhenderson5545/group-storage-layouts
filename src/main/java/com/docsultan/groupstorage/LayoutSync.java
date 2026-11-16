package com.docsultan.groupstorage;

import java.util.*;
import java.util.function.Consumer;
import java.util.function.LongSupplier;

/** Client-thread protocol. Membership/party/profile checks belong to the adapter. */
final class LayoutSync
{
    static final long POLL_MS=5000, TIMEOUT_MS=15000;
    private final boolean host;
    private final long self, leader;
    private final String group;
    private final Consumer<GroupStorageLayoutSyncMessage> send;
    private final Consumer<StorageLayout> changed;
    private final Consumer<String> notice;
    private final LongSupplier clock;
    private StorageLayout layout;
    private String session, poll, pending;
    private long revision, nextPoll, lastHeard, pendingSince;
    private boolean heard, hostNeedsLayout;
    private int[] observed=new int[0];
    private java.util.function.IntSupplier columns=() -> 8;

    void setColumns(java.util.function.IntSupplier columns) { this.columns=columns; }

    LayoutSync(boolean host,long self,long leader,String group,StorageLayout initial,
        Consumer<GroupStorageLayoutSyncMessage> send,Consumer<StorageLayout> changed,
        Consumer<String> notice,LongSupplier clock)
    {
        this.host=host; this.self=self; this.leader=leader; this.group=group;
        this.send=send; this.changed=changed; this.notice=notice; this.clock=clock;
        layout=initial==null?null:new StorageLayout(initial.items());
        session=host?UUID.randomUUID().toString():null;
    }

    boolean isHost() { return host; }
    boolean hasLayout() { return layout!=null; }
    boolean hostNeedsLayout() { return hostNeedsLayout && clock.getAsLong()-lastHeard<TIMEOUT_MS; }
    boolean ready() { return host || (heard && clock.getAsLong()-lastHeard<TIMEOUT_MS); }
    boolean pending() { return pending!=null; }
    long revision() { return revision; }

    void tick()
    {
        long now=clock.getAsLong();
        if(pending!=null && now-pendingSince>=TIMEOUT_MS)
        {
            pending=null;
            notice.accept("No edit acknowledgement. Refreshing; check the layout before retrying.");
        }
        if(!host && now>=nextPoll)
        {
            requestLayout();
        }
    }

    void requestLayout()
    {
        if(host) return;
        nextPoll=clock.getAsLong()+POLL_MS; poll=UUID.randomUUID().toString();
        GroupStorageLayoutSyncMessage m=message("get"); m.token=poll; m.observed=observed.clone(); send.accept(m);
    }

    void observe(Collection<Integer> ids)
    {
        int[] current=ids.stream().filter(id -> id!=null && id>0 && id<=1_000_000)
            .distinct().limit(StorageLayout.MAX_SLOTS).mapToInt(Integer::intValue).toArray();
        if(!Arrays.equals(observed,current)) nextPoll=0;
        observed=current;
        if(host && layout!=null && remember(observed)) publish(null,0,null);
    }

    void replace(StorageLayout value)
    {
        if(!host) throw new IllegalStateException("Only the layout host can replace the shared layout");
        layout=new StorageLayout(value.items()); revision++; commit(); publish(null,0,null);
    }

    boolean move(int source,int target,int item)
    {
        if(!ready() || pending!=null || layout==null || !validMove(source,target,item)) return false;
        if(host)
        {
            layout.swap(source,target); revision++; commit(); publish(null,0,null);
        }
        else
        {
            pending=UUID.randomUUID().toString(); pendingSince=clock.getAsLong();
            GroupStorageLayoutSyncMessage m=message("move"); m.token=pending;
            m.source=source; m.target=target; m.item=item; send.accept(m);
        }
        return true;
    }

    void receive(GroupStorageLayoutSyncMessage m)
    {
        if(m==null || m.protocol!=1 || !group.equals(m.group) || m.getMemberId()==self
            || (m.recipient!=0 && m.recipient!=self) || m.kind==null || m.kind.length()>12
            || (m.token!=null && m.token.length()>64) || (m.session!=null && m.session.length()>64)
            || m.revision<0) return;
        if(host)
        {
            if("get".equals(m.kind) && m.token!=null)
            {
                if(layout==null)
                {
                    GroupStorageLayoutSyncMessage reply=message("no-layout");
                    reply.token=m.token; reply.recipient=m.getMemberId(); send.accept(reply);
                    return;
                }
                if(m.observed!=null && m.observed.length<=StorageLayout.MAX_SLOTS)
                {
                    for(int id:m.observed) if(id<=0 || id>1_000_000) return;
                    if(remember(m.observed)) publish(null,0,null);
                }
                publish(m.token,m.getMemberId(),null);
            }
            else if("move".equals(m.kind) && m.token!=null && layout!=null)
            {
                if(!session.equals(m.session) || revision!=m.revision || !validMove(m.source,m.target,m.item))
                    publish(m.token,m.getMemberId(),"Layout changed before your drag arrived. Review it and retry.");
                else
                {
                    layout.swap(m.source,m.target); revision++; commit();
                    publish(null,0,null); publish(m.token,m.getMemberId(),null);
                }
            }
            return;
        }
        if(m.getMemberId()!=leader || m.session==null) return;
        boolean polled=poll!=null && poll.equals(m.token);
        boolean same=Objects.equals(session,m.session);
        if("no-layout".equals(m.kind))
        {
            // Only a fresh reply may pause editing. Never erase our saved layout or
            // let a delayed no-layout reply roll back a snapshot from this session.
            if(!polled || (same && m.revision<=revision)) return;
            heard=false; hostNeedsLayout=true; lastHeard=clock.getAsLong();
            poll=null; pending=null;
            return;
        }
        if(!"snapshot".equals(m.kind)) return;
        // A different host session requires a response to our fresh challenge.
        if(!same && !polled) return;
        if(same && m.revision<revision) return;
        final StorageLayout incoming;
        try { incoming=StorageLayout.decode(m.layout); }
        catch(IllegalArgumentException ex) { return; }
        boolean update=!same || layout==null || m.revision>revision;
        session=m.session; revision=m.revision; heard=true; hostNeedsLayout=false; lastHeard=clock.getAsLong();
        if(polled) poll=null;
        if(pending!=null && pending.equals(m.token))
        {
            pending=null;
            if(m.error!=null) notice.accept("Layout changed before your drag arrived. Review it and retry.");
        }
        if(update) { layout=incoming; commit(); }
    }

    private boolean validMove(int source,int target,int item)
    {
        return source>=0 && target>=0 && source<StorageLayout.MAX_SLOTS && target<StorageLayout.MAX_SLOTS
            && source!=target && item>0 && layout.itemAt(source)==item;
    }
    private boolean remember(int[] ids)
    {
        try
        {
            if(!layout.remember(Arrays.stream(ids).boxed().collect(java.util.stream.Collectors.toList()),columns.getAsInt())) return false;
            revision++; commit(); return true;
        }
        catch(IllegalArgumentException ex) { notice.accept("Shared layout is full; new items use the native view until space is available."); return false; }
    }
    private void commit() { changed.accept(new StorageLayout(layout.items())); }
    private GroupStorageLayoutSyncMessage message(String kind)
    {
        GroupStorageLayoutSyncMessage m=new GroupStorageLayoutSyncMessage();
        m.kind=kind; m.group=group; m.session=session; m.revision=revision; return m;
    }
    private void publish(String token,long recipient,String error)
    {
        if(layout==null) return;
        GroupStorageLayoutSyncMessage m=message("snapshot");
        m.token=token; m.recipient=recipient; m.error=error; m.layout=layout.encode(); send.accept(m);
    }
}
