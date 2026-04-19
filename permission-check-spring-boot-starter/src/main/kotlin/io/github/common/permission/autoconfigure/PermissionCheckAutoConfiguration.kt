package io.github.common.permission.autoconfigure

import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.context.annotation.Import

/**
 * Auto-configuration that activates when AspectJ is available.
 * Provides sensible defaults for PrincipalIdExtractor and PermissionRepository,
 * but allows applications to override with custom implementations.
 */
@AutoConfiguration
@ConditionalOnClass(name = ["org.aspectj.lang.annotation.Aspect"])
@Import(PermissionCheckConfiguration::class)
class PermissionCheckAutoConfiguration