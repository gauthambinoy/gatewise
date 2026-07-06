package com.gatewise.gateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Multi-region high-availability settings.
 *
 * <p>When {@link Replica#enabled()} is true, read-only transactions ({@code @Transactional(readOnly
 * = true)}) are routed to a read replica and every write goes to the primary, taking read load off
 * the primary database (see {@code gateway/docs/HA.md}). Off by default: with no replica configured
 * the gateway uses Spring Boot's normal single datasource unchanged, so existing behaviour is
 * identical.
 *
 * @param replica the read-replica datasource; a disabled instance when unset
 */
@ConfigurationProperties(prefix = "gatewise.ha")
public record HaProperties(Replica replica) {

  public HaProperties {
    if (replica == null) {
      replica = new Replica(false, null, null, null);
    }
  }

  /**
   * A read replica of the primary database. Routing to it is opt-in and read-only — writes always
   * go to the primary, so the replica only needs to be an asynchronously-replicated read copy.
   *
   * @param enabled route read-only transactions to this replica when true
   * @param url JDBC URL of the read replica (e.g. {@code jdbc:postgresql://replica:5432/gatewise})
   * @param username replica login user
   * @param password replica login password
   */
  public record Replica(boolean enabled, String url, String username, String password) {}
}
