package com.redis.lock.mapper;

import com.redis.lock.api.request.TransactionsRequest;
import com.redis.lock.persistent.postgres.entity.TransactionsEntity;
import org.springframework.stereotype.Component;

@Component
public class TransactionMapper {

    public TransactionsEntity toEntity(TransactionsRequest request) {
        var transaction = new TransactionsEntity();
        transaction.setTransactionId(request.getId());
        transaction.setAmount(request.getAmount());
        transaction.setUserId(request.getUserId());
        transaction.setCurrency(request.getCurrency());
        transaction.setStatus(request.getStatus());
        transaction.setDescription(request.getDescription());

        return transaction;
    }
}
