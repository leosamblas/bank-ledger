package com.bankledger.ledger.adapters.in.rest;

import com.bankledger.ledger.application.port.in.GetJournalUseCase;
import com.bankledger.ledger.application.port.in.GetLedgerAccountUseCase;
import com.bankledger.ledger.application.port.in.GetTrialBalanceUseCase;
import com.bankledger.ledger.application.port.in.TrialBalanceResult;
import com.bankledger.ledger.domain.model.AccountKind;
import com.bankledger.ledger.domain.model.AccountStatus;
import com.bankledger.ledger.domain.model.Entry;
import com.bankledger.ledger.domain.model.EntryDirection;
import com.bankledger.ledger.domain.model.Journal;
import com.bankledger.ledger.domain.model.LedgerAccount;
import com.bankledger.ledger.domain.model.NormalBalance;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
@DisplayName("InternalLedgerController Unit Tests")
class InternalLedgerControllerTest {

    private MockMvc mockMvc;

    @Mock
    private GetLedgerAccountUseCase getLedgerAccountUseCase;

    @Mock
    private GetJournalUseCase getJournalUseCase;

    @Mock
    private GetTrialBalanceUseCase getTrialBalanceUseCase;

    private InternalLedgerController internalLedgerController;

    @BeforeEach
    void setUp() {
        internalLedgerController = new InternalLedgerController(
                getLedgerAccountUseCase,
                getJournalUseCase,
                getTrialBalanceUseCase
        );

        mockMvc = MockMvcBuilders
                .standaloneSetup(internalLedgerController)
                .setMessageConverters(new JacksonJsonHttpMessageConverter(JsonMapper.builder().build()))
                .build();
    }

    @Test
    @DisplayName("GET /internal/v1/ledger/accounts/{id}/balance - should return 200 with balance when account exists")
    void shouldReturnBalanceWhenAccountExists() throws Exception {
        UUID accountId = UUID.randomUUID();
        LedgerAccount account = new LedgerAccount(
                accountId,
                "ACC-100",
                AccountKind.CUSTOMER,
                NormalBalance.CREDIT,
                "BRL",
                50000L,
                2L,
                AccountStatus.ACTIVE,
                Instant.now(),
                Instant.now(),
                1L
        );

        when(getLedgerAccountUseCase.getAccount(accountId)).thenReturn(Optional.of(account));

        mockMvc.perform(get("/internal/v1/ledger/accounts/{id}/balance", accountId)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountId").value(accountId.toString()))
                .andExpect(jsonPath("$.balance").value(50000))
                .andExpect(jsonPath("$.lastSequence").value(2))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    @DisplayName("GET /internal/v1/ledger/accounts/{id}/balance - should return 404 when account does not exist")
    void shouldReturn404WhenAccountNotFound() throws Exception {
        UUID accountId = UUID.randomUUID();
        when(getLedgerAccountUseCase.getAccount(accountId)).thenReturn(Optional.empty());

        mockMvc.perform(get("/internal/v1/ledger/accounts/{id}/balance", accountId))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("GET /internal/v1/ledger/journals/by-transaction/{transactionId} - should return 200 with journal and entries")
    void shouldReturnJournalByTransactionId() throws Exception {
        UUID transactionId = UUID.randomUUID();
        UUID journalId = UUID.randomUUID();
        UUID accountA = UUID.randomUUID();
        UUID accountB = UUID.randomUUID();

        Entry entryA = new Entry(
                UUID.randomUUID(), journalId, accountA, EntryDirection.DEBIT, 50000L, 50000L, 1L, Instant.now()
        );
        Entry entryB = new Entry(
                UUID.randomUUID(), journalId, accountB, EntryDirection.CREDIT, 50000L, 50000L, 1L, Instant.now()
        );

        Journal journal = Journal.createPosted(
                journalId,
                transactionId,
                "DEPOSIT",
                "Depósito de teste",
                50000L,
                "BRL",
                UUID.randomUUID(),
                Instant.now(),
                List.of(entryA, entryB)
        );

        when(getJournalUseCase.getJournalByTransactionId(transactionId)).thenReturn(Optional.of(journal));

        mockMvc.perform(get("/internal/v1/ledger/journals/by-transaction/{transactionId}", transactionId)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.journalId").value(journalId.toString()))
                .andExpect(jsonPath("$.transactionId").value(transactionId.toString()))
                .andExpect(jsonPath("$.type").value("DEPOSIT"))
                .andExpect(jsonPath("$.status").value("POSTED"))
                .andExpect(jsonPath("$.totalAmount").value(50000))
                .andExpect(jsonPath("$.entries.length()").value(2))
                .andExpect(jsonPath("$.entries[0].accountId").value(accountA.toString()))
                .andExpect(jsonPath("$.entries[0].direction").value("DEBIT"))
                .andExpect(jsonPath("$.entries[0].amount").value(50000));
    }

    @Test
    @DisplayName("GET /internal/v1/ledger/journals/by-transaction/{transactionId} - should return 404 when journal not found")
    void shouldReturn404WhenJournalNotFound() throws Exception {
        UUID transactionId = UUID.randomUUID();
        when(getJournalUseCase.getJournalByTransactionId(transactionId)).thenReturn(Optional.empty());

        mockMvc.perform(get("/internal/v1/ledger/journals/by-transaction/{transactionId}", transactionId))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("GET /internal/v1/ledger/trial-balance - should return 200 with debits, credits, and difference")
    void shouldReturnTrialBalance() throws Exception {
        TrialBalanceResult result = new TrialBalanceResult(100000L, 100000L, 0L);
        when(getTrialBalanceUseCase.getTrialBalance()).thenReturn(result);

        mockMvc.perform(get("/internal/v1/ledger/trial-balance")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.debits").value(100000))
                .andExpect(jsonPath("$.credits").value(100000))
                .andExpect(jsonPath("$.difference").value(0));
    }
}
