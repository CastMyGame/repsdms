package com.reps.demogcloud.security.models.contactus;

import lombok.*;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
public class ContactUsRequest {
    private String email;
    private String subject;
    private String message;

}
