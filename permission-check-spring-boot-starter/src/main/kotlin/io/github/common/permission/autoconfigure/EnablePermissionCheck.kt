package io.github.common.permission.autoconfigure

import org.springframework.context.annotation.Import

/**
 * Enable annotation-based permission checking with AOP.
 *
 * Add this annotation to your main application class or any @Configuration class
 * to automatically configure the permission checking system.
 *
 * Requirements:
 * - Your UserDetails principal should implement PrincipalIdentity interface
 * - Implement PermissionRepository interface OR have principals implement PermissionAware
 * - Use @Require annotation on methods that need permission checking
 *
 * Example:
 * ```
 * @SpringBootApplication
 * @EnablePermissionCheck
 * class MyApplication
 * ```
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
@Import(PermissionCheckConfiguration::class)
annotation class EnablePermissionCheck