package net.yakclient.boot.security

import java.io.FilePermission
import java.security.AllPermission
import java.security.Permission

public open class PermissionPrivilege(
    internal val permission: Permission
) : Privilege {
    override val name: String = permission.name

    override fun checkAccess(manager: PrivilegeManager): Boolean = manager.privileges
        .filterIsInstance<PermissionPrivilege>()
        .filter { permission::class.isInstance(it.permission) }
        .any { permission.implies(it.permission) }

    override fun implies(other: Privilege): Boolean =
        if (other is PermissionPrivilege) permission.implies(other.permission) else false
}

public class AllPrivilege : PermissionPrivilege(AllPermission()) {
    override fun implies(other: Privilege): Boolean = true
}

public class FilePrivilege(path: String, action: List<FileAction>) : PermissionPrivilege(
    FilePermission(
        path,
        action.joinToString(separator = ",", transform = FileAction::internalName)
    )
) {
    public constructor(path: String, vararg actions: FileAction) : this(path, actions.toList())
}

public enum class FileAction(
    internal val internalName: String
) {
    READ("read"),
    WRITE("write"),
    DELETE("delete"),
    EXECUTE("execute"),
    READLINK("readlink"),
    ALL("read,write,delete,execute,readlink")
}

