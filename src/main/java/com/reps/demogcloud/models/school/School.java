package com.reps.demogcloud.models.school;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Document(collection = "schools")
public class School {
    @Id
    private String schoolIdNumber;

    private String schoolName;

    private int maxPunishLevel = 4;

    private String currency;

    private String city;
    private String state;
    private String zip;

    @Indexed(unique = true)
    private String normalizedKey;

}
