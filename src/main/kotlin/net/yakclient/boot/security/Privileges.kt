package net.yakclient.boot.security

public class Privileges internal constructor(
    private val delegate: MutableList<Privilege> = ArrayList()
) : List<Privilege> by delegate {
    public fun add(privilege: Privilege) {
        if (!PrivilegeAccess.hasPrivilege(privilege)) throw SecurityException("Cannot add: ${privilege.name} to privileges as caller does not have sufficient privileges!")

        delegate.add(privilege)
    }
}

//@JvmName("toPermissionCollectionExtension")
//@Suppress(UNUSED_INLINE)
//public inline fun Privileges.toPermissionCollection(): PermissionCollection = toPermissionCollection(this)
//
//public fun Permission.toPrivilege(): Privilege = PermissionPrivilege(this)
//
//private class JavaPermissionWrapper(
//    val privilege: Privilege
//) : Permission(privilege.name) {
//    override fun equals(other: Any?): Boolean = privilege == other
//
//    override fun hashCode(): Int = privilege.hashCode()
//
//    override fun implies(permission: Permission): Boolean = false
//
//    override fun getActions(): String = name
//
//    override fun checkGuard(`object`: Any) {
//        val codeSource = `object`::class.java.protectionDomain.codeSource as? SecuredSource ?: return
//
//        if (!privilege.checkAccess(codeSource.manager)) throw SecurityException("Access to privilege: '$name' denied to '$`object`'")
//    }
//}
//
//public fun Privilege.toPermission(): Permission =
//    if (this is PermissionPrivilege) permission else JavaPermissionWrapper(this)
//
//public fun toPermissionCollection(list: Privileges): PermissionCollection =
//    Permissions().apply { list.forEach { add( it.toPermission()) } }