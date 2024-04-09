package com.reps.demogcloud.exceptions;

import com.reps.demogcloud.models.infraction.Infraction;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Objects;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class GenericResponse {
    private String error;
    private Objects objects;
    int status;





}
