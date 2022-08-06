package net.yakclient.boot.loader

import net.yakclient.boot.security.toPermissionCollection
import net.yakclient.boot.container.ContainerSource
import net.yakclient.boot.security.PrivilegeManager
import java.nio.ByteBuffer
import java.security.ProtectionDomain

//public class ContainerClassLoader(
//    sourceProvider: SourceProvider,
//    privilegeManager: PrivilegeManager,
//    source: ContainerSource,
//    _components: List<ClassProvider>,
//    parent: ClassLoader,
//) : ProvidedClassLoader(
//    sourceProvider,
//    _components,
//    parent
//) {
//    private val domain: ProtectionDomain =
//        ProtectionDomain(source, privilegeManager.privileges.toPermissionCollection(), this, null)
//
//    override fun findLocalClass(name: String, buffer: ByteBuffer): Class<*> = defineClass(name, buffer, domain)
//}