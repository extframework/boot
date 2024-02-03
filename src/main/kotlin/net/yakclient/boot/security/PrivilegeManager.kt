package net.yakclient.boot.security

public class PrivilegeManager(
    public val parent: PrivilegeManager?,
    _privileges: Privileges,
    public val granter: PrivilegeGrantRequestHandler = PrivilegeGrantRequestHandler { }
) {
    public val privileges: Privileges = Privileges(_privileges.toMutableList())

    public fun request(privilege: Privilege): Unit = granter.handleRequest(PrivilegeRequest(privilege, this))

    public fun hasPrivilege(privilege: Privilege): Boolean = privilege.checkAccess(this)
}