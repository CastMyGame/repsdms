package com.reps.demogcloud.models.school;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Document(collection = "schools")
public class School {
    @Id
    private String schoolIdNumber;
    private String schoolName;
    private int maxPunishLevel;
    private String currency;

}
