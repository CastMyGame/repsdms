package com.reps.demogcloud.models.infraction;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;


public class InfractionResponse {
    private String error;
    private Infraction infraction;

    public InfractionResponse(){};

    public InfractionResponse(String error, Infraction infraction) {
        this.error = error;
        this.infraction = infraction;
    }

    public String getError() {
        return error;
    }

    public void setError(String error) {
        this.error = error;
    }

    public Infraction getInfraction() {
        return infraction;
    }

    public void setInfraction(Infraction infraction) {
        this.infraction = infraction;
    }
}
