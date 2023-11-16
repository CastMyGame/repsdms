package com.reps.demogcloud.models.punishment;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.joda.time.DateTime;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ClosePunishmentRequest {

    private String infractionName;
    private String studentEmail;
    private ArrayList<String> studentAnswer;
}
