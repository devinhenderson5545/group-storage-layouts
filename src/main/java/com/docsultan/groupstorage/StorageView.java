package com.docsultan.groupstorage;

import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.*;

/** Immutable snapshot shared with the AWT mouse listener and overlay. */
final class StorageView
{
    final Rectangle bounds;
    final int scrollY, visibleSlots;
    final GridGeometry grid;
    final List<Integer> items;
    final Map<Integer, BufferedImage> missing;
    final long epoch;
    final boolean arranging;

    StorageView(Rectangle bounds,int scrollY,int visibleSlots,GridGeometry grid,List<Integer> items,
                Map<Integer,BufferedImage> missing,long epoch,boolean arranging)
    {
        this.bounds=new Rectangle(bounds); this.scrollY=scrollY; this.visibleSlots=visibleSlots;
        this.grid=grid; this.items=items; this.missing=Collections.unmodifiableMap(new HashMap<>(missing));
        this.epoch=epoch; this.arranging=arranging;
    }
    int slotAt(int x,int y)
    {
        if(!bounds.contains(x,y)) return -1;
        int slot=grid.slotAt(x-bounds.x,y-bounds.y+scrollY);
        return slot>=0 && slot<visibleSlots?slot:-1;
    }
    int itemAt(int slot) { return slot>=0 && slot<items.size()?items.get(slot):-1; }
    Rectangle cell(int slot)
    {
        Rectangle r=grid.cell(slot); r.translate(bounds.x,bounds.y-scrollY); return r;
    }
}
