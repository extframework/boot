package net.yakclient.boot.security

import java.security.CodeSource
import java.security.cert.Certificate

public data class SecuredSource(
    val manager: PrivilegeManager
) : CodeSource(null, arrayOf<Certificate>())