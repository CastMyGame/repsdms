package com.reps.demogcloud.models.student;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CurrencySpendRequest {
    private String studentEmail;
    private Integer currencyTransferred;
}
