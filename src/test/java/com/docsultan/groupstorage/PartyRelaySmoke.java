package com.docsultan.groupstorage;

import com.google.gson.Gson;
import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.name.Names;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Player;
import net.runelite.api.clan.ClanID;
import net.runelite.api.clan.ClanMember;
import net.runelite.api.clan.ClanSettings;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.chat.ChatMessageManager;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.party.PartyService;
import net.runelite.client.party.PartyDataService;
import net.runelite.client.party.messages.PartyData;
import net.runelite.client.task.Scheduler;
import net.runelite.client.party.WSClient;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import static org.mockito.Mockito.*;

/** Opt-in real relay smoke test. Synthetic items only; no game login or real profiles. */
public final class PartyRelaySmoke
{
    private static final List<Node> nodes=new ArrayList<>();
    private static final Path output=Paths.get("build","relay-smoke");

    public static final class Node implements AutoCloseable
    {
        final PartyService party;
        final WSClient socket;
        final OkHttpClient http=new OkHttpClient.Builder().pingInterval(10,TimeUnit.SECONDS).build();
        final Queue<GroupStorageLayoutSyncMessage> inbox=new ConcurrentLinkedQueue<>();
        final Path file;
        final String name;
        final Client client=mock(Client.class);
        final GroupStoragePlugin plugin=mock(GroupStoragePlugin.class);
        final List<String> notices=new ArrayList<>();
        StorageLayout saved;
        LayoutSync state;
        PartyLayoutSync adapter;
        String adapterStatus="";
        long received;
        long commits;
        Node(String name,String passphrase,StorageLayout initial) throws Exception
        {
            this.name="gsl"+name; file=output.resolve(name+".txt"); saved=initial;
            Player player=mock(Player.class); when(player.getName()).thenReturn(this.name);
            when(client.getLocalPlayer()).thenReturn(player); when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
            when(client.getItemCount()).thenReturn(40000);
            ClanSettings clan=mock(ClanSettings.class); when(clan.getName()).thenReturn("synthetic-relay-test");
            List<ClanMember> roster=new ArrayList<>();
            for(String memberName:Arrays.asList("gslhost","gslsecond","gslthird","gslfourth"))
            { ClanMember member=mock(ClanMember.class); when(member.getName()).thenReturn(memberName); roster.add(member); }
            when(clan.getMembers()).thenReturn(roster); when(client.getClanSettings(ClanID.GROUP_IRONMAN)).thenReturn(clan);
            when(plugin.savedLayout()).thenAnswer(call -> saved);
            when(plugin.layoutColumns()).thenReturn(8);
            doAnswer(call -> { save(call.getArgument(0)); return null; }).when(plugin).acceptSyncedLayout(any());
            doAnswer(call -> { adapterStatus=call.getArgument(0); return null; }).when(plugin).syncStatus(anyString(),anyBoolean(),anyString());
            doAnswer(call -> { notices.add(call.getArgument(0)); return null; }).when(plugin).syncNotice(anyString());
            EventBus bus=new EventBus();
            Injector injector=Guice.createInjector(new AbstractModule() {
                @Override protected void configure()
                {
                    bind(Client.class).toInstance(client);
                    bind(ClientThread.class).toInstance(mock(ClientThread.class));
                    bind(Scheduler.class).toInstance(mock(Scheduler.class));
                    bind(ChatMessageManager.class).toInstance(mock(ChatMessageManager.class));
                    bind(EventBus.class).toInstance(bus);
                    bind(OkHttpClient.class).toInstance(http);
                    bind(Gson.class).toInstance(new Gson());
                    bind(HttpUrl.class).annotatedWith(Names.named("runelite.ws"))
                        .toInstance(HttpUrl.get("https://api.runelite.net/ws2"));
                }
            });
            party=injector.getInstance(PartyService.class); socket=injector.getInstance(WSClient.class);
            injector.getInstance(PartyDataService.class);
            socket.registerMessage(GroupStorageLayoutSyncMessage.class); bus.register(this);
            nodes.add(this); party.changeParty(passphrase);
        }
        void identify() { PartyData data=new PartyData(); data.setCharacterName(name); party.send(data); }
        void startAdapter()
        {
            state=null; inbox.clear(); adapter=new PartyLayoutSync(plugin,client,party); adapter.configure(true,"gslhost");
        }
        @Subscribe public void onGroupStorageLayoutSyncMessage(GroupStorageLayoutSyncMessage message) { inbox.add(message); }
        void start(boolean host,long leader)
        {
            inbox.clear();
            state=new LayoutSync(host,party.getLocalMember().getMemberId(),leader,"synthetic-relay-test",saved,
                party::send,this::save,notices::add,System::currentTimeMillis);
        }
        void save(StorageLayout layout)
        {
            commits++;
            saved=layout;
            try { Files.writeString(file,layout.encode()); }
            catch(Exception ex) { throw new IllegalStateException(ex); }
        }
        void pump()
        {
            GroupStorageLayoutSyncMessage message;
            while((message=inbox.poll())!=null)
            { received++; if(adapter!=null) adapter.receive(message,adapter.generation); else if(state!=null) state.receive(message); }
            if(adapter!=null) adapter.tick();
            if(state!=null) state.tick();
        }
        @Override public void close()
        {
            state=null; adapter=null; party.changeParty(null); socket.close();
            http.dispatcher().executorService().shutdownNow(); http.connectionPool().evictAll();
        }
    }
    private static void await(String label,BooleanSupplier done) throws Exception
    {
        long until=System.nanoTime()+TimeUnit.SECONDS.toNanos(25);
        while(System.nanoTime()<until)
        {
            for(Node node:nodes) node.pump();
            if(done.getAsBoolean()) { System.out.println("PASS: "+label); return; }
            Thread.sleep(25);
        }
        for(Node node:nodes) System.out.println("DIAGNOSTIC "+node.name+" status="+node.adapterStatus+" commits="+node.commits
            +" layout="+(node.saved==null?"none":node.saved.encode())+" notices="+node.notices);
        throw new AssertionError("Timed out: "+label);
    }
    private static boolean same(Node... values)
    {
        for(Node n:values) if(!values[0].saved.encode().equals(n.saved.encode())) return false;
        return true;
    }
    public static void main(String[] args) throws Exception
    {
        Files.createDirectories(output);
        String passphrase="gsl-smoke-"+UUID.randomUUID(); // isolated disposable party; never a user's party
        try
        {
            Node host=new Node("host",passphrase,new StorageLayout(Arrays.asList(4151,11806,27277)));
            Node second=new Node("second",passphrase,new StorageLayout(Arrays.asList(100)));
            Node third=new Node("third",passphrase,new StorageLayout(Arrays.asList(200)));
            await("three independent Party connections joined",() -> nodes.stream().allMatch(n -> n.party.getLocalMember()!=null && n.party.getMembers().size()==3));
            long leader=host.party.getLocalMember().getMemberId();
            host.start(true,leader); second.start(false,leader); third.start(false,leader);
            await("initial shared layout delivered",() -> second.state.ready() && third.state.ready() && same(host,second,third));
            if(!host.state.move(2,10,27277)) throw new AssertionError("host drag rejected");
            await("host Shadow move broadcast to both followers",() -> same(host,second,third) && second.saved.itemAt(10)==27277);
            if(!second.state.move(10,12,27277)) throw new AssertionError("follower drag rejected");
            await("follower drag acknowledged and persisted on all three",() -> same(host,second,third) && !second.state.pending() && third.saved.itemAt(12)==27277);
            if(!second.state.move(0,6,4151) || !third.state.move(1,7,11806)) throw new AssertionError("concurrent drags not sent");
            await("concurrent edits converge with one conflict rejection",() -> same(host,second,third) && !second.state.pending() && !third.state.pending() && second.notices.size()+third.notices.size()==1);
            host.state=null; host.party.changeParty(null);
            // Relay rosters retain disconnected members temporarily. Liveness must use
            // our acknowledged snapshots, not immediate removal from the member list.
            await("host timeout pauses follower edits despite retained Party roster",() -> !second.state.ready() && !third.state.ready());
            if(second.state.move(12,15,27277)) throw new AssertionError("offline follower edit accepted");
            // Same persisted-layout path used by the offline host adapter; no network send.
            StorageLayout offline=StorageLayout.decode(Files.readString(host.file)); offline.swap(12,20); host.save(offline);
            host.party.changeParty(passphrase);
            await("host reconnect observed",() -> host.party.getLocalMember()!=null
                && second.party.getMemberById(host.party.getLocalMember().getMemberId())!=null
                && third.party.getMemberById(host.party.getLocalMember().getMemberId())!=null);
            leader=host.party.getLocalMember().getMemberId();
            host.saved=StorageLayout.decode(Files.readString(host.file));
            host.start(true,leader); second.start(false,leader); third.start(false,leader);
            await("offline host edit replaces older follower copies",() -> second.state.ready() && third.state.ready() && same(host,second,third) && third.saved.itemAt(20)==27277);
            for(Node node:nodes) if(!Files.readString(node.file).equals(host.saved.encode())) throw new AssertionError("persistence mismatch");
            // Exercise the production adapter with real Party name messages and a mocked
            // GIM roster. A fresh party avoids retained old connections from the prior test.
            String adapterParty="gsl-adapter-"+UUID.randomUUID();
            for(Node node:nodes) { node.state=null; node.party.changeParty(adapterParty); }
            await("adapter test party joined",() -> nodes.stream().allMatch(n -> n.party.getLocalMember()!=null && n.party.getMembers().size()==3));
            for(Node node:nodes) node.identify();
            await("real PartyDataService received all synthetic character names",() -> nodes.stream().allMatch(n -> n.party.getMembers().stream().allMatch(m -> m.isLoggedIn())));
            for(Node node:nodes) node.startAdapter();
            await("production adapters trust roster and sync",() -> second.adapter.canEdit() && third.adapter.canEdit() && same(host,second,third));
            if(!host.adapter.move(20,22,27277)) throw new AssertionError("adapter host drag rejected");
            await("adapter host move reaches both followers",() -> same(host,second,third) && third.saved.itemAt(22)==27277);
            if(!second.adapter.move(22,24,27277)) throw new AssertionError("adapter follower drag rejected");
            await("adapter follower move acknowledged",() -> second.adapter.canEdit() && same(host,second,third) && third.saved.itemAt(24)==27277);
            host.party.changeParty(null);
            if(!host.adapter.move(24,26,27277)) throw new AssertionError("adapter offline host edit rejected");
            String rejoinParty="gsl-rejoin-"+UUID.randomUUID();
            for(Node node:nodes) node.party.changeParty(rejoinParty);
            await("new shared party joined after offline edit",() -> nodes.stream().allMatch(n -> n.party.getLocalMember()!=null && n.party.getMembers().size()==3));
            for(Node node:nodes) node.identify();
            await("adapter offline host edit syncs without toggling settings",() -> second.adapter.canEdit() && third.adapter.canEdit() && same(host,second,third) && third.saved.itemAt(26)==27277);
            Node fourth=new Node("fourth",rejoinParty,null);
            await("fresh fourth connection joined",() -> nodes.stream().allMatch(n -> n.party.getLocalMember()!=null && n.party.getMembers().size()==4));
            for(Node node:nodes) node.identify();
            await("four clients identified",() -> nodes.stream().allMatch(n -> n.party.getMembers().stream().allMatch(m -> m.isLoggedIn())));
            PartyData logout=new PartyData(); logout.setCharacterName(""); third.party.send(logout);
            when(third.client.getGameState()).thenReturn(GameState.LOGIN_SCREEN);
            long offlineMember=third.party.getLocalMember().getMemberId();
            await("non-host logout reached all four Party rosters",() -> nodes.stream().allMatch(n -> !n.party.getMemberById(offlineMember).isLoggedIn()));
            fourth.startAdapter();
            await("fresh fourth member syncs while another member is logged out",() -> fourth.adapter.canEdit() && same(host,second,fourth));
            fourth.saved=StorageLayout.decode(Files.readString(fourth.file)); fourth.startAdapter();
            await("restarted follower adapter restores persisted layout and reconnects",() -> fourth.adapter.canEdit() && same(host,second,fourth));
            host.saved=StorageLayout.decode(Files.readString(host.file)); host.startAdapter();
            long secondBefore=second.commits, fourthBefore=fourth.commits;
            second.adapter.requestLayout(); fourth.adapter.requestLayout();
            await("restarted host adapter sends saved layout after fresh handshake",() -> second.commits>secondBefore && fourth.commits>fourthBefore
                && second.adapter.canEdit() && fourth.adapter.canEdit() && same(host,second,fourth));
            if(!fourth.adapter.move(26,28,27277)) throw new AssertionError("fresh fourth member drag rejected");
            await("fresh fourth member edits converge after adapter restarts",() -> fourth.adapter.canEdit() && same(host,second,fourth) && host.saved.itemAt(28)==27277);
            if(!fourth.adapter.release(28,27277)) throw new AssertionError("follower release rejected");
            await("follower placeholder release acknowledged across online clients",() -> fourth.adapter.canEdit() && same(host,second,fourth) && host.saved.positionOf(27277)<0);
            fourth.adapter.observe(Arrays.asList(4151,11806,27277)); fourth.adapter.requestLayout();
            await("genuinely observed item can return after release",() -> same(host,second,fourth) && host.saved.positionOf(27277)>=0);
            String report="Real RuneLite WSClient + PartyService: 3 independent connections.\n"
                +"Passed initial sync, host drag, follower drag, concurrent conflict, disconnect, offline edit and reconnect.\n"
                +"Relay retains disconnected roster entries; follower liveness timeout passed.\n"
                +"Production PartyLayoutSync adapter + real PartyDataService: host/follower edits and offline edit then new shared party passed.\n"
                +"Fourth independent connection: first sync with a logged-out group member, persisted follower/host adapter restart, and subsequent shared edit passed.\n"
                +"Adapter restart retains its transport; full client process restart is covered only by mocked tests.\n"
                +"Follower release acknowledgement and re-adding a newly observed item after release passed.\n"
                +"Synthetic names, items and mocked GIM roster only. No game login, UI or item transactions exercised.\n";
            Files.writeString(output.resolve("result.txt"),report); System.out.println(report);
        }
        finally { for(Node node:nodes) node.close(); }
    }
}
