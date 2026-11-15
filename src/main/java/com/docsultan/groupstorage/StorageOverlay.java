package com.docsultan.groupstorage;

import java.awt.*;
import net.runelite.client.ui.overlay.*;

final class StorageOverlay extends Overlay
{
    private final GroupStoragePlugin plugin;
    StorageOverlay(GroupStoragePlugin plugin)
    {
        this.plugin=plugin;
        setPosition(OverlayPosition.DYNAMIC);
        setLayer(OverlayLayer.ABOVE_WIDGETS);
    }
    @Override public Dimension render(Graphics2D graphics)
    {
        StorageView view=plugin.view;
        if(view==null) return null;
        Graphics2D g=(Graphics2D)graphics.create();
        try
        {
            g.clip(view.bounds);
            for(int i=0;i<view.visibleSlots;i++)
            {
                Rectangle r=view.cell(i);
                if(!r.intersects(view.bounds)) continue;
                if(view.arranging)
                {
                    g.setColor(new Color(255,210,100,65)); g.drawRect(r.x-2,r.y-2,view.grid.stepX-2,view.grid.stepY-2);
                }
                int id=view.itemAt(i);
                if(id>0 && view.missing.containsKey(id))
                {
                    g.setComposite(AlphaComposite.SrcOver.derive(0.35f));
                    g.drawImage(view.missing.get(id),r.x,r.y,null);
                    g.setComposite(AlphaComposite.SrcOver);
                    g.setColor(Color.LIGHT_GRAY); g.drawString("0",r.x,r.y+10);
                }
            }
            if(view.arranging && plugin.mouse.dragSlot>=0 && plugin.mouse.pointer!=null)
            {
                int target=view.slotAt(plugin.mouse.pointer.x,plugin.mouse.pointer.y);
                if(target>=0)
                {
                    Rectangle r=view.cell(target);
                    g.setColor(Color.YELLOW); g.setStroke(new BasicStroke(2));
                    g.drawRect(r.x-2,r.y-2,view.grid.stepX-2,view.grid.stepY-2);
                }
            }
        }
        finally { g.dispose(); }
        return null;
    }
}
