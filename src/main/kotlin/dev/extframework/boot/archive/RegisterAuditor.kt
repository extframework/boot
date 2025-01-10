package dev.extframework.boot.archive

import dev.extframework.boot.audit.Auditors

public interface RegisterAuditor {
    public fun register(auditors: Auditors) : Auditors
}