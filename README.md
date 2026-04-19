# Permission Check Spring Boot Starter

A domain-agnostic Spring Boot starter for annotation-based permission checking with AOP.

## Quick Start

### 1. Add Dependency

#### Gradle (Kotlin DSL)

Add JitPack repository in `settings.gradle.kts`:

```kotlin
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
    }
}
```

Add dependency in `build.gradle.kts`:

```kotlin
dependencies {
    implementation("com.github.NFUChen:permission-check-spring-boot-starter:-SNAPSHOT")
}
```

#### Gradle (Groovy DSL)

Add JitPack repository in `settings.gradle`:

```groovy
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenCentral()
        maven { url 'https://jitpack.io' }
    }
}
```

Add dependency in `build.gradle`:

```groovy
dependencies {
    implementation 'com.github.NFUChen:permission-check-spring-boot-starter:-SNAPSHOT'
}
```

#### Maven

Add JitPack repository in `pom.xml`:

```xml
<repositories>
    <repository>
        <id>jitpack.io</id>
        <url>https://jitpack.io</url>
    </repository>
</repositories>
```

Add dependency:

```xml
<dependency>
    <groupId>com.github.NFUChen</groupId>
    <artifactId>permission-check-spring-boot-starter</artifactId>
    <version>-SNAPSHOT</version>
</dependency>
```

### 2. Enable Permission Checking

```kotlin
@SpringBootApplication
@EnablePermissionCheck
class MyApplication
```

### 3. Use @Require Annotation

```kotlin
@Service
class OrderService {

    @Require("orders:read")
    fun getOrders(): List<Order> { ... }

    @Require("orders:create")
    fun createOrder(order: Order): Order { ... }

    @Require(permissions = ["orders:read", "users:read"], requireAll = true)
    fun getOrdersWithUsers(): OrderUserData { ... }
}
```

**That's it!** Now implement the required interfaces on your User/Principal:

1. **Required**: Implement `PrincipalIdentity` to provide user UUID
2. **Choose one**:
   - Implement `PermissionAware` to provide permissions directly, OR
   - Provide a custom `PermissionRepository` bean

See Default Behavior section below for examples.

## Default Behavior

The starter requires two things from your application:

### 1. Principal Identity (Required)

Your principal **must** implement `PrincipalIdentity` to provide a UUID identifier:

```kotlin
import io.github.common.permission.provider.PrincipalIdentity

class MyUser(
    val id: UUID,
    private val username: String,
    // ... other properties
) : UserDetails, PrincipalIdentity {

    override fun getPrincipalId(): UUID = id

    // UserDetails methods...
}
```

### 2. Permissions (Choose one approach)

**Option A: PermissionAware interface** (simpler)

```kotlin
class MyUser : UserDetails, PrincipalIdentity, PermissionAware {
    @ManyToMany
    val roles: Set<Role> = emptySet()

    override fun getPrincipalId(): UUID = id

    override fun getPermissions(): Set<String> {
        return roles.flatMap { it.permissions }.toSet()
    }
}
```

**Option B: Custom PermissionRepository** (more flexible)

See Customization section below.

## Customization

The starter provides three levels of customization, from simplest to most flexible:

### Level 1: PermissionAware Interface

**Best for**: Simple applications where user entities can directly provide permissions

```kotlin
@Entity
class User : UserDetails, PermissionAware {
    @ManyToMany
    val roles: Set<Role> = emptySet()

    override fun getPermissions(): Set<String> {
        return roles.flatMap { it.permissions }.toSet()
    }
}
```

**Pros**: No additional beans needed, works immediately
**Cons**: Couples domain model to permission framework

### Level 2: Custom PermissionRepository

**Best for**: Most applications - separates permission logic from domain model

```kotlin
@Component
@Primary
class ApplicationPermissionRepository(
    private val roleRepository: RoleRepository
) : PermissionRepository {

    override fun getUserPermissions(userId: UUID): Set<String> {
        val roles = roleRepository.findByUserId(userId)
        return roles.flatMap { it.permissions }.toSet()
    }

    override fun evictUserPermissions(userId: UUID) {
        // Optional: Clear cache when permissions change
        cacheManager.getCache("userPermissions")?.evict(userId)
    }
}
```

**Pros**: Clean separation, testable, flexible
**Cons**: Requires one extra class

### Level 3: Custom PrincipalIdExtractor

**Best for**: Applications with non-standard authentication (API keys, custom tokens, etc.)

```kotlin
@Component
@Primary
class CustomPrincipalIdExtractor : PrincipalIdExtractor {

    override fun extractPrincipalId(): UUID {
        val auth = SecurityContextHolder.getContext().authentication
            ?: throw IllegalStateException("Not authenticated")

        return when (val principal = auth.principal) {
            is CustomUser -> principal.userId
            is String -> lookupUserIdFromApiKey(principal)
            else -> throw IllegalStateException("Unsupported principal type")
        }
    }

    private fun lookupUserIdFromApiKey(apiKey: String): UUID {
        // Custom logic to map API key to user ID
        // ...
    }
}
```

**Pros**: Complete control over user identification
**Cons**: Needed only for non-standard setups

### Configuration Properties

```yaml
permission-check:
  cache:
    enabled: true
    cache-name: "userPermissions"
    ttl-seconds: 300
  logging:
    debug-enabled: false
    audit-enabled: true
```

## Permission String Format

The starter uses a simple **two-part format**: `domain:action`

```kotlin
// Basic format (domain:action)
@Require("orders:read")
@Require("users:create")
@Require("billing:manage")

// Wildcard permissions
@Require("orders:*")    // All order actions
@Require("*:read")      // Read any resource
@Require("*:*")         // System admin (all permissions)

// Multiple permissions
@Require(permissions = ["orders:read", "users:read"], requireAll = true)   // AND
@Require(permissions = ["admin:*", "manager:*"], requireAll = false)       // OR
```

**Note**: The format is strictly `domain:action`. Multi-level hierarchies like `domain:subdomain:action` are not supported. Keep permissions flat and simple.

## Exception Handling

```kotlin
@ControllerAdvice
class PermissionExceptionHandler {

    @ExceptionHandler(PermissionDeniedException::class)
    fun handlePermissionDenied(e: PermissionDeniedException): ResponseEntity<*> {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
            .body(mapOf("error" to "Access denied", "details" to e.message))
    }

    @ExceptionHandler(SecurityException::class)
    fun handleNotAuthenticated(e: SecurityException): ResponseEntity<*> {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
            .body(mapOf("error" to "Authentication required"))
    }
}
```

## Examples

### E-commerce Application

```kotlin
@Service
class ProductService {
    @Require("products:read")
    fun getProducts(): List<Product> { ... }

    @Require("products:create")
    fun createProduct(product: Product): Product { ... }

    @Require(permissions = ["products:read", "inventory:read"], requireAll = true)
    fun getProductsWithInventory(): List<ProductInventory> { ... }
}
```

### Multi-tenant SaaS

```kotlin
@Service
class TenantService {
    @Require("tenants:read")
    fun getTenants(): List<Tenant> { ... }

    @Require("tenants:admin")
    fun createTenant(tenant: Tenant): Tenant { ... }

    @Require(permissions = ["billing:read", "tenants:read"], requireAll = true)
    fun getTenantBilling(tenantId: UUID): BillingInfo { ... }
}
```

## Architecture

### Permission Format

The starter enforces a simple **two-part format**: `domain:action`

- **Domain**: Resource or feature area (e.g., "orders", "users", "billing")
- **Action**: Operation type (e.g., "read", "create", "update", "delete", "manage")
- **Wildcards**: Use `*` for any domain or action
  - `orders:*` → All actions on orders
  - `*:read` → Read access to all resources
  - `*:*` → Full system access

### Layered Architecture

The starter uses a clean layered architecture:

**Layer 1 - AOP Interception**
- `PermissionAspect` intercepts `@Require` annotated methods

**Layer 2 - Identity Resolution**
- `PrincipalIdExtractor` extracts UUID from security context
- `PrincipalIdentity` provides the UUID from principal

**Layer 3 - Permission Evaluation**
- `PermissionEvaluator` performs wildcard matching logic

**Layer 4 - Caching (Performance)**
- `PermissionService` provides caching with Spring Cache abstraction
- Default: `CachedPermissionService` with `@Cacheable`

**Layer 5 - Data Access**
- `PermissionRepository` fetches permissions from data source
- Alternative: `PermissionAware` interface on principal (simpler)

### Core Components

1. **@Require Annotation**: Declarative permission checking on methods
2. **PermissionAspect**: AOP interceptor that validates permissions before method execution
3. **PrincipalIdExtractor**: Extracts principal ID from Spring Security context
4. **PrincipalIdentity**: Interface that principals must implement to provide their UUID
5. **PermissionEvaluator**: Evaluates permissions with wildcard matching
6. **PermissionService**: Caching layer for permission data (uses Spring Cache)
7. **PermissionRepository**: Data access interface for user permissions (or PermissionAware for simple cases)

### How It Works

```
Method Call → @Require → PermissionAspect
                              ↓
                     PrincipalIdExtractor.extractPrincipalId()
                              ↓
                    Principal.getPrincipalId() → UUID
                              ↓
                     PermissionEvaluator.hasPermission(uuid, "orders:read")
                              ↓
                     PermissionService.getUserPermissions(uuid)
                              ↓                      ↑
                         [Cache Check]          [Cache Miss]
                              ↓                      ↓
                         [Cache Hit]    PermissionRepository.getUserPermissions(uuid)
                              ↓                      ↓
                              └──────────┬──────────┘
                                         ↓
                               Set<String> permissions
                                         ↓
                           Wildcard Matching & Validation
                                         ↓
              [GRANTED] → Proceed | [DENIED] → PermissionDeniedException
```

## Benefits

- **Simple**: Two-part `domain:action` format is easy to understand and maintain
- **Clear Contracts**: `PrincipalIdentity` for identity, `PermissionAware` for permissions - obvious requirements
- **Flexible**: Three levels of customization (PermissionAware, PermissionRepository, PrincipalIdExtractor)
- **Performance**: Built-in caching with Spring Cache abstraction
- **AOP-Based**: Clean separation of business and security logic
- **Auditable**: Comprehensive logging for security compliance
- **Wildcard Support**: Powerful pattern matching for role-based access
- **Type-Safe**: UUID-based principal identification, no string parsing magic