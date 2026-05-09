package io.github.common.permission.service

import java.util.*

/**
 * Interface for evaluating whether a user has specific permissions.
 */
interface PermissionEvaluator {
    /*** Check if a user has the required permission (backward-compatible).
     * Returns true for both GRANTED and GRANTED_SELF_ONLY.
     * @param userId User identifier
     * @param permission Permission string to check
     * @return true if user has permission (any scope), false otherwise
     */
    fun hasPermission(userId: UUID, permission: String): Boolean {
        return evaluatePermission(userId, permission) != PermissionResult.DENIED
    }

    /**
     * Evaluate a user's permission with scope awareness.
     * @param userId User identifier
     * @param permission Permission string to check (format: "domain:action")
     * @return PermissionResult indicating DENIED, GRANTED, or GRANTED_SELF_ONLY
     */
    fun evaluatePermission(userId: UUID, permission: String): PermissionResult
}
