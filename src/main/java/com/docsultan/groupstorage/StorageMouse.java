package com.docsultan.groupstorage;

import java.awt.Point;
import java.awt.event.MouseEvent;
import net.runelite.client.input.MouseListener;

final class StorageMouse implements MouseListener
{
    private final GroupStoragePlugin plugin;
    private StorageView pressedView;
    private int source=-1;
    private Point press;
    volatile Point pointer;
    volatile int dragSlot=-1;
    private boolean captured;
    private boolean passThrough;

    StorageMouse(GroupStoragePlugin plugin) { this.plugin=plugin; }
    void cancel() { pressedView=null; source=-1; press=null; dragSlot=-1; }

    @Override public MouseEvent mousePressed(MouseEvent e)
    {
        StorageView v=plugin.view;
        int slot=v==null?-1:v.slotAt(e.getX(),e.getY());
        passThrough=plugin.isMenuOpen() || (e.getButton()==MouseEvent.BUTTON3 && v!=null
            && v.missing.containsKey(v.itemAt(slot)) && v.cell(slot).contains(e.getPoint()));
        if(passThrough) return e; // Let the native context menu open and accept its selection.
        if(v!=null && v.arranging && v.bounds.contains(e.getPoint()))
        {
            captured=true;
            e.consume(); // Includes right/middle clicks: edit mode cannot interact with storage items.
            if(e.getButton()==MouseEvent.BUTTON1)
            {
                pressedView=v; source=v.slotAt(e.getX(),e.getY()); press=e.getPoint();
                if(v.itemAt(source)>0) { dragSlot=source; pointer=e.getPoint(); }
            }
        }
        return e;
    }
    @Override public MouseEvent mouseReleased(MouseEvent e)
    {
        if(passThrough) return e;
        if(captured)
        {
            e.consume(); captured=false;
            StorageView current=plugin.view;
            if(pressedView!=null && current!=null && current.arranging && current.epoch==pressedView.epoch
                && source>=0 && press!=null && press.distance(e.getPoint())>=5)
            {
                int target=current.slotAt(e.getX(),e.getY());
                if(target>=0) plugin.move(source,target,pressedView.itemAt(source),pressedView.epoch);
            }
            cancel();
        }
        return e;
    }
    @Override public MouseEvent mouseDragged(MouseEvent e)
    {
        if(captured) { pointer=e.getPoint(); e.consume(); }
        return e;
    }
    @Override public MouseEvent mouseClicked(MouseEvent e)
    {
        if(passThrough) { passThrough=false; return e; }
        StorageView v=plugin.view;
        if(v!=null && v.arranging && v.bounds.contains(e.getPoint())) e.consume();
        return e;
    }
    @Override public MouseEvent mouseMoved(MouseEvent e) { pointer=e.getPoint(); return e; }
    @Override public MouseEvent mouseEntered(MouseEvent e) { return e; }
    @Override public MouseEvent mouseExited(MouseEvent e) { return e; }
}
