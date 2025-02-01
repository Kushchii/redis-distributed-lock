package com.redis.lock;

import com.redis.lock.api.request.CallbackRequest;
import com.redis.lock.api.request.TransactionsRequest;
import com.redis.lock.api.response.TransactionsResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static com.redis.lock.BaseTest.random;
import static org.junit.jupiter.api.Assertions.assertEquals;

class RedisFunctionalTest extends BaseFunctionalTest {

    private static final UUID TRANSACTION_ID = UUID.fromString("221dc9a8-81e7-4bee-afc8-3cd83aae580d");
    private static final String CALLBACK_STATUS_SUCCESS = "success";

    private TransactionsRequest createTransactionRequest() {
        var request = random(TransactionsRequest.class);
        request.setId(TRANSACTION_ID);
        return request;
    }

    private CallbackRequest createCallbackRequest() {
        var request = new CallbackRequest();
        request.setId(TRANSACTION_ID);
        request.setStatus(CALLBACK_STATUS_SUCCESS);
        return request;
    }

    @Test
    @DisplayName("Successful transaction")
    void shouldProcessTransactionSuccessfully() {
        var transactionsRequest = createTransactionRequest();
        var expectedResponse = new TransactionsResponse("Transaction processed successfully");

        var actualResponse = doPost("/api/transactions", transactionsRequest, TransactionsResponse.class);

        assertEquals(expectedResponse, actualResponse);
    }

    @Test
    @DisplayName("Successful callback")
    void shouldProcessCallbackSuccessfully() {
        var transactionsRequest = createTransactionRequest();
        var callbackRequest = createCallbackRequest();
        var expectedCallbackResponse = new TransactionsResponse("Callback processed successfully");

        doPost("/api/transactions", transactionsRequest, TransactionsResponse.class);
        var actualCallbackResponse = doPost("/api/callback", callbackRequest, TransactionsResponse.class);

        assertEquals(expectedCallbackResponse, actualCallbackResponse);
    }

    @Test
    @DisplayName("Asynchronous transaction and delayed callback")
    void shouldHandleAsyncTransactionAndDelayedCallback() {
        var transactionFuture = CompletableFuture.runAsync(() -> {
            var transactionsRequest = createTransactionRequest();
            doPost("/api/transactions", transactionsRequest, TransactionsResponse.class);
        });

        var callbackFuture = CompletableFuture.runAsync(() -> {
            try {
                Thread.sleep(1000);
                var callbackRequest = createCallbackRequest();
                var expectedCallbackResponse = new TransactionsResponse("Callback processed successfully");

                var actualCallbackResponse = doPost("/api/callback", callbackRequest, TransactionsResponse.class);

                assertEquals(expectedCallbackResponse, actualCallbackResponse);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Callback was interrupted", e);
            }
        });

        CompletableFuture.allOf(transactionFuture, callbackFuture).join();
    }
}
