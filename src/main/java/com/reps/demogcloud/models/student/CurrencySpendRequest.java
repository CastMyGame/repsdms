package com.reps.demogcloud.models.student;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CurrencySpendRequest {
    private String studentEmail;
    @com.fasterxml.jackson.databind.annotation.JsonDeserialize(using=com.reps.demogcloud.models.PositiveWholeCurrencyDeserializer.class)
    private Integer currencyTransferred;
}
