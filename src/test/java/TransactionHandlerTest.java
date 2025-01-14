package com.redis.lock.handler;

import com.redis.lock.api.request.CallbackRequest;
import com.redis.lock.api.request.TransactionsRequest;
import com.redis.lock.api.response.TransactionsResponse;
import com.redis.lock.config.WebFluxConfiguration;
import com.redis.lock.mapper.TransactionMapper;
import com.redis.lock.persistent.postgres.entity.TransactionsEntity;
import com.redis.lock.persistent.repository.TransactionRepository;
import com.redis.lock.service.TransactionService;
import com.redis.lock.service.TransactionServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.*;
import org.redisson.api.RLockReactive;
import org.redisson.api.RedissonReactiveClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@WebFluxTest(TransactionHandler.class)
public class TransactionHandlerTest {

    @Autowired
    private WebTestClient webTestClient;

    @MockBean
    private TransactionService transactionService;

    @MockBean
    private TransactionMapper transactionMapper;

    @MockBean
    private TransactionRepository transactionRepository;

    @MockBean
    private RedissonReactiveClient redissonReactiveClient;

    @Mock
    private RLockReactive rLockReactive;

    @InjectMocks
    private TransactionServiceImpl transactionServiceImpl;

    @Captor
    private ArgumentCaptor<TransactionsEntity> transactionsEntityCaptor;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);

        // Mock the Redisson client and lock
        given(redissonReactiveClient.getLock(anyString())).willReturn(rLockReactive);
        given(rLockReactive.tryLock(anyLong(), anyLong(), any(TimeUnit.class))).willReturn(Mono.just(true));

        // Initialize the TransactionServiceImpl with the mocked dependencies
        transactionServiceImpl = new TransactionServiceImpl(transactionMapper, redissonReactiveClient, transactionRepository);

        // Set the real implementation of TransactionService in TransactionHandler
        RouterFunction<ServerResponse> routerFunction = new WebFluxConfiguration()
                .singleStepPaymentRouterFunction(new TransactionHandler(transactionServiceImpl));
        webTestClient = WebTestClient.bindToRouterFunction(routerFunction)
                .configureClient()
                .responseTimeout(Duration.ofSeconds(10)) // Increase the timeout to 10 seconds
                .build();
    }

    @Test
    void testTransactionsEndpoint() {
        TransactionsRequest transactionsRequest = new TransactionsRequest();
        transactionsRequest.setId(UUID.fromString("221dc9a8-81e7-4bee-afc8-3cd83aae580d"));
        transactionsRequest.setStatus("pending");
        transactionsRequest.setUserId("1e0d2473-1396-4d4b-a8b0-9f2c2efef805");
        transactionsRequest.setAmount(new BigDecimal("1.00"));
        transactionsRequest.setCurrency("UAH");
        transactionsRequest.setDescription("T-shirt");

        TransactionsResponse transactionsResponse = new TransactionsResponse("Transaction processed successfully");

        TransactionsEntity transactionsEntity = new TransactionsEntity();
        transactionsEntity.setTransactionId(UUID.fromString(UUID.fromString("221dc9a8-81e7-4bee-afc8-3cd83aae580d").toString()));

        // Mock the service methods
        given(transactionMapper.toEntity(any(TransactionsRequest.class))).willReturn(transactionsEntity);
        given(transactionRepository.save(any(TransactionsEntity.class))).willReturn(Mono.just(transactionsEntity));

        // Perform the request and verify the response
        webTestClient.post()
                .uri("/api/transactions")
                .bodyValue(transactionsRequest)
                .exchange()
                .expectStatus().isOk()
                .expectBody(TransactionsResponse.class)
                .isEqualTo(transactionsResponse);

        // Verify interactions
        verify(transactionMapper).toEntity(any(TransactionsRequest.class));
        verify(transactionRepository).save(transactionsEntityCaptor.capture());

        TransactionsEntity savedEntity = transactionsEntityCaptor.getValue();
        assertEquals(UUID.fromString("221dc9a8-81e7-4bee-afc8-3cd83aae580d"), savedEntity.getTransactionId());
    }

    @Test
    void testCallbackEndpoint() {
        CallbackRequest callbackRequest = new CallbackRequest();
        callbackRequest.setId(UUID.fromString("221dc9a8-81e7-4bee-afc8-3cd83aae580d"));
        callbackRequest.setStatus("success");

        TransactionsResponse transactionsResponse = new TransactionsResponse("Callback processed successfully");

        TransactionsEntity transactionsEntity = new TransactionsEntity();
        transactionsEntity.setTransactionId(UUID.fromString("221dc9a8-81e7-4bee-afc8-3cd83aae580d"));

        // Mock the service methods
        given(transactionRepository.findByTransactionId(UUID.fromString("221dc9a8-81e7-4bee-afc8-3cd83aae580d"))).willReturn(Mono.just(transactionsEntity));
        given(transactionRepository.save(any(TransactionsEntity.class))).willReturn(Mono.just(transactionsEntity));

        // Perform the request and verify the response
        webTestClient.post()
                .uri("/api/callback")
                .bodyValue(callbackRequest)
                .exchange()
                .expectStatus().isOk()
                .expectBody(TransactionsResponse.class)
                .isEqualTo(transactionsResponse);

        // Verify interactions
        verify(transactionRepository).findByTransactionId(UUID.fromString("221dc9a8-81e7-4bee-afc8-3cd83aae580d"));
        verify(transactionRepository).save(transactionsEntityCaptor.capture());

        TransactionsEntity updatedEntity = transactionsEntityCaptor.getValue();
        assertEquals("success", updatedEntity.getStatus());
    }

    @Test
    void testTransactionAndCallbackInParallel() {
        TransactionsRequest transactionsRequest = new TransactionsRequest();
        transactionsRequest.setId(UUID.fromString("223dc9a8-81e7-4bee-afc8-4cd83aae380d"));
        transactionsRequest.setStatus("pending");
        transactionsRequest.setUserId("1e0d2473-1396-4d4b-a8b0-9f2c2efef805");
        transactionsRequest.setAmount(new BigDecimal("1.00"));
        transactionsRequest.setCurrency("UAH");
        transactionsRequest.setDescription("T-shirt");

        TransactionsEntity transactionsEntity = new TransactionsEntity();
        transactionsEntity.setTransactionId(UUID.fromString("223dc9a8-81e7-4bee-afc8-4cd83aae380d"));

        given(transactionMapper.toEntity(any(TransactionsRequest.class))).willReturn(transactionsEntity);
        given(transactionRepository.save(any(TransactionsEntity.class))).willAnswer(invocation -> {
            TransactionsEntity entity = invocation.getArgument(0);
            entity.setTransactionId(UUID.fromString("223dc9a8-81e7-4bee-afc8-4cd83aae380d"));
            return Mono.just(entity);
        });

        given(rLockReactive.tryLock(anyLong(), anyLong(), any(TimeUnit.class))).willReturn(Mono.just(true));
        given(rLockReactive.unlock()).willReturn(Mono.empty());

        CallbackRequest callbackRequest = new CallbackRequest();
        callbackRequest.setId(UUID.fromString("223dc9a8-81e7-4bee-afc8-4cd83aae380d"));
        callbackRequest.setStatus("success");

        given(transactionRepository.findByTransactionId(UUID.fromString("223dc9a8-81e7-4bee-afc8-4cd83aae380d"))).willReturn(Mono.just(transactionsEntity));
        given(transactionRepository.save(any(TransactionsEntity.class))).willAnswer(invocation -> {
            TransactionsEntity entity = invocation.getArgument(0);
            entity.setStatus("success");
            return Mono.just(entity);
        });

        var scheduler = Executors.newScheduledThreadPool(2);

        var transactionFuture = CompletableFuture.supplyAsync(() -> webTestClient.post()
                .uri("/api/transactions")
                .bodyValue(transactionsRequest)
                .exchange()
                .expectStatus().isOk()
                .returnResult(TransactionsResponse.class)
                .getResponseBody()
                .single()
                .block());

        var callbackFuture = CompletableFuture.supplyAsync(() -> {
            try {
                TimeUnit.SECONDS.sleep(1);
            } catch (InterruptedException e) {
                throw new IllegalStateException(e);
            }
            return webTestClient.post()
                    .uri("/api/callback")
                    .bodyValue(callbackRequest)
                    .exchange()
                    .expectStatus().isOk()
                    .returnResult(TransactionsResponse.class)
                    .getResponseBody()
                    .single()
                    .block();
        }, scheduler);

        CompletableFuture.allOf(transactionFuture, callbackFuture).thenAccept(v -> {
            try {
                var transactionResponse = transactionFuture.get();
                var callbackResponse = callbackFuture.get();

                assertEquals("Transaction processed successfully", transactionResponse.getMessage());
                assertEquals("Callback processed successfully", callbackResponse.getMessage());
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }).join();

        scheduler.shutdown();

        verify(transactionMapper).toEntity(any(TransactionsRequest.class));
        verify(transactionRepository, times(2)).save(any(TransactionsEntity.class));
        verify(rLockReactive, times(2)).tryLock(anyLong(), anyLong(), any(TimeUnit.class));
        verify(rLockReactive, times(2)).unlock();
    }
}