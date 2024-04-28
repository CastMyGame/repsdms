package com.reps.demogcloud.models.punishment;

import lombok.*;

import java.util.*;

@Data
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class StateFileRequest {
    private StateFormIntElement incidentTypeId;
    private Integer incidentConfigurationGroupId;
    private StateTimeElement versionDate;
    private StateTimeElement incidentDate;
    private StateFormIntElement reportedById;
    private Integer incidentPartyId;
    private StateFormIntElement currentUser;
    private StateFormIntElement studentId;
    private StateFormIntElement occurredAtOrganizationId;
    private StateFormIntElement locationId;
    private StateFormIntElement incidentBehavior;
    private String description;
    private List<StateFormIntElement> staffResponse;
    private Integer incidentRoleId;
    private boolean isReadyToAssignActions = false;
    private boolean behaviorRequiredForActions = true;
    private String studentNumber;
    private StateFormIntElement studentGrade;
    private StateFormIntElement organizationId;
    private StateFormBooleanElement isSpecialEd;
    private StateFormBooleanElement is504;
    private StateFormIntElement isHomeless;
    private String ruleInstanceToken;

}
