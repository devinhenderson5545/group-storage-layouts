package com.docsultan.groupstorage;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

public final class GroupStorageLauncher
{
    public static void main(String[] args) throws Exception
    {
        ExternalPluginManager.loadBuiltin(GroupStoragePlugin.class);
        RuneLite.main(args);
    }
}
