package com.reps.demogcloud.models.student;

import lombok.Getter;

@Getter
public class CurrencySpendRequest {
    private String studentEmail;
    private Integer currencyTransferred;
}
