package com.reps.demogcloud.models.punishment;

import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import java.util.List;


@Data
@Setter
@Getter
public class IncidentRequest360Review {

    private List<Object> Parties;
    private IncidentType Incident_IncidentTypeId;
    private int Incident_IncidentConfigurationGroupId;
    private IncidentDate Incident_VersionDate;
    private IncidentDateTime Incident_IncidentDate;
    private IncidentPerson Incident_ReportedById;
    private IncidentOrganization Incident_OccurredAtOrganizationId;
    private int IncidentParty_IncidentPartyId;
    private IncidentPerson CurrentUser;
    private IncidentParty IncidentParty_IncidentPartyTypeId;
    private IncidentPerson IncidentParty_StudentId;
    private IncidentLocation Incident_LocationId;
    private List<IncidentBehavior> IncidentBehavior_LayoutFieldOptionId;
    private String IncidentParty_Description;
    private List<IncidentResponse> IncidentStaffResponse_LayoutFieldOptionId;
    private int IncidentTypeRole_IncidentRoleId;
    private boolean IsReadyToAssignActions;
    private boolean BehaviorRequiredForActions;
    private String IncidentParty_StudentNumber;
    private IncidentGrade IncidentParty_StudentGradeId;
    private IncidentOrganization IncidentParty_StudentOrganizationId;
    private IncidentSpecialEducation IncidentParty_StudentIsSpecialEducation;
    private IncidentSpecialEducation IncidentParty_StudentIs504;
    private IncidentHomelessType IncidentParty_StudentHomelessTypeId;
    private Object RuleInstanceToken;

    // Constructor, getters and setters

    public static class IncidentType {
        private int value;
        private String text;

        // Constructor, getters, and setters
    }

    public static class IncidentDate {
        private String date;
        private Object time;

        // Constructor, getters, and setters
    }

    public static class IncidentDateTime {
        private String date;
        private String time;

        // Constructor, getters, and setters
    }

    public static class IncidentPerson {
        private int value;
        private String text;

        // Constructor, getters, and setters
    }

    public static class IncidentOrganization {
        private int value;
        private String text;

        // Constructor, getters, and setters
    }

    public static class IncidentParty {
        private int value;
        private String text;

        // Constructor, getters, and setters
    }

    public static class IncidentLocation {
        private int value;
        private String text;

        // Constructor, getters, and setters
    }

    public static class IncidentBehavior {
        private int value;
        private String text;
        private Object isPrimary;
        private boolean isRemoved;

        // Constructor, getters, and setters
    }

    public static class IncidentResponse {
        private int value;
        private String text;

        // Constructor, getters, and setters
    }

    public static class IncidentGrade {
        private String text;
        private int value;

        // Constructor, getters, and setters
    }

    public static class IncidentSpecialEducation {
        private String text;
        private boolean value;

        // Constructor, getters, and setters
    }

    public static class IncidentHomelessType {
        private String text;
        private int value;

        // Constructor, getters, and setters
    }

    // Constructor, getters, and setters for Incident class fields
}
