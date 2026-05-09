package io.github.common.permission.service

import org.slf4j.LoggerFactory
import java.util.*

/**
 * Default implementation of PermissionEvaluator with support for wildcard and self-scope matching.
 *
 * Supported permission patterns (full access):
 * - Direct match: "orders:read" matches "orders:read"
 * - Domain wildcards: "orders:*" matches "orders:read", "orders:create", etc.
 * - Action wildcards: "*:read" matches "orders:read", "users:read", etc.
 * - System-wide: "*:*" matches everything (typically admin-only)
 *
 * Self-scope patterns (ownership check required):
 * - Direct self: "orders:read:self" — user can only read their own orders
 * - Domain wildcard self: "orders:*:self" — user can do anything on their own orders
 * - Action wildcard self: "*:read:self" — user can read their own resources across all domains
 *
 * Full permissions ("domain:action") are a superset of self-scoped permissions ("domain:action:self").
 * If a user holds both, the full permission takes precedence and no ownership check is needed.
 */
class DefaultPermissionEvaluator(
    private val permissionService: PermissionService
) : PermissionEvaluator {

    private val logger = LoggerFactory.getLogger(DefaultPermissionEvaluator::class.java)

    override fun evaluatePermission(userId: UUID, permission: String): PermissionResult {
        logger.debug("Evaluating permission '{}' for user: {}", permission, userId)

        val userPermissions = permissionService.getUserPermissions(userId)
        val result = matchesPermission(userPermissions, permission)

        if (result == PermissionResult.DENIED) {
            logger.debug("Permission '{}' denied for user: {} - Available: {}", permission, userId, userPermissions)
        } else {
            logger.debug("Permission '{}' {} for user: {}", permission, result, userId)
        }

        return result
    }

    private fun matchesPermission(userPermissions: Set<String>, requiredPermission: String): PermissionResult {
        // System-wide permissions (admin access)
        if (userPermissions.contains("*:*")) {
            return PermissionResult.GRANTED
        }

        // Parse required permission — expected format: "domain:action"
        val parts = requiredPermission.split(":", limit = 2)
        if (parts.size != 2) {
            return PermissionResult.DENIED
        }

        val domain = parts[0]
        val action = parts[1]

        // Check full (non-self) permissions first — full permission is a superset of self
        if (hasFullPermission(userPermissions, domain, action)) {
            return PermissionResult.GRANTED
        }

        // Check self-scoped permissions
        if (hasSelfPermission(userPermissions, domain, action)) {
            return PermissionResult.GRANTED_SELF_ONLY
        }

        return PermissionResult.DENIED
    }

    private fun hasFullPermission(userPermissions: Set<String>, domain: String, action: String): Boolean {
        // Direct match
        if (userPermissions.contains("$domain:$action")) return true
        // Domain wildcard: "domain:*"
        if (userPermissions.contains("$domain:*")) return true
        // Action wildcard: "*:action"
        if (userPermissions.contains("*:$action")) return true
        return false
    }

    private fun hasSelfPermission(userPermissions: Set<String>, domain: String, action: String): Boolean {
        // Direct self match: "domain:action:self"
        if (userPermissions.contains("$domain:$action:self")) return true
        // Domain wildcard self: "domain:*:self"
        if (userPermissions.contains("$domain:*:self")) return true
        // Action wildcard self: "*:action:self"
        if (userPermissions.contains("*:$action:self")) return true
        return false
    }
}
