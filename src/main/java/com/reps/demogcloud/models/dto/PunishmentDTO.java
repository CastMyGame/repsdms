package com.reps.demogcloud.models.dto;

import com.reps.demogcloud.models.punishment.Punishment;
import lombok.Data;

@Data
public class PunishmentDTO {
    String firstName;
    String lastName;
    String studentEmail;
    Punishment punishment;

}
