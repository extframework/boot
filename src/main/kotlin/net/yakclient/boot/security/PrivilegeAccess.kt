package net.yakclient.boot.security

import net.yakclient.boot.container.callerContainer

public object PrivilegeAccess {
    public fun hasPrivilege(privilege: Privilege): Boolean {
        return callerContainer()?.privilegeManager?.hasPrivilege(privilege) ?: true
    }

    public fun createPrivileges(vararg privileges: Privilege): Privileges {
        if (privileges.isEmpty()) return Privileges(mutableListOf())

        return createPrivileges(privileges.toList())
    }

    public fun createPrivileges(privileges: List<Privilege>): Privileges {
        check(privileges.all(PrivilegeAccess::hasPrivilege)) { "Insufficient privileges to create: ${privileges.joinToString { it.name }}" }

        return Privileges(privileges.toMutableList())
    }

    public fun emptyPrivileges() : Privileges = Privileges(ArrayList())

    public fun allPrivileges(): Privileges {
        val privilege = AllPrivilege()
        if (!hasPrivilege(privilege)) throw SecurityException("Insufficient privileges to create all privileges!")

        return Privileges(mutableListOf(privilege))
    }
}

