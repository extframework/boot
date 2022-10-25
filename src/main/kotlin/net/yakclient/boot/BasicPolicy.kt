package net.yakclient.boot

import net.yakclient.boot.container.ContainerSource
import java.security.*


internal class BasicPolicy : Policy() {
    private val permissions: PermissionCollection = Permissions()
    init {
        permissions.add(AllPermission())
    }

    override fun getPermissions(codesource: CodeSource?): PermissionCollection =
        if (codesource !is ContainerSource) permissions else codesource.handle.handle.privilegeManager.permissions
}