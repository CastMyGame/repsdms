package com.reps.demogcloud.models.student;

import lombok.Getter;

@Getter
public class TransactionRequest {
    private String studentEmail;
    private Integer currencySpend;
}
