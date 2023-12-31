package net.yakclient.boot.security

import net.yakclient.boot.loader.SourceDefiner
import java.net.URI
import java.security.ProtectionDomain

public fun SecureSourceDefiner(manager: PrivilegeManager, location: URI): SourceDefiner {
   val domain = ProtectionDomain(SecuredSource(manager, location), manager.permissions)

   return SourceDefiner { name, bb, _, definer ->
      definer(name, bb, domain)
   }
}