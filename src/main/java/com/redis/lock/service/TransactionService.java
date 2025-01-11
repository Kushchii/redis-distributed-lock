package com.redis.lock.service;

import com.redis.lock.api.request.TransactionsRequest;
import com.redis.lock.api.response.TransactionsResponse;
import reactor.core.publisher.Mono;

public interface TransactionService {

    Mono<TransactionsResponse> transactions(TransactionsRequest request);
}
