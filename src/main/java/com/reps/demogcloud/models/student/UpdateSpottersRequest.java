package com.reps.demogcloud.models.student;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class UpdateSpottersRequest {

    private List<String> spotters;
    private List<String> studentEmail;
}
