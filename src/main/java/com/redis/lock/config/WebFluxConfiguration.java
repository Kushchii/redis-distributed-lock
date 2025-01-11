package com.redis.lock.config;

import com.redis.lock.handler.TransactionHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.config.EnableWebFlux;
import org.springframework.web.reactive.config.WebFluxConfigurer;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerResponse;

import static org.springframework.web.reactive.function.server.RouterFunctions.route;

@EnableWebFlux
@Configuration
@RequiredArgsConstructor
public class WebFluxConfiguration implements WebFluxConfigurer {

    public static final String TRANSACTIONS = "/api/transactions";
    public static final String CALLBACK = "/api/callback";

    @Bean
    public RouterFunction<ServerResponse> singleStepPaymentRouterFunction(
            TransactionHandler handler) {
        return route()
                .POST(TRANSACTIONS, handler::transactions)
                .POST(CALLBACK, handler::callback)
                .build();
    }
}
