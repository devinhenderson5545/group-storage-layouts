package com.docsultan.groupstorage;

import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;

public class StorageLayoutTest
{
    @Test public void withdrawalReturnAndShuffledServerOrderKeepPositions()
    {
        StorageLayout l=new StorageLayout(Arrays.asList(100,200,300));
        l.remember(Arrays.asList(300,100)); // 200 withdrawn; server compresses/reorders.
        assertEquals(200,l.itemAt(1));
        l.remember(Arrays.asList(200,300,100)); // Someone returns 200 at native index zero.
        assertEquals(Arrays.asList(100,200,300),l.items());
    }
    @Test public void draggingPlaceholderToGapSurvivesSaveAndReturn()
    {
        StorageLayout l=new StorageLayout(Arrays.asList(100,200,300));
        l.remember(Arrays.asList(100,300));
        l.swap(1,10);
        StorageLayout reloaded=StorageLayout.decode(l.encode());
        reloaded.remember(Arrays.asList(200,300,100));
        assertEquals(200,reloaded.itemAt(10)); assertEquals(-1,reloaded.itemAt(1));
    }
    @Test public void occupiedDragSwapsWithoutLosingEitherItem()
    {
        StorageLayout l=new StorageLayout(Arrays.asList(100,200,300)); l.swap(0,2);
        assertEquals(Arrays.asList(300,200,100),l.items());
    }
    @Test public void newItemsAppendWithoutFillingIntentionalGaps()
    {
        StorageLayout l=new StorageLayout(Arrays.asList(100,-1,300)); l.remember(Arrays.asList(100,400));
        assertEquals(Arrays.asList(100,-1,300,400),l.items());
    }
    @Test public void separateVariantsNeverShareAWithdrawalSlot()
    {
        StorageLayout l=new StorageLayout(); l.remember(Arrays.asList(2434,139,141,143));
        assertEquals(4,l.size());
    }
    @Test public void teamExportRoundTripPreservesAllPositions()
    {
        StorageLayout l=new StorageLayout(Arrays.asList(20997,-1,27275,11802));
        assertEquals(l.items(),StorageLayout.decode(l.encode()).items());
    }
    @Test public void invalidImportsAreRejected()
    {
        for(String text:Arrays.asList("", "GSL2:100", "GSL1:100,100", "GSL1:0", "GSL1:-2", "GSL1:100,", "GSL1:abc", "GSL1:1000001"))
        {
            try { StorageLayout.decode(text); fail("Accepted: "+text); }
            catch(IllegalArgumentException expected) { }
        }
    }
    @Test public void randomizedWithdrawalsDoNotEraseOrReassignItems()
    {
        StorageLayout l=new StorageLayout(); List<Integer> ids=new ArrayList<>();
        for(int i=1;i<=200;i++) ids.add(i);
        l.remember(ids); Random random=new Random(5234);
        for(int i=0;i<1000;i++)
        {
            List<Integer> before=l.items();
            Collections.shuffle(ids,random); l.remember(ids.subList(0,random.nextInt(ids.size())));
            assertEquals(before,l.items());
            l.swap(random.nextInt(200),random.nextInt(220));
            Set<Integer> actual=new HashSet<>(l.items()); actual.remove(-1);
            assertEquals(new HashSet<>(ids),actual);
        }
    }
    @Test public void capacityFailureDoesNotPartlyModifyLayout()
    {
        List<Integer> full=new ArrayList<>(); for(int i=1;i<=StorageLayout.MAX_SLOTS;i++) full.add(i);
        StorageLayout l=new StorageLayout(full);
        try { l.remember(Arrays.asList(9999)); fail(); } catch(IllegalArgumentException expected) { }
        assertEquals(full,l.items());
    }
}
