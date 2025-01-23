package com.reps.demogcloud.models.punishment;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

import java.util.*;

@Data
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class StateFileRequest {
    @JsonProperty("Parties")
    private List<String> parties;
    @JsonProperty("Incident_IncidentTypeId")
    private StateFormIntElement incidentTypeId;
    @JsonProperty("Incident_IncidentConfigurationGroupId")
    private Integer incidentConfigurationGroupId;
    @JsonProperty("Incident_VersionDate")
    private StateTimeElement versionDate;
    @JsonProperty("Incident_IncidentDate")
    private StateTimeElement incidentDate;
    @JsonProperty("Incident_ReportedById")
    private StateFormIntElement reportedById;
    @JsonProperty("IncidentParty_IncidentPartyId")
    private Integer incidentPartyId;
    @JsonProperty("CurrentUser")
    private StateFormIntElement currentUser;
    @JsonProperty("IncidentParty_IncidentPartyTypeId")
    private StateFormIntElement incidentPartyTypeId;
    @JsonProperty("IncidentParty_StudentId")
    private StateFormIntElement studentId;
    @JsonProperty("Incident_OccurredAtOrganizationId")
    private StateFormIntElement occurredAtOrganizationId;
    @JsonProperty("Incident_LocationId")
    private StateFormIntElement locationId;
    @JsonProperty("IncidentBehavior_LayoutFieldOptionId")
    private List<FieldOptionElement> incidentBehavior;
    @JsonProperty("IncidentParty_Description")
    private String description;
    @JsonProperty("IncidentStaffResponse_LayoutFieldOptionId")
    private List<StateFormIntElement> staffResponse;
    @JsonProperty("IncidentTypeRole_IncidentRoleId")
    private Integer incidentRoleId;
    @JsonProperty("IsReadyToAssignActions")
    private boolean isReadyToAssignActions = false;
    @JsonProperty("BehaviorRequiredForActions")
    private boolean behaviorRequiredForActions = true;
    @JsonProperty("IncidentParty_StudentNumber")
    private String studentNumber;
    @JsonProperty("IncidentParty_StudentGradeId")
    private StateFormIntElement studentGrade;
    @JsonProperty("IncidentParty_StudentOrganizationId")
    private StateFormIntElement organizationId;
    @JsonProperty("IncidentParty_StudentIsSpecialEducation")
    private StateFormBooleanElement isSpecialEd;
    @JsonProperty("IncidentParty_StudentIs504")
    private StateFormBooleanElement is504;
    @JsonProperty("IncidentParty_StudentHomelessTypeId")
    private StateFormIntElement isHomeless;
    @JsonProperty("RuleInstanceToken")
    private String ruleInstanceToken;

    // Getters and setters
}

