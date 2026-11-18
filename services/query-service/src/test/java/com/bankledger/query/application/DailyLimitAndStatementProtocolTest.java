package com.bankledger.query.application;

import com.bankledger.commons.inbox.InboxHandler;
import com.bankledger.commons.security.CommonAuthenticatedUser;
import com.bankledger.commons.security.SecurityConstants;
import com.bankledger.query.adapters.in.kafka.QueryEventConsumer;
import com.bankledger.query.adapters.in.rest.QueryController;
import com.bankledger.query.adapters.in.rest.dto.BalanceResponse;
import com.bankledger.query.adapters.in.rest.dto.StatementItemResponse;
import com.bankledger.query.adapters.in.rest.dto.StatementResponse;
import com.bankledger.query.adapters.out.persistence.AccountBalanceEntity;
import com.bankledger.query.adapters.out.persistence.AccountBalanceRepository;
import com.bankledger.query.adapters.out.persistence.StatementEntryEntity;
import com.bankledger.query.adapters.out.persistence.StatementEntryRepository;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.kafka.support.Acknowledgment;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Task 5: Database-Driven Limit & Statement Protocol Projections Test")
class DailyLimitAndStatementProtocolTest {

    @Mock
    private AccountBalanceRepository accountBalanceRepository;

    @Mock
    private StatementEntryRepository statementEntryRepository;

    @Mock
    private InboxHandler inboxHandler;

    @Mock
    private Acknowledgment acknowledgment;

    private Clock fixedClock;
    private QueryController queryController;

    @BeforeEach
    void setUp() {
        // Fixed at 2026-11-18 14:30:00 UTC (11:30 in São Paulo UTC-3)
        Instant fixedInstant = Instant.parse("2026-11-18T14:30:00Z");
        fixedClock = Clock.fixed(fixedInstant, ZoneId.of("UTC"));
        queryController = new QueryController(accountBalanceRepository, statementEntryRepository, fixedClock);
    }

    private CommonAuthenticatedUser createUser(UUID accountId) {
        return new CommonAuthenticatedUser(
                UUID.randomUUID(),
                accountId,
                "12345678900",
                SecurityConstants.ROLE_USER,
                "0001",
                "12345678"
        );
    }

    @Test
    @DisplayName("getBalance: calculates dynamic usedTodayCents from today's debit transfers and returns DB daily limit")
    void getBalanceShouldCalculateDynamicUsedTodayCentsAndReturnDbLimit() {
        UUID accountId = UUID.randomUUID();
        CommonAuthenticatedUser user = createUser(accountId);
        Authentication auth = new TestingAuthenticationToken(user, null, user.getAuthorities());

        // Account with custom approved limit in database (e.g. R$ 250.000,00 = 25000000 cents)
        AccountBalanceEntity balanceEntity = AccountBalanceEntity.builder()
                .accountId(accountId)
                .balance(50000000L) // R$ 500.000,00 balance
                .currency("BRL")
                .lastSequence(15L)
                .dailyTransferLimitCents(25000000L)
                .updatedAt(Instant.parse("2026-11-18T14:00:00Z"))
                .build();

        when(accountBalanceRepository.findById(accountId)).thenReturn(Optional.of(balanceEntity));

        // Start of day in America/Sao_Paulo for 2026-11-18 is 2026-11-18T03:00:00Z
        Instant expectedStartOfDay = Instant.parse("2026-11-18T03:00:00Z");

        when(statementEntryRepository.sumAmountByAccountIdAndDirectionAndCategoryAndPostedAtGreaterThanEqual(
                eq(accountId),
                eq("DEBIT"),
                eq("TRANSFER"),
                eq(expectedStartOfDay)
        )).thenReturn(350000L); // R$ 3.500,00 used today in transfers

        ResponseEntity<?> responseEntity = queryController.getBalance(accountId, user, auth);

        assertThat(responseEntity.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(responseEntity.getBody()).isInstanceOf(BalanceResponse.class);

        BalanceResponse balanceResponse = (BalanceResponse) responseEntity.getBody();
        assertThat(balanceResponse.accountId()).isEqualTo(accountId);
        assertThat(balanceResponse.balance()).isEqualTo(50000000L);
        assertThat(balanceResponse.availableCents()).isEqualTo(50000000L);
        assertThat(balanceResponse.dailyLimitCents()).isEqualTo(25000000L);
        assertThat(balanceResponse.usedTodayCents()).isEqualTo(350000L);
    }

    @Test
    @DisplayName("getBalance: defaults dailyTransferLimitCents to 5000000L when 0 or not configured")
    void getBalanceShouldDefaultDailyLimitWhenZero() {
        UUID accountId = UUID.randomUUID();
        CommonAuthenticatedUser user = createUser(accountId);
        Authentication auth = new TestingAuthenticationToken(user, null, user.getAuthorities());

        AccountBalanceEntity balanceEntity = AccountBalanceEntity.builder()
                .accountId(accountId)
                .balance(100000L)
                .currency("BRL")
                .lastSequence(1L)
                .dailyTransferLimitCents(0L)
                .updatedAt(Instant.parse("2026-11-18T14:00:00Z"))
                .build();

        when(accountBalanceRepository.findById(accountId)).thenReturn(Optional.of(balanceEntity));
        when(statementEntryRepository.sumAmountByAccountIdAndDirectionAndCategoryAndPostedAtGreaterThanEqual(
                eq(accountId), eq("DEBIT"), eq("TRANSFER"), any(Instant.class)
        )).thenReturn(0L);

        ResponseEntity<?> responseEntity = queryController.getBalance(accountId, user, auth);

        assertThat(responseEntity.getStatusCode().is2xxSuccessful()).isTrue();
        BalanceResponse balanceResponse = (BalanceResponse) responseEntity.getBody();
        assertThat(balanceResponse.dailyLimitCents()).isEqualTo(5000000L);
        assertThat(balanceResponse.usedTodayCents()).isEqualTo(0L);
    }

    @Test
    @DisplayName("getStatement: maps protocolNumber and counterpartyDocument to StatementItemResponse")
    void getStatementShouldIncludeProtocolNumberAndCounterpartyDocument() {
        UUID accountId = UUID.randomUUID();
        CommonAuthenticatedUser user = createUser(accountId);
        Authentication auth = new TestingAuthenticationToken(user, null, user.getAuthorities());

        UUID statementId = UUID.randomUUID();
        UUID txId = UUID.randomUUID();
        UUID journalId = UUID.randomUUID();
        Instant postedAt = Instant.parse("2026-11-18T14:15:00Z");

        StatementEntryEntity entry = StatementEntryEntity.builder()
                .id(statementId)
                .accountId(accountId)
                .sequence(10L)
                .journalId(journalId)
                .transactionId(txId)
                .category("TRANSFER")
                .type("TRANSFER")
                .direction("DEBIT")
                .amount(150000L)
                .balanceAfter(48500000L)
                .counterpartyName("Ana Oliveira")
                .counterpartyDocumentMasked("***.456.789-**")
                .counterpartyDocument("123.456.789-00")
                .counterpartyAccount("0001-98765-4")
                .protocolNumber("PRT-20261118-9F8A1B")
                .description("Transferência enviada")
                .postedAt(postedAt)
                .build();

        when(statementEntryRepository.findByAccountIdOrderBySequenceDesc(accountId))
                .thenReturn(List.of(entry));

        ResponseEntity<?> responseEntity = queryController.getStatement(accountId, null, null, 50, user, auth);

        assertThat(responseEntity.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(responseEntity.getBody()).isInstanceOf(StatementResponse.class);

        StatementResponse statementResponse = (StatementResponse) responseEntity.getBody();
        assertThat(statementResponse.items()).hasSize(1);

        StatementItemResponse item = statementResponse.items().get(0);
        assertThat(item.id()).isEqualTo(statementId);
        assertThat(item.protocolNumber()).isEqualTo("PRT-20261118-9F8A1B");
        assertThat(item.counterpartyDocument()).isEqualTo("123.456.789-00");
        assertThat(item.counterpartyDocumentMasked()).isEqualTo("***.456.789-**");
        assertThat(item.counterpartyName()).isEqualTo("Ana Oliveira");
        assertThat(item.amountCents()).isEqualTo(150000L);
    }

    @Test
    @DisplayName("QueryEventConsumer: saves protocolNumber and counterpartyDocument from EntryPostedInboundDto")
    void queryEventConsumerShouldPersistProtocolNumberAndCounterpartyDocument() {
        ObjectMapper objectMapper = JsonMapper.builder().build();
        QueryEventConsumer consumer = new QueryEventConsumer(
                accountBalanceRepository,
                statementEntryRepository,
                inboxHandler,
                objectMapper
        );

        UUID eventId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        UUID entryId = UUID.randomUUID();
        UUID journalId = UUID.randomUUID();
        UUID txId = UUID.randomUUID();

        String envelopeJson = String.format("""
                {
                  "id": "%s",
                  "source": "ledger-service",
                  "type": "com.bankledger.ledger.entry-posted",
                  "data": {
                    "entryId": "%s",
                    "accountId": "%s",
                    "journalId": "%s",
                    "transactionId": "%s",
                    "direction": "DEBIT",
                    "amount": 50000,
                    "sequence": 1,
                    "balanceAfter": 950000,
                    "postedAt": "2026-11-18T10:00:00Z",
                    "category": "TRANSFER",
                    "type": "TRANSFER",
                    "description": "Transferência enviada",
                    "counterpartyName": "Carlos Silva",
                    "counterpartyDocumentMasked": "***.234.567-**",
                    "counterpartyDocument": "111.222.333-44",
                    "counterpartyAccount": "0001-11223-4",
                    "protocolNumber": "PRT-20261118-5A2C9D",
                    "idempotencyKey": "idemp-123"
                  }
                }
                """, eventId, entryId, accountId, journalId, txId);

        ConsumerRecord<String, String> record = new ConsumerRecord<>(
                "ledger.entries.v1", 0, 10L, accountId.toString(), envelopeJson
        );

        when(accountBalanceRepository.findById(accountId)).thenReturn(Optional.of(
                AccountBalanceEntity.builder()
                        .accountId(accountId)
                        .balance(1000000L)
                        .currency("BRL")
                        .lastSequence(0L)
                        .dailyTransferLimitCents(5000000L)
                        .updatedAt(Instant.parse("2026-11-18T09:00:00Z"))
                        .build()
        ));

        when(statementEntryRepository.existsByAccountIdAndSequence(accountId, 1L)).thenReturn(false);

        doAnswer(invocation -> {
            Runnable action = invocation.getArgument(2);
            action.run();
            return null;
        }).when(inboxHandler).execute(eq(eventId), eq(QueryEventConsumer.HANDLER_ENTRY_POSTED), any(Runnable.class));

        consumer.onEntryPosted(record, acknowledgment);

        ArgumentCaptor<StatementEntryEntity> captor = ArgumentCaptor.forClass(StatementEntryEntity.class);
        verify(statementEntryRepository).save(captor.capture());

        StatementEntryEntity saved = captor.getValue();
        assertThat(saved.getProtocolNumber()).isEqualTo("PRT-20261118-5A2C9D");
        assertThat(saved.getCounterpartyDocument()).isEqualTo("111.222.333-44");
        verify(acknowledgment).acknowledge();
    }
}
