package net.yakclient.boot.archive

import com.durganmcbroom.artifact.resolver.ArtifactRequest

public open class ArchiveKey<T: ArtifactRequest<*>>(
    public val request: T
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ArchiveKey<*>) return false

        if (request != other.request) return false

        return true
    }

    override fun hashCode(): Int {
        return request.hashCode()
    }
}