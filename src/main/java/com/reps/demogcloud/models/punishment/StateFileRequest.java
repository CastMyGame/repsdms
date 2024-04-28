package com.reps.demogcloud.models.punishment;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.*;

@Data
@Getter
@AllArgsConstructor
@NoArgsConstructor
public class StateFileRequest {
    private StateFormElement incidentTypeId;
    private Integer incidentConfigurationGroupId;
    private StateTimeElement versionDate;
    private StateTimeElement incidentDate;
    private StateFormElement reportedById;
    private Integer incidentPartyId;
    private StateFormElement currentUser;
    private StateFormElement studentId;
    private StateFormElement occurredAtOrganizationId;
    private StateFormElement locationId;
    private StateFormElement incidentBehavior;
    private String description;
    private List<StateFormElement> staffResponse;
    private Integer incidentRoleId;
    private boolean isReadyToAssignActions = false;
    private boolean behaviorRequiredForActions = true;
    private String studentNumber;
    private StateFormElement studentGrade;
    private StateFormElement organizationId;
    private StateFormElement isSpecialEd;
    private StateFormElement is504;
    private StateFormElement isHomeless;
    private String ruleInstanceToken;



}
