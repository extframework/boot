package com.kaolinmc.boot.archive

import com.kaolinmc.boot.audit.Auditors

public interface RegisterAuditor {
    public fun register(auditors: Auditors) : Auditors
}