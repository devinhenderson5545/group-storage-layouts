package com.docsultan.groupstorage;

import java.util.*;

/** Exact item IDs, not container indices. Empty positions are -1. */
final class StorageLayout
{
    static final int MAX_SLOTS = 2048;
    private final List<Integer> slots;

    StorageLayout() { this.slots = new ArrayList<>(); }
    StorageLayout(List<Integer> slots)
    {
        validate(slots);
        this.slots = new ArrayList<>(slots);
    }
    int size() { return slots.size(); }
    int itemAt(int slot) { return slot >= 0 && slot < slots.size() ? slots.get(slot) : -1; }
    int positionOf(int id) { return slots.indexOf(id); }
    List<Integer> items() { return Collections.unmodifiableList(new ArrayList<>(slots)); }

    boolean remember(Collection<Integer> liveIds)
    {
        return remember(liveIds, 8);
    }

    boolean remember(Collection<Integer> liveIds, int columns)
    {
        if (columns < 1 || columns > MAX_SLOTS) throw new IllegalArgumentException("Invalid grid width");
        LinkedHashSet<Integer> unseen = new LinkedHashSet<>(liveIds);
        unseen.removeIf(id -> id == null || id <= 0 || slots.contains(id));
        if (unseen.isEmpty()) return false;
        int end = slots.size();
        while (end > 0 && slots.get(end - 1) == -1) end--;
        int start = ((end + columns - 1) / columns) * columns;
        if (start + unseen.size() > MAX_SLOTS)
            throw new IllegalArgumentException("Layout is full. Release unused placeholders first.");
        // Ignore trailing empty rows, but keep every item, placeholder and internal gap.
        slots.subList(end, slots.size()).clear();
        while (slots.size() < start) slots.add(-1);
        slots.addAll(unseen);
        return true;
    }

    void swap(int source, int target)
    {
        if (source < 0 || target < 0 || source >= MAX_SLOTS || target >= MAX_SLOTS)
            throw new IllegalArgumentException("Invalid layout position");
        if (itemAt(source) <= 0 || source == target) return;
        while (slots.size() <= Math.max(source, target)) slots.add(-1);
        Collections.swap(slots, source, target);
    }

    void release(int slot)
    {
        if (slot >= 0 && slot < slots.size()) slots.set(slot, -1);
        while (!slots.isEmpty() && slots.get(slots.size() - 1) == -1) slots.remove(slots.size() - 1);
    }

    String encode()
    {
        StringJoiner out = new StringJoiner(",", "GSL1:", "");
        slots.forEach(id -> out.add(Integer.toString(id)));
        return out.toString();
    }

    static StorageLayout decode(String text)
    {
        if (text == null || text.length() > MAX_SLOTS * 12 + 5)
            throw new IllegalArgumentException("Layout text is missing or too long");
        text = text.trim();
        if (!text.startsWith("GSL1:")) throw new IllegalArgumentException("Expected a GSL1 layout export");
        String body = text.substring(5);
        List<Integer> ids = new ArrayList<>();
        if (!body.isEmpty())
        {
            for (String token : body.split(",", -1))
            {
                try { ids.add(Integer.parseInt(token.trim())); }
                catch (NumberFormatException ex) { throw new IllegalArgumentException("Invalid item ID in layout"); }
            }
        }
        return new StorageLayout(ids);
    }

    private static void validate(List<Integer> ids)
    {
        if (ids.size() > MAX_SLOTS) throw new IllegalArgumentException("Too many layout positions");
        Set<Integer> seen = new HashSet<>();
        for (Integer id : ids)
        {
            if (id == null || id == 0 || id < -1 || id > 1_000_000)
                throw new IllegalArgumentException("Invalid item ID");
            if (id > 0 && !seen.add(id)) throw new IllegalArgumentException("Duplicate item ID: " + id);
        }
    }
}
