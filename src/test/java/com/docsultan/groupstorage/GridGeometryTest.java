package com.docsultan.groupstorage;

import java.awt.Rectangle;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class GridGeometryTest
{
    @Test public void infersNativeSpacingAndColumnCount()
    {
        List<Rectangle> cells=new ArrayList<>();
        for(int i=0;i<16;i++) cells.add(new Rectangle(8+(i%8)*48,(i/8)*36,36,32));
        GridGeometry g=GridGeometry.infer(cells,392);
        assertEquals(8,g.columns); assertEquals(48,g.stepX); assertEquals(36,g.stepY);
        assertEquals(new Rectangle(56,72,36,32),g.cell(17));
    }
    @Test public void hitTestingUsesScrollAndWindowOffset()
    {
        GridGeometry g=new GridGeometry(8,0,48,36,8,36,32);
        StorageView v=new StorageView(new Rectangle(200,100,392,200),72,100,g,Arrays.asList(1,2),Collections.emptyMap(),1,true);
        assertEquals(17,v.slotAt(260,110));
        assertEquals(-1,v.slotAt(199,110));
        assertEquals(-1,v.slotAt(260,301));
        assertEquals(new Rectangle(256,100,36,32),v.cell(17));
    }
    @Test(expected=IllegalArgumentException.class) public void refusesAnUnverifiedSingleRow()
    {
        GridGeometry.infer(Arrays.asList(new Rectangle(8,0,36,32),new Rectangle(56,0,36,32)),392);
    }
}
