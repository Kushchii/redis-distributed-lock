package com.redis.lock.handler;

import com.redis.lock.api.request.TransactionsRequest;
import com.redis.lock.service.TransactionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

@Component
@Slf4j
@Validated
@RequiredArgsConstructor
public class TransactionHandler extends BaseHandler {

    private final TransactionService transactionService;

    public Mono<ServerResponse> transactions(ServerRequest request) {
        return request.bodyToMono(TransactionsRequest.class)
                .flatMap(transactionService::transactions)
                .flatMap(it -> toServerResponse(HttpStatus.OK, it));
    }
}
