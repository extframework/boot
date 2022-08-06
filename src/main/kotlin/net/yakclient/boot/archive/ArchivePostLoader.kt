package net.yakclient.boot.archive

public interface ArchivePostLoader<in I: ArchiveLoader.PostLoadedArchive, out O : ArchiveNode> {
    public fun postLoad(archive: I) : O
}
