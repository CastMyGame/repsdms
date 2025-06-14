package com.reps.demogcloud.models.student;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;


@Data
@NoArgsConstructor
@AllArgsConstructor
public class TransferPointsRequest {
    @NotBlank(message = "Giving student email is required")
    private String givingStudentEmail;

    @NotBlank(message = "Receiving student email is required")
    private String receivingStudentEmail;

    @NotNull(message = "Points transferred is required")
    @Min(value = 1, message = "Must transfer at least 1 point")
    private Integer pointsTransferred;
}
