package com.docsultan.groupstorage;

import net.runelite.client.party.messages.PartyMemberMessage;

/** Versioned layout data only. The Party transport supplies memberId. */
public final class GroupStorageLayoutSyncMessage extends PartyMemberMessage
{
    public int protocol=1;
    public String group, kind, session, token, layout, error;
    public long revision, recipient;
    public int source, target, item;
    public int[] observed;
}
