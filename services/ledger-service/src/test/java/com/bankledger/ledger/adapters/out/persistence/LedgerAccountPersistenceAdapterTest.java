package com.bankledger.ledger.adapters.out.persistence;

import com.bankledger.ledger.adapters.out.persistence.entity.LedgerAccountEntity;
import com.bankledger.ledger.adapters.out.persistence.repository.LedgerAccountSpringDataRepository;
import com.bankledger.ledger.adapters.out.persistence.repository.TrialBalanceProjection;
import com.bankledger.ledger.domain.model.AccountKind;
import com.bankledger.ledger.domain.model.AccountStatus;
import com.bankledger.ledger.domain.model.LedgerAccount;
import com.bankledger.ledger.domain.model.NormalBalance;
import com.bankledger.ledger.domain.model.TrialBalanceTotals;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("LedgerAccountPersistenceAdapter Unit Tests")
class LedgerAccountPersistenceAdapterTest {

    @Mock
    private LedgerAccountSpringDataRepository repository;

    @InjectMocks
    private LedgerAccountPersistenceAdapter adapter;

    @Nested
    @DisplayName("Trial Balance Queries")
    class TrialBalanceTests {

        @Test
        @DisplayName("Should return TrialBalanceTotals from repository aggregate projection")
        void shouldReturnTrialBalanceTotalsFromProjection() {
            TrialBalanceProjection projection = mock(TrialBalanceProjection.class);
            when(projection.getDebits()).thenReturn(150000L);
            when(projection.getCredits()).thenReturn(150000L);
            when(repository.getTrialBalanceAggregate()).thenReturn(projection);

            TrialBalanceTotals totals = adapter.getTrialBalanceTotals();

            assertThat(totals).isNotNull();
            assertThat(totals.debits()).isEqualTo(150000L);
            assertThat(totals.credits()).isEqualTo(150000L);
            assertThat(totals.difference()).isZero();
            verify(repository).getTrialBalanceAggregate();
        }

        @Test
        @DisplayName("Should return zeros when repository aggregate projection is null")
        void shouldReturnZerosWhenProjectionIsNull() {
            when(repository.getTrialBalanceAggregate()).thenReturn(null);

            TrialBalanceTotals totals = adapter.getTrialBalanceTotals();

            assertThat(totals).isNotNull();
            assertThat(totals.debits()).isZero();
            assertThat(totals.credits()).isZero();
            assertThat(totals.difference()).isZero();
            verify(repository).getTrialBalanceAggregate();
        }

        @Test
        @DisplayName("Should delegate sumDebits to getTrialBalanceTotals")
        void shouldDelegateSumDebits() {
            TrialBalanceProjection projection = mock(TrialBalanceProjection.class);
            when(projection.getDebits()).thenReturn(80000L);
            when(projection.getCredits()).thenReturn(50000L);
            when(repository.getTrialBalanceAggregate()).thenReturn(projection);

            long debits = adapter.sumDebits();

            assertThat(debits).isEqualTo(80000L);
            verify(repository).getTrialBalanceAggregate();
        }

        @Test
        @DisplayName("Should delegate sumCredits to getTrialBalanceTotals")
        void shouldDelegateSumCredits() {
            TrialBalanceProjection projection = mock(TrialBalanceProjection.class);
            when(projection.getDebits()).thenReturn(80000L);
            when(projection.getCredits()).thenReturn(50000L);
            when(repository.getTrialBalanceAggregate()).thenReturn(projection);

            long credits = adapter.sumCredits();

            assertThat(credits).isEqualTo(50000L);
            verify(repository).getTrialBalanceAggregate();
        }
    }

    @Nested
    @DisplayName("Account Queries and Persistence")
    class AccountQueryTests {

        @Test
        @DisplayName("findById should return empty when id is null")
        void findByIdShouldReturnEmptyWhenIdIsNull() {
            Optional<LedgerAccount> result = adapter.findById(null);

            assertThat(result).isEmpty();
            verifyNoInteractions(repository);
        }

        @Test
        @DisplayName("findById should return domain account when found")
        void findByIdShouldReturnDomainAccount() {
            UUID id = UUID.randomUUID();
            LedgerAccountEntity entity = new LedgerAccountEntity(
                    id, "ACC-01", AccountKind.CUSTOMER, NormalBalance.CREDIT, "BRL", 5000L, 1L,
                    AccountStatus.ACTIVE, Instant.now(), Instant.now(), 0L
            );
            when(repository.findById(id)).thenReturn(Optional.of(entity));

            Optional<LedgerAccount> result = adapter.findById(id);

            assertThat(result).isPresent();
            assertThat(result.get().getCode()).isEqualTo("ACC-01");
        }

        @Test
        @DisplayName("findByIdForUpdate should return empty when id is null")
        void findByIdForUpdateShouldReturnEmptyWhenIdIsNull() {
            Optional<LedgerAccount> result = adapter.findByIdForUpdate(null);

            assertThat(result).isEmpty();
            verifyNoInteractions(repository);
        }

        @Test
        @DisplayName("findByCode should return empty when code is blank")
        void findByCodeShouldReturnEmptyWhenBlank() {
            assertThat(adapter.findByCode(null)).isEmpty();
            assertThat(adapter.findByCode("  ")).isEmpty();
            verifyNoInteractions(repository);
        }

        @Test
        @DisplayName("save should throw NPE when account is null")
        void saveShouldThrowWhenNull() {
            assertThatThrownBy(() -> adapter.save(null))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("save should persist entity and return domain object")
        void saveShouldPersistEntity() {
            UUID id = UUID.randomUUID();
            LedgerAccount domain = new LedgerAccount(
                    id, "ACC-01", AccountKind.CUSTOMER, NormalBalance.CREDIT, "BRL", 5000L, 1L,
                    AccountStatus.ACTIVE, Instant.now(), Instant.now(), 0L
            );
            LedgerAccountEntity entity = LedgerAccountEntity.fromDomain(domain);
            when(repository.findById(id)).thenReturn(Optional.empty());
            when(repository.saveAndFlush(any(LedgerAccountEntity.class))).thenReturn(entity);

            LedgerAccount saved = adapter.save(domain);

            assertThat(saved).isNotNull();
            assertThat(saved.getId()).isEqualTo(id);
            verify(repository).saveAndFlush(any(LedgerAccountEntity.class));
        }
    }
}
