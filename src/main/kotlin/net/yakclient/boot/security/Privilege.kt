package net.yakclient.boot.security


public interface Privilege {
    public val name: String

    public fun checkAccess(manager: PrivilegeManager) : Boolean

    public fun implies(other: Privilege) : Boolean
}