package com.redis.lock.service;

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
    private static final long LOCK_WAIT_TIME_SECONDS = 5;
    private static final long LOCK_LEASE_TIME_SECONDS = 10;
    private static final long PROCESSING_DELAY_SECONDS = 5;
    private final TransactionMapper transactionMapper;
    private final RedissonReactiveClient redissonReactiveClient;
    private final TransactionRepository transactionRepository;

    @Override
    public Mono<TransactionsResponse> transactions(TransactionsRequest request) {
        var lock = redissonReactiveClient.getLock(TRANSACTION_LOCK_KEY);

        return Mono.defer(() ->
                lock.tryLock(LOCK_WAIT_TIME_SECONDS, LOCK_LEASE_TIME_SECONDS, TimeUnit.SECONDS)
                        .flatMap(locked -> {
                            if (locked) {
                                log.info("Lock acquired successfully for transaction {}", request.getId());

                                return processTransactionAndSave(request)
                                        .doOnSuccess(savedEntity -> log.info("Transaction entity saved: {}", savedEntity))
                                        .flatMap(this::processTransaction)
                                        .doOnError(error -> log.error("Error processing transaction: {}", request.getId(), error))
                                        .then(releaseLock(lock, request))
                                        .thenReturn(new TransactionsResponse("Transaction processed successfully"));
                            } else {
                                log.warn("Failed to acquire lock for transaction {}", request.getId());
                                return Mono.error(new IllegalStateException("Failed to acquire lock for transaction"));
                            }
                        })
                        .onErrorResume(e -> handleTransactionError(request, e))
        );
    }

    private Mono<TransactionsEntity> processTransactionAndSave(TransactionsRequest request) {
        TransactionsEntity entity = transactionMapper.toEntity(request);
        return transactionRepository.save(entity);
    }

    private Mono<Void> releaseLock(RLockReactive lock, TransactionsRequest request) {
        return Mono.fromRunnable(lock::unlock)
                .doOnTerminate(() -> log.info("Lock released for transaction {}", request.getId())).then();
    }

    private Mono<TransactionsResponse> handleTransactionError(TransactionsRequest request, Throwable error) {
        log.error("Transaction processing failed: {}", request.getId(), error);
        return Mono.just(new TransactionsResponse("Transaction failed due to an unexpected error"));
    }


    private Mono<TransactionsResponse> processTransaction(TransactionsEntity entity) {
        return Mono.delay(Duration.ofSeconds(PROCESSING_DELAY_SECONDS))
                .then(Mono.fromCallable(() -> {
                    log.info("Transaction processed with id: {}", entity.getId());
                    return new TransactionsResponse("Transaction processed successfully");
                }));
    }
}
