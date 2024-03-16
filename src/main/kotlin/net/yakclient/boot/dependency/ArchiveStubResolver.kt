package net.yakclient.boot.dependency

import com.durganmcbroom.artifact.resolver.ArtifactStub
import net.yakclient.boot.archive.ArchiveException
import kotlin.reflect.KClass

//public interface ArchiveStubResolver<T : ArtifactStub<*, *>, N: Any> {
//    public val stubType: KClass<T>
//    public val nodeType: KClass<N>
//
//    public fun resolve(stub: T): Either<ArchiveException, N>
//}