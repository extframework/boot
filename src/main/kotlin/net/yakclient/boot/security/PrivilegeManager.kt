package net.yakclient.boot.security

import java.security.PermissionCollection

public class PrivilegeManager constructor(
    public val parent: PrivilegeManager?,
    _privileges: Privileges,
    public val granter: PrivilegeGrantRequestHandler = PrivilegeGrantRequestHandler { }
) {
    public val permissions: PermissionCollection = _privileges.toPermissionCollection()

    public val privileges: Privileges = Privileges(run {
        val delegate = _privileges.toMutableList()
        object : MutableList<Privilege> by delegate {
            override fun add(element: Privilege): Boolean {
                permissions.add(element.toPermission())
                return delegate.add(element)
            }
        }

    })

    public fun request(privilege: Privilege): Unit = granter.handleRequest(PrivilegeRequest(privilege, this))

    public fun hasPrivilege(privilege: Privilege): Boolean = privilege.checkAccess(this)
}