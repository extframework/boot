package net.yakclient.boot.security

import java.net.URI
import java.security.CodeSource
import java.security.cert.Certificate

public data class SecuredSource(
    val manager: PrivilegeManager,
    private val location: URI
) : CodeSource(location.toURL(), arrayOf<Certificate>())