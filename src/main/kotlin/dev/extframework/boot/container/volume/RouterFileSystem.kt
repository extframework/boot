package dev.extframework.boot.container.volume

import java.nio.file.*
import java.nio.file.attribute.UserPrincipalLookupService
import java.nio.file.spi.FileSystemProvider

public class RouterFileSystem(
    private val delegate: FileSystem,
    private val rules: RouterRules
) : FileSystem() {
    override fun close(): Unit = delegate.close()

    override fun provider(): FileSystemProvider = delegate.provider()

    override fun isOpen(): Boolean = delegate.isOpen

    override fun isReadOnly(): Boolean = delegate.isReadOnly

    override fun getSeparator(): String = delegate.separator

    override fun getRootDirectories(): MutableIterable<Path> = delegate.rootDirectories

    override fun getFileStores(): MutableIterable<FileStore> = delegate.fileStores

    override fun supportedFileAttributeViews(): MutableSet<String> = delegate.supportedFileAttributeViews()

    override fun getPath(first: String, vararg more: String?): Path {
        val path = delegate.getPath(first, *more)

        val rule = rules.rules
            .map { it.classifier.classify(path) to it }
            .filter { it.first.couldClassify }
            .takeIf { it.isNotEmpty() }
            ?.reduce { f, s -> if (f.first.isMoreSpecific(s.first)) f else s }?.second

        return rule?.associatedVolume?.fs?.getPath(first, *more) ?: path
    }

    override fun getPathMatcher(syntaxAndPattern: String?): PathMatcher = delegate.getPathMatcher(syntaxAndPattern)

    override fun getUserPrincipalLookupService(): UserPrincipalLookupService = delegate.userPrincipalLookupService

    override fun newWatchService(): WatchService = delegate.newWatchService()
}