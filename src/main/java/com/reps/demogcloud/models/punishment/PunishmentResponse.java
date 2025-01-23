package com.reps.demogcloud.models.punishment;

import lombok.*;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
public class PunishmentResponse {
    private Punishment punishment;
    private String error;
    private String parentToEmail;
    private String studentToEmail;
    private String teacherToEmail;
    private String subject;
    private String message;


}
