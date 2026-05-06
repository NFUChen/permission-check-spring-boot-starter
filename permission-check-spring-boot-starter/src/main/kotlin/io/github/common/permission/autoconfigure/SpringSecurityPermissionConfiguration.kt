package io.github.common.permission.autoconfigure

import io.github.common.permission.provider.DefaultPermissionRepository
import io.github.common.permission.provider.DefaultPrincipalIdExtractor
import io.github.common.permission.provider.PermissionRepository
import io.github.common.permission.provider.PrincipalIdExtractor
import io.github.common.permission.system.SystemAuthenticationContext
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * Spring Security-dependent beans, isolated in a separate configuration class
 * so that the classloader never attempts to resolve Spring Security types
 * when Spring Security is not on the classpath.
 */
@Configuration
@ConditionalOnClass(name = ["org.springframework.security.core.context.SecurityContextHolder"])
class SpringSecurityPermissionConfiguration {

    /**
     * Provide default PrincipalIdExtractor if none is implemented by the application.
     * Requires principals to implement PrincipalIdentity interface.
     */
    @Bean
    @ConditionalOnMissingBean
    fun defaultPrincipalIdExtractor(): PrincipalIdExtractor {
        return DefaultPrincipalIdExtractor()
    }

    /**
     * Provide default PermissionRepository if none is implemented by the application.
     * This converts Spring Security roles to permissions using common patterns.
     */
    @Bean
    @ConditionalOnMissingBean
    fun defaultPermissionRepository(): PermissionRepository {
        return DefaultPermissionRepository()
    }

    /**
     * Provide SystemAuthenticationContext for executing code blocks
     * with elevated or custom permissions via the Spring SecurityContext.
     */
    @Bean
    @ConditionalOnMissingBean
    fun systemAuthenticationContext(): SystemAuthenticationContext {
        return SystemAuthenticationContext()
    }
}