package com.docsultan.groupstorage;

import java.awt.Rectangle;
import net.runelite.api.widgets.Widget;
import org.junit.Test;
import static org.mockito.Mockito.*;

public class WidgetPlacementTest
{
    @Test public void movingAndRestoringNeverRewritesNativeActionIdentity()
    {
        Widget w=mock(Widget.class);
        when(w.getOriginalX()).thenReturn(8); when(w.getOriginalY()).thenReturn(36);
        when(w.getDragDeadTime()).thenReturn(5); when(w.isSelfHidden()).thenReturn(false);
        WidgetPlacement placement=new WidgetPlacement(w);
        placement.place(new Rectangle(104,108,36,32)); placement.restore();
        verify(w).setOriginalX(104); verify(w).setOriginalY(108);
        verify(w).setOriginalX(8); verify(w).setOriginalY(36);
        verify(w).setDragDeadTime(Integer.MAX_VALUE); verify(w).setDragDeadTime(5);
        verify(w,never()).setItemId(anyInt()); verify(w,never()).setItemQuantity(anyInt());
        verify(w,never()).clearActions(); verify(w,never()).setChildren(any());
        verify(w,never()).setOnOpListener(any(Object[].class));
    }
}
