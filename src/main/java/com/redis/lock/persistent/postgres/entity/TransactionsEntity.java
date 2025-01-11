package com.redis.lock.persistent.postgres.entity;

import lombok.Data;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@Table("transactions")
public class TransactionsEntity {

    @Column("id")
    private UUID id;

    @Column("status")
    private String status;

    @Column("user_id")
    private String userId;

    @Column("amount")
    private BigDecimal amount;

    @Column("currency")
    private String currency;

    @Column("description")
    private String description;
}