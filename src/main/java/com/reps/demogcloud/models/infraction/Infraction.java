package com.reps.demogcloud.models.infraction;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.ArrayList;
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Document(collection = "Infractions")
public class Infraction {
    @Id
    private String infractionId;
    private String infractionName;
    private String infractionLevel;
}
