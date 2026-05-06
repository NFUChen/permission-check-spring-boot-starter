package io.github.common.permission.system

import io.github.common.permission.provider.PermissionAware
import io.github.common.permission.provider.PrincipalIdentity
import java.util.*

/**
 * A synthetic principal representing the system itself (schedulers, event listeners, etc.).
 * Carries `*:*` permissions so that any @Require check passes when running as system.
 */
class SystemPrincipal private constructor() : PrincipalIdentity, PermissionAware {

    companion object {
        val INSTANCE = SystemPrincipal()
        val SYSTEM_ID: UUID = UUID(0, 0)
    }

    override fun getPrincipalId(): UUID = SYSTEM_ID

    override fun getPermissions(): Set<String> = setOf("*:*")
}