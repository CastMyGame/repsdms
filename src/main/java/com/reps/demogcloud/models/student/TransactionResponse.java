package com.reps.demogcloud.models.student;

import lombok.AllArgsConstructor;

@AllArgsConstructor
public class TransactionResponse {
    private TransactionRequest request;
    private String error;
}
