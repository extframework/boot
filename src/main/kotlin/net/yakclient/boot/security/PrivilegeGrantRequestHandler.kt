package net.yakclient.boot.security

public fun interface PrivilegeGrantRequestHandler {
    public fun handleRequest(request: PrivilegeRequest)
}