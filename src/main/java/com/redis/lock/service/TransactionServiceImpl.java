package com.redis.lock.service;

import com.redis.lock.api.request.CallbackRequest;
import com.redis.lock.api.request.TransactionsRequest;
import com.redis.lock.api.response.TransactionsResponse;
import com.redis.lock.mapper.TransactionMapper;
import com.redis.lock.persistent.postgres.entity.TransactionsEntity;
import com.redis.lock.persistent.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLockReactive;
import org.redisson.api.RedissonReactiveClient;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

@RequiredArgsConstructor
@Slf4j
@Service
public class TransactionServiceImpl implements TransactionService {

    private static final String TRANSACTION_LOCK_KEY = "transaction_lock";
    private static final long LOCK_WAIT_TIME_SECONDS = 30;
    private static final long LOCK_LEASE_TIME_SECONDS = 10;

    private static final long PROCESSING_DELAY_SECONDS = 5;

    private static final String CALLBACK_OPERATION_TYPE = "callback";
    private static final String TRANSACTION_OPERATION_TYPE = "transaction";

    private final TransactionMapper transactionMapper;
    private final RedissonReactiveClient redissonReactiveClient;
    private final TransactionRepository transactionRepository;

    @Override
    public Mono<TransactionsResponse> transactions(TransactionsRequest request) {
        var lock = redissonReactiveClient.getLock(TRANSACTION_LOCK_KEY + ":" + request.getId());
        return acquireLock(lock, TRANSACTION_OPERATION_TYPE)
                .flatMap(locked -> locked ? processTransactionAndSave(request) : Mono.error(new IllegalStateException("Failed to acquire lock for transaction")))
                .flatMap(this::processTransaction)
                .doOnSuccess(it -> log.info("Transaction processed successfully: {}", request.getId()))
                .onErrorResume(e -> handleTransactionError(request, e).then())
                .then(releaseLock(lock, TRANSACTION_OPERATION_TYPE))
                .thenReturn(new TransactionsResponse("Transaction processed successfully"));
    }

    @Override
    public Mono<TransactionsResponse> callback(CallbackRequest callbackRequest) {
        var lock = redissonReactiveClient.getLock(TRANSACTION_LOCK_KEY);
        return acquireLock(lock, CALLBACK_OPERATION_TYPE)
                .flatMap(locked -> locked ? processCallbackLogic(callbackRequest) : Mono.error(new IllegalStateException("Failed to acquire lock for callback")))
                .then(releaseLock(lock, CALLBACK_OPERATION_TYPE))
                .thenReturn(new TransactionsResponse("Callback processed successfully"))
                .onErrorResume(e -> {
                    log.error("Callback processing failed", e);
                    return Mono.error(new RuntimeException("Callback processing failed due to an unexpected error", e));
                });
    }

    private Mono<Boolean> acquireLock(RLockReactive lock, String operationType) {
        long threadId = System.nanoTime();
        return lock.tryLock(LOCK_WAIT_TIME_SECONDS, LOCK_LEASE_TIME_SECONDS, TimeUnit.SECONDS, threadId)
                .doOnSuccess(locked -> {
                    if (locked) {
                        log.info("Lock acquired successfully for operation: {}", operationType);
                    } else {
                        log.warn("Failed to acquire lock for operation: {}", operationType);
                    }
                });
    }

    private Mono<TransactionsEntity> processTransactionAndSave(TransactionsRequest request) {
        TransactionsEntity entity = transactionMapper.toEntity(request);
        return transactionRepository.save(entity)
                .doOnSuccess(savedEntity -> log.info("Transaction entity saved: {}", savedEntity.getTransactionId()));
    }

    private Mono<Void> processTransaction(TransactionsEntity entity) {
        return Mono.delay(Duration.ofSeconds(PROCESSING_DELAY_SECONDS))
                .then(Mono.fromRunnable(() -> log.info("Transaction processed with id: {}", entity.getTransactionId())));
    }

    private Mono<Void> processCallbackLogic(CallbackRequest callbackRequest) {
        return transactionRepository.findByTransactionId(callbackRequest.getId())
                .flatMap(transaction -> {
                    if (transaction == null) {
                        return Mono.error(new IllegalStateException("Transaction not found for ID: " + callbackRequest.getId()));
                    }
                    log.info("Processing callback for transaction {} with current status {}", transaction.getTransactionId(), transaction.getStatus());

                    transaction.setStatus(callbackRequest.getStatus());

                    return transactionRepository.save(transaction)
                            .doOnSuccess(updatedTransaction ->
                                    log.info("Transaction status updated to: {} for transaction {}", updatedTransaction.getStatus(), updatedTransaction.getTransactionId()))
                            .then();
                })
                .doOnError(error -> log.error("Error processing callback for transaction {}: {}", callbackRequest.getId(), error.getMessage()));
    }

    public Mono<TransactionsResponse> handleTransactionError(TransactionsRequest request, Throwable error) {
        log.error("Transaction processing failed: {}", request.getId(), error);
        return Mono.just(new TransactionsResponse("Transaction failed due to an unexpected error"));
    }

    private Mono<TransactionsResponse> releaseLock(RLockReactive lock, String operationType) {
        return Mono.fromRunnable(lock::unlock)
                .doOnTerminate(() -> log.info("Lock released successfully for operation: {}", operationType))
                .thenReturn(new TransactionsResponse("Lock released successfully for operation: " + operationType));
    }
}
