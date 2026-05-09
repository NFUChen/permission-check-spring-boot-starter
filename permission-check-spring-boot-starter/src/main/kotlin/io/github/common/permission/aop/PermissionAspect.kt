package io.github.common.permission.aop

import io.github.common.permission.annotation.Require
import io.github.common.permission.annotation.ResourceOwner
import io.github.common.permission.annotation.extractPermissions
import io.github.common.permission.autoconfigure.PermissionCheckProperties
import io.github.common.permission.exception.PermissionDeniedException
import io.github.common.permission.provider.OwnedResource
import io.github.common.permission.provider.PrincipalIdExtractor
import io.github.common.permission.service.PermissionEvaluator
import io.github.common.permission.service.PermissionResult
import org.aspectj.lang.ProceedingJoinPoint
import org.aspectj.lang.annotation.Around
import org.aspectj.lang.annotation.Aspect
import org.aspectj.lang.reflect.MethodSignature
import org.slf4j.LoggerFactory
import java.util.*

/**
 * AOP Aspect for intercepting methods annotated with @Require.
 *
 * Supports both full permissions ("domain:action") and self-scoped permissions
 * ("domain:action:self"). When the evaluator returns GRANTED_SELF_ONLY, the aspect
 * looks for a @ResourceOwner-annotated parameter to verify that the current user
 * owns the resource being accessed.
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
        val method = (joinPoint.signature as MethodSignature).method
        val methodName = method.name
        val className = joinPoint.target.javaClass.simpleName
        val currentUserId = principalIdExtractor.extractPrincipalId()

        if (loggingProperties.debugEnabled) {
            logger.debug("Permission check initiated for user={} method={}.{}", currentUserId, className, methodName)
        }

        validatePermissions(currentUserId, require, method, joinPoint)

        if (loggingProperties.debugEnabled) {
            logger.debug("Permission granted for user={} method={}.{}", currentUserId, className, methodName)
        }

        return joinPoint.proceed()
    }

    private fun validatePermissions(
        userId: UUID,
        require: Require,
        method: java.lang.reflect.Method,
        joinPoint: ProceedingJoinPoint
    ) {
        val permissionsToCheck = require.extractPermissions()

        val results = permissionsToCheck.map { permission ->
            permission to permissionEvaluator.evaluatePermission(userId, permission)
        }

        val passed = when (require.requireAll) {
            true -> checkAllPermissions(results, userId, method, joinPoint)
            false -> checkAnyPermission(results, userId, method, joinPoint)
        }

        if (!passed) {
            val permissionsInfo = getPermissionsContext(require)
            if (loggingProperties.auditEnabled) {
                logger.warn("Permission denied for user={} method={}. Required: {}", userId, method.name, permissionsInfo)
            }
            throw PermissionDeniedException(
                userId = userId,
                requiredPermissions = permissionsInfo,
                methodName = method.name
            )
        }
    }

    /**
     * AND logic: all permissions must be granted. If any returns SELF_ONLY,
     * ownership must match for that permission.
     */
    private fun checkAllPermissions(
        results: List<Pair<String, PermissionResult>>,
        userId: UUID,
        method: java.lang.reflect.Method,
        joinPoint: ProceedingJoinPoint
    ): Boolean {
        for ((_, result) in results) {
            when (result) {
                PermissionResult.DENIED -> return false
                PermissionResult.GRANTED -> continue
                PermissionResult.GRANTED_SELF_ONLY -> {
                    if (!verifyOwnership(userId, method, joinPoint)) return false
                }
            }
        }
        return true
    }

    /**
     * OR logic: any permission being granted is sufficient. SELF_ONLY
     * counts as granted only if ownership matches.
     */
    private fun checkAnyPermission(
        results: List<Pair<String, PermissionResult>>,
        userId: UUID,
        method: java.lang.reflect.Method,
        joinPoint: ProceedingJoinPoint
    ): Boolean {
        for ((_, result) in results) {
            when (result) {
                PermissionResult.GRANTED -> return true
                PermissionResult.GRANTED_SELF_ONLY -> {
                    if (verifyOwnership(userId, method, joinPoint)) return true
                }
                PermissionResult.DENIED -> continue
            }
        }
        return false
    }

    /**
     * Extract owner ID from the @ResourceOwner-annotated parameter and compare with userId.
     * Returns false if no @ResourceOwner parameter is found (cannot verify ownership).
     */
    private fun verifyOwnership(
        userId: UUID,
        method: java.lang.reflect.Method,
        joinPoint: ProceedingJoinPoint
    ): Boolean {
        val ownerId = extractResourceOwnerId(method, joinPoint.args)
        if (ownerId == null) {
            logger.debug("No @ResourceOwner parameter found on method={}, denying self-scope access", method.name)
            return false
        }
        return ownerId == userId
    }

    /**
     * Find the parameter annotated with @ResourceOwner and extract the owner UUID.
     * Supports UUID parameters directly and OwnedResource implementations.
     */
    private fun extractResourceOwnerId(method: java.lang.reflect.Method, args: Array<Any>): UUID? {
        val paramAnnotations = method.parameterAnnotations
        for (i in paramAnnotations.indices) {
            val hasResourceOwner = paramAnnotations[i].any { it is ResourceOwner }
            if (!hasResourceOwner) continue

            val arg = args[i]
            return when (arg) {
                is UUID -> arg
                is OwnedResource -> arg.getOwnerId()
                else -> {
                    logger.warn(
                        "@ResourceOwner parameter type {} is not UUID or OwnedResource on method={}",
                        arg.javaClass.simpleName, method.name
                    )
                    null
                }
            }
        }
        return null
    }

    private fun getPermissionsContext(require: Require): String {
        val permissionsToCheck = require.extractPermissions()
        return when {
            permissionsToCheck.size > 1 -> {
                val operator = if (require.requireAll) "AND" else "OR"
                permissionsToCheck.joinToString(" $operator ")
            }
            else -> permissionsToCheck.first()
        }
    }
}
