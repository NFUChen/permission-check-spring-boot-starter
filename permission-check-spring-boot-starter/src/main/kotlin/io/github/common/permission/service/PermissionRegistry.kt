package io.github.common.permission.service

import io.github.common.permission.annotation.Require
import io.github.common.permission.annotation.extractPermissions
import org.springframework.aop.support.AopUtils
import org.springframework.context.ApplicationContext

/**
 * Collects all permissions defined via @Require annotations across all Spring beans at startup.
 *
 * After application context initialization, [definedPermissions] contains the complete set
 * of unique permission strings found in the codebase. Useful for:
 * - Generating permission documentation
 * - Building admin UIs for permission assignment
 * - Validating database permission data against code
 * - Exposing via Actuator endpoints
 */
class PermissionRegistry(
    private val applicationContext: ApplicationContext
) {

    fun getAllPermissions(): Set<String> {
        val permissions = mutableSetOf<String>()
        for (beanName in applicationContext.beanDefinitionNames) {
            applicationContext.getType(beanName) ?: continue
            val bean = try {
                applicationContext.getBean(beanName)
            } catch (_: Exception) {
                continue
            }

            val targetClass = AopUtils.getTargetClass(bean)
            for (cls in listOf(targetClass) + targetClass.interfaces) {
                for (method in cls.methods) {
                    val require = method.getAnnotation(Require::class.java) ?: continue
                    permissions.addAll(require.extractPermissions())
                }
            }
        }
        return permissions
    }
}
