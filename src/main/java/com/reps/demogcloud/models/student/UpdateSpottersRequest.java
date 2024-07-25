package com.reps.demogcloud.models.student;

import lombok.Getter;

import java.util.List;

@Getter
public class UpdateSpottersRequest {

    private List<String> spotters;
    private String studentEmail;
}
