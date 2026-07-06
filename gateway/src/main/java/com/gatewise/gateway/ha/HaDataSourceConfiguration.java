package com.gatewise.gateway.ha;

import com.gatewise.gateway.config.HaProperties;
import java.util.Map;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.datasource.LazyConnectionDataSourceProxy;

/**
 * Wires read/write datasource splitting, and only when {@code gatewise.ha.replica.enabled=true}.
 *
 * <p>Gating the whole thing on that one switch keeps the default path untouched: when the property
 * is absent or false this {@code @Configuration} contributes no beans, so Spring Boot's standard
 * single-datasource auto-configuration is used exactly as before and every existing test and the
 * live deployment behave identically.
 *
 * <p>When enabled, the gateway exposes a {@link RoutingDataSource} (wrapped in a {@link
 * LazyConnectionDataSourceProxy}) as the primary {@link DataSource}. Because Spring Boot's pooled
 * datasource auto-configuration backs off as soon as a {@code DataSource} bean is present, JPA and
 * Flyway transparently use this routing datasource. Read-only transactions are served from the
 * replica, everything else from the primary; the lazy proxy defers acquiring the physical
 * connection until the transaction's read-only flag has been set, so the routing decision is
 * correct. Flyway migrations and all writes run outside a read-only transaction and therefore
 * always hit the primary.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "gatewise.ha.replica", name = "enabled", havingValue = "true")
public class HaDataSourceConfiguration {

  /**
   * The writable primary datasource, built from the standard {@code spring.datasource.*} properties
   * so it is configured identically to the single datasource used when HA is off.
   *
   * @param properties Spring Boot's bound {@code spring.datasource.*} configuration
   * @return the primary connection pool
   */
  @Bean
  public DataSource primaryDataSource(DataSourceProperties properties) {
    return properties.initializeDataSourceBuilder().build();
  }

  /**
   * The read-replica datasource, built from {@code gatewise.ha.replica.*}.
   *
   * @param properties the HA configuration carrying the replica connection details
   * @return the replica connection pool
   * @throws IllegalStateException if the replica is enabled but no URL is configured
   */
  @Bean
  public DataSource replicaDataSource(HaProperties properties) {
    HaProperties.Replica replica = properties.replica();
    if (replica.url() == null || replica.url().isBlank()) {
      throw new IllegalStateException(
          "gatewise.ha.replica.enabled is true but gatewise.ha.replica.url is not set");
    }
    return DataSourceBuilder.create()
        .url(replica.url())
        .username(replica.username())
        .password(replica.password())
        .build();
  }

  /**
   * The routing datasource exposed as the primary {@link DataSource}: read-only transactions go to
   * the replica, everything else to the primary, with the primary as the default fallback.
   *
   * @param primary the writable primary datasource
   * @param replica the read-replica datasource
   * @return a lazy-connection proxy over the {@link RoutingDataSource}
   */
  @Bean
  @Primary
  public DataSource dataSource(
      @Qualifier("primaryDataSource") DataSource primary,
      @Qualifier("replicaDataSource") DataSource replica) {
    RoutingDataSource routing = new RoutingDataSource();
    routing.setTargetDataSources(
        Map.of(RoutingDataSource.PRIMARY, primary, RoutingDataSource.REPLICA, replica));
    routing.setDefaultTargetDataSource(primary);
    routing.afterPropertiesSet();
    return new LazyConnectionDataSourceProxy(routing);
  }
}
