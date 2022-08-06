package net.yakclient.boot.security

import net.yakclient.boot.loader.SourceDefiner
import java.security.ProtectionDomain

public fun SecureSourceDefiner(manager: PrivilegeManager): SourceDefiner {
   val domain = ProtectionDomain(SecuredSource(manager), manager.permissions)

   return SourceDefiner { name, bb, _, definer ->
      definer(name, bb, domain)
   }
}