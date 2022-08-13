package net.yakclient.boot.extension

import net.yakclient.boot.DescriptorKey
import net.yakclient.boot.archive.ArchiveKey
import net.yakclient.boot.store.DataAccess
import net.yakclient.boot.store.DataStore
import net.yakclient.boot.store.DelegatingDataStore

public class ExtensionStore<K : ArchiveKey, T : ExtensionData>(access: DataAccess<K, T>) :
    DelegatingDataStore<K, T>(access)