package com.reps.demogcloud.models.student;

import lombok.*;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class StudentResponse {
    private String error;
    private Student student;

}