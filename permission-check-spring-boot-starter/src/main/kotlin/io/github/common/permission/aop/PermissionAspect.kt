package io.github.common.permission.aop

import io.github.common.permission.annotation.Require
import io.github.common.permission.annotation.extractPermissions
import io.github.common.permission.autoconfigure.PermissionCheckProperties
import io.github.common.permission.exception.PermissionDeniedException
import io.github.common.permission.provider.PrincipalIdExtractor
import io.github.common.permission.service.PermissionEvaluator
import org.aspectj.lang.ProceedingJoinPoint
import org.aspectj.lang.annotation.Around
import org.aspectj.lang.annotation.Aspect
import org.aspectj.lang.reflect.MethodSignature
import org.slf4j.LoggerFactory
import java.util.*

/**
 * AOP Aspect for intercepting methods annotated with @Require.
 * This aspect is completely domain-agnostic and works with any user model
 * through the PrincipalIdExtractor interface.
 */
@Aspect
class PermissionAspect(
    private val permissionEvaluator: PermissionEvaluator,
    private val principalIdExtractor: PrincipalIdExtractor,
    private val loggingProperties: PermissionCheckProperties.LoggingProperties
) {
    private val logger = LoggerFactory.getLogger(PermissionAspect::class.java)

    @Around("@annotation(require)")
    fun around(
        joinPoint: ProceedingJoinPoint,
        require: Require
    ): Any? {
        val methodName = (joinPoint.signature as MethodSignature).method.name
        val className = joinPoint.target.javaClass.simpleName

        val currentUserId = principalIdExtractor.extractPrincipalId()

        if (loggingProperties.debugEnabled) {
            logger.debug("Permission check initiated for user=$currentUserId method=$className.$methodName")
        }

        validatePermissions(currentUserId, require, methodName)

        if (loggingProperties.debugEnabled) {
            logger.debug("Permission granted for user=$currentUserId method=$className.$methodName")
        }

        return joinPoint.proceed()
    }

    private fun validatePermissions(userId: UUID, require: Require, methodName: String) {
        val hasPermission = checkPermissions(userId, require)
        if (hasPermission) {
            return
        }

        val permissionsInfo = getPermissionsContext(require)

        if (loggingProperties.auditEnabled) {
            logger.warn("Permission denied for user=$userId method=$methodName. Required: $permissionsInfo")
        }

        throw PermissionDeniedException(
            userId = userId,
            requiredPermissions = permissionsInfo,
            methodName = methodName
        )
    }

    /**
     * Check user permissions against the @Require annotation.
     * Supports both single and multiple permission checking with AND/OR logic.
     */
    private fun checkPermissions(userId: UUID, require: Require): Boolean {
        val permissionsToCheck = require.extractPermissions()

        return when (require.requireAll) {
            true -> {
                // AND logic - requires all permissions
                for (permission in permissionsToCheck) {
                    if (!permissionEvaluator.hasPermission(userId, permission)) {
                        return false
                    }
                }
                true
            }
            false -> {
                // OR logic - any permission is sufficient
                for (permission in permissionsToCheck) {
                    if (permissionEvaluator.hasPermission(userId, permission)) {
                        return true
                    }
                }
                false
            }
        }
    }

    /**
     * Get permission information string for logging and error messages.
     */
    private fun getPermissionsContext(require: Require): String {
        val permissionsToCheck = require.extractPermissions()

        return when {
            permissionsToCheck.size > 1 -> {
                val operator = require.getOperatorString()
                permissionsToCheck.joinToString(" $operator ")
            }
            else -> permissionsToCheck.first()
        }
    }

    private fun Require.getOperatorString(): String =
        when (requireAll) {
            true -> "AND"
            false -> "OR"
        }
}