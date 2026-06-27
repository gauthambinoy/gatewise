package com.auvex.gateway.ha;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Unit tests for the read/write routing decision. Pure JUnit — no Spring context, no database: it
 * drives {@link TransactionSynchronizationManager} directly and asserts which datasource key the
 * routing datasource picks. Lives in the same package so it can call the protected {@code
 * determineCurrentLookupKey()}.
 */
class RoutingDataSourceTest {

  private final RoutingDataSource routingDataSource = new RoutingDataSource();

  /** Reset the thread-bound transaction state so one test can never leak into the next. */
  @AfterEach
  void clearTransactionState() {
    TransactionSynchronizationManager.setCurrentTransactionReadOnly(false);
    if (TransactionSynchronizationManager.isSynchronizationActive()) {
      TransactionSynchronizationManager.clearSynchronization();
    }
  }

  @Test
  void routesReadOnlyTransactionToReplica() {
    TransactionSynchronizationManager.initSynchronization();
    TransactionSynchronizationManager.setCurrentTransactionReadOnly(true);

    assertThat(routingDataSource.determineCurrentLookupKey()).isEqualTo(RoutingDataSource.REPLICA);
  }

  @Test
  void routesWritableTransactionToPrimary() {
    TransactionSynchronizationManager.initSynchronization();
    TransactionSynchronizationManager.setCurrentTransactionReadOnly(false);

    assertThat(routingDataSource.determineCurrentLookupKey()).isEqualTo(RoutingDataSource.PRIMARY);
  }

  @Test
  void routesOutsideAnyTransactionToPrimary() {
    assertThat(routingDataSource.determineCurrentLookupKey()).isEqualTo(RoutingDataSource.PRIMARY);
  }
}
