package com.docsultan.groupstorage;

import net.runelite.api.widgets.Widget;
import java.awt.Rectangle;

/** Only geometry/visibility/drag timing change. Native IDs, indices, actions and listeners stay intact. */
final class WidgetPlacement
{
    final Widget widget;
    final int x,y,dragTime;
    final boolean hidden;
    WidgetPlacement(Widget widget)
    {
        this.widget=widget;
        x=widget.getOriginalX(); y=widget.getOriginalY();
        hidden=widget.isSelfHidden(); dragTime=widget.getDragDeadTime();
    }
    void place(Rectangle cell)
    {
        widget.setOriginalX(cell.x); widget.setOriginalY(cell.y);
        widget.setHidden(false);
        // Layout dragging is handled locally in Arrange mode. Never send native storage reorder drags.
        widget.setDragDeadTime(Integer.MAX_VALUE);
        widget.revalidate();
    }
    void restore()
    {
        widget.setOriginalX(x); widget.setOriginalY(y);
        widget.setHidden(hidden); widget.setDragDeadTime(dragTime); widget.revalidate();
    }
}
