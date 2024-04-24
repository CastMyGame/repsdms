package com.reps.demogcloud.models.employee;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class CurrencyTransferRequest {
    private String teacherEmail;
    private String studentEmail;
    private Integer currencyTransferred;
}
