package net.yakclient.boot.extension

import net.yakclient.boot.ArtifactArchiveKey
import net.yakclient.boot.store.DataStore

public typealias ExtensionStore = DataStore<ArtifactArchiveKey, ExtensionData>