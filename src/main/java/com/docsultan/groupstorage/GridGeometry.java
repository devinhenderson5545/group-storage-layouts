package com.docsultan.groupstorage;

import java.awt.Rectangle;
import java.util.*;

/** Infer the native grid instead of assuming a fixed window size or column count. */
final class GridGeometry
{
    final int startX, startY, stepX, stepY, columns, itemWidth, itemHeight;
    GridGeometry(int startX, int startY, int stepX, int stepY, int columns, int itemWidth, int itemHeight)
    {
        this.startX=startX; this.startY=startY; this.stepX=stepX; this.stepY=stepY;
        this.columns=columns; this.itemWidth=itemWidth; this.itemHeight=itemHeight;
    }

    static GridGeometry infer(List<Rectangle> cells, int containerWidth)
    {
        if (cells.isEmpty()) throw new IllegalArgumentException("Open storage with at least two items to capture its grid");
        TreeSet<Integer> xs = new TreeSet<>(), ys = new TreeSet<>();
        for (Rectangle r : cells) { xs.add(r.x); ys.add(r.y); }
        int width=cells.get(0).width, height=cells.get(0).height;
        if (xs.size() < 2 || ys.size() < 2)
            throw new IllegalArgumentException("Capture storage with at least two rows of items so its spacing can be verified");
        int dx = step(xs), dy = step(ys);
        if (dx < width || dy < height || width <= 0 || height <= 0)
            throw new IllegalArgumentException("Unrecognised storage grid");
        int columns = 1 + (containerWidth - xs.first() - width) / dx;
        if (columns < 1 || columns > 32) throw new IllegalArgumentException("Unrecognised storage width");
        GridGeometry grid = new GridGeometry(xs.first(), ys.first(), dx, dy, columns, width, height);
        for (Rectangle r : cells)
            if ((r.x-grid.startX)%dx != 0 || (r.y-grid.startY)%dy != 0 || r.width!=width || r.height!=height)
                throw new IllegalArgumentException("Storage items do not form a regular grid");
        return grid;
    }
    private static int step(TreeSet<Integer> values)
    {
        int min=Integer.MAX_VALUE, prev=values.first();
        for (int value : values) { if(value>prev) min=Math.min(min,value-prev); prev=value; }
        return min;
    }
    Rectangle cell(int position)
    {
        return new Rectangle(startX+(position%columns)*stepX, startY+(position/columns)*stepY,itemWidth,itemHeight);
    }
    int slotAt(int x, int y)
    {
        x -= startX; y -= startY;
        if(x<0 || y<0 || x>=columns*stepX) return -1;
        int slot=(y/stepY)*columns+x/stepX;
        return slot<StorageLayout.MAX_SLOTS?slot:-1;
    }
    int scrollHeight(int slots) { return startY + ((slots+columns-1)/columns)*stepY; }
}
