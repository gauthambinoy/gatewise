package com.gatewise.gateway.ha;

import org.springframework.jdbc.datasource.lookup.AbstractRoutingDataSource;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Routes each connection to the primary or the read replica based on the current transaction.
 *
 * <p>A connection requested inside a {@code @Transactional(readOnly = true)} transaction is served
 * from the replica; everything else — writes, and reads outside a read-only transaction — goes to
 * the primary. Spring asks for the lookup key lazily, per connection, via {@link
 * #determineCurrentLookupKey()}, so the decision always reflects the transaction in scope at the
 * moment the connection is actually opened (which is why the routing datasource is wrapped in a
 * {@code LazyConnectionDataSourceProxy} — see {@code HaDataSourceConfiguration}).
 */
public class RoutingDataSource extends AbstractRoutingDataSource {

  /** Lookup key for the primary (writable) datasource. */
  public static final String PRIMARY = "primary";

  /** Lookup key for the read-replica datasource. */
  public static final String REPLICA = "replica";

  /**
   * Returns {@link #REPLICA} when called inside a current read-only transaction, otherwise {@link
   * #PRIMARY}.
   *
   * @return the target datasource key for the connection about to be opened
   */
  @Override
  protected Object determineCurrentLookupKey() {
    return TransactionSynchronizationManager.isCurrentTransactionReadOnly() ? REPLICA : PRIMARY;
  }
}
