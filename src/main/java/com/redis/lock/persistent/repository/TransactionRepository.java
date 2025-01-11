package com.redis.lock.persistent.repository;

import com.redis.lock.persistent.postgres.entity.TransactionsEntity;
import jakarta.validation.constraints.NotBlank;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface TransactionRepository extends R2dbcRepository<TransactionsEntity, Long> {

    Mono<TransactionsEntity> findByTransactionId(UUID id);
}

