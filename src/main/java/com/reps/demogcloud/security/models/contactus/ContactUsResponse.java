package com.reps.demogcloud.security.models.contactus;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
public class ContactUsResponse {
    private ContactUsRequest request;
    private String error;
}
