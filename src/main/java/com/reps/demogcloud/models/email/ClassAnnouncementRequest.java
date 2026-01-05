package com.reps.demogcloud.models.email;

import lombok.*;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ClassAnnouncementRequest {
    private String teacherEmail;
    private String className;
    private String subject;
    private String msg;
    private String preferredLanguage;

}
