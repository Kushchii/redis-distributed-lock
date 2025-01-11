package com.redis.lock.persistent.repository;

import com.redis.lock.persistent.postgres.entity.TransactionsEntity;
import org.springframework.data.r2dbc.repository.R2dbcRepository;

public interface TransactionRepository extends R2dbcRepository<TransactionsEntity, Long> {

}

