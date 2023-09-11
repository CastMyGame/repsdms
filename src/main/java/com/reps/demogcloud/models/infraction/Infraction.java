package com.reps.demogcloud.models.infraction;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@Document(collection = "Infractions")
public class Infraction {
    @Id
    private String id;
    private String infractionId;
    private String infractionName;
    private String infractionDescription;
    private String infractionType;
//    private String infractionUrl;
    private String infractionAssign;

    public Infraction(){};
    public Infraction(String id, String infractionId, String infractionName, String infractionDescription, String infractionType, String infractionAssign) {
        this.id = id;
        this.infractionId = infractionId;
        this.infractionName = infractionName;
        this.infractionDescription = infractionDescription;
        this.infractionType = infractionType;
        this.infractionAssign = infractionAssign;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getInfractionId() {
        return infractionId;
    }

    public void setInfractionId(String infractionId) {
        this.infractionId = infractionId;
    }

    public String getInfractionName() {
        return infractionName;
    }

    public void setInfractionName(String infractionName) {
        this.infractionName = infractionName;
    }

    public String getInfractionDescription() {
        return infractionDescription;
    }

    public void setInfractionDescription(String infractionDescription) {
        this.infractionDescription = infractionDescription;
    }

    public String getInfractionType() {
        return infractionType;
    }

    public void setInfractionType(String infractionType) {
        this.infractionType = infractionType;
    }

    public String getInfractionAssign() {
        return infractionAssign;
    }

    public void setInfractionAssign(String infractionAssign) {
        this.infractionAssign = infractionAssign;
    }
}
