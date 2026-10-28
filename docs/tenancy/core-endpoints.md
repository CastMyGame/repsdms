# Phase 4A endpoint and caller inventory

Backend baseline: dev 2e661f3 (PR #179 merged). Frontend baseline: dev c302615. Static source inventory; no endpoint requests or deployed data reads. All listed production endpoints retain existing authorization in this preparation PR. See core-workflows.md for policy/dependency details and deferred paths.

## StudentController

Source: `src/main/java/com/reps/demogcloud/controllers/StudentController.java`.

| Method | Path | Source line |
| --- | --- | --- |
| GET | `/student/v1/enrollment-directory` | 31 |
| GET | `/student/v1/` | 36 |
| GET | `/student/v1/studentid/{studentId}` | 41 |
| GET | `/student/v1/allStudents` | 52 |
| GET | `/student/v1/allStudents/` | 52 |
| GET | `/student/v1/archived` | 62 |
| GET | `/student/v1/lastname/{lastName}` | 70 |
| GET | `/student/v1/email/{email}` | 80 |
| GET | `/student/v1/parentEmail/{parentEmail}` | 89 |
| GET | `/student/v1/detentionList/{school}` | 98 |
| GET | `/student/v1/issList/{school}` | 107 |
| GET | `/student/v1/findBySpotter/{spotterEmail}` | 117 |
| POST | `/student/v1/newStudent` | 128 |
| POST | `/student/v1/addStudents` | 136 |
| POST | `/student/v1/points/add` | 148 |
| POST | `/student/v1/points/delete` | 156 |
| POST | `/student/v1/points/transfer` | 164 |
| POST | `/student/v1/getByEmailList` | 174 |
| POST | `/student/v1/{studentEmail}/add-time` | 185 |
| PUT | `/student/v1/assignSchool` | 197 |
| PUT | `/student/v1/updateStudents` | 206 |
| PUT | `/student/v1/archived/{studentId}` | 216 |
| PUT | `/student/v1/notes/{id}` | 224 |
| PUT | `/student/v1/addAsSpotter` | 233 |
| PUT | `/student/v1/removeAsSpotter` | 240 |
| DELETE | `/student/v1/delete` | 250 |
| PUT | `/student/v1/remove-spotter/{email}` | 260 |

## EmployeeController

Source: `src/main/java/com/reps/demogcloud/controllers/EmployeeController.java`.

| Method | Path | Source line |
| --- | --- | --- |
| GET | `/employees/v1/employees` | 36 |
| GET | `/employees/v1/employees/email/{email}` | 43 |
| GET | `/employees/v1/employees/{role}` | 50 |
| POST | `/employees/v1/employees` | 63 |
| POST | `/employees/v1/employees/list` | 69 |
| PUT | `/employees/v1/employees/{id}/roles` | 75 |
| PUT | `/employees/v1/currency/spend` | 80 |
| PUT | `/employees/v1/{school}` | 89 |
| PUT | `/employees/v1/updateClass/{teacherEmail}` | 101 |
| PUT | `/employees/v1/updateAll` | 113 |
| DELETE | `/employees/v1/employees/{id}` | 125 |
| POST | `/employees/v1/deleteClass/{teacherEmail}` | 131 |

## PunishController

Source: `src/main/java/com/reps/demogcloud/controllers/PunishController.java`.

| Method | Path | Source line |
| --- | --- | --- |
| GET | `/punish/v1/punishments` | 34 |
| GET | `/punish/v1/archived` | 41 |
| GET | `/punish/v1/student/punishments/{studentEmail}` | 48 |
| GET | `/punish/v1/{id}` | 56 |
| GET | `/punish/v1/punishStatus/{status}` | 64 |
| GET | `/punish/v1/student/{email}` | 72 |
| GET | `/punish/v1/openPunishments` | 80 |
| GET | `/punish/v1/punishments/{studentEmail}` | 88 |
| POST | `/punish/v1/punishId/close` | 96 |
| POST | `/punish/v1/close/{id}` | 104 |
| POST | `/punish/v1/startPunish/form` | 113 |
| POST | `/punish/v1/startPunish/formList` | 122 |
| POST | `/punish/v1/studentsReport/{studentEmail}` | 141 |
| PUT | `/punish/v1/{id}/index/{index}` | 151 |
| PUT | `/punish/v1/archived/{userId}/{punishmentId}` | 158 |
| PUT | `/punish/v1/archived/restore/{punishmentId}` | 165 |
| PUT | `/punish/v1/rejected/{punishmentId}` | 172 |
| PUT | `/punish/v1/updates` | 179 |
| PUT | `/punish/v1/descriptions` | 187 |
| PUT | `/punish/v1/emails` | 195 |
| PUT | `/punish/v1/schoolName` | 203 |
| PUT | `/punish/v1/infractionName` | 211 |
| PUT | `/punish/v1/infractionLevel` | 219 |
| DELETE | `/punish/v1/delete` | 229 |

## OfficeReferralController

Source: `src/main/java/com/reps/demogcloud/controllers/OfficeReferralController.java`.

| Method | Path | Source line |
| --- | --- | --- |
| GET | `/officeReferral/v1/punishments` | 32 |
| GET | `/officeReferral/v1/id/{id}` | 40 |
| GET | `/officeReferral/v1/admin/{email}` | 50 |
| POST | `/officeReferral/v1/startPunish/adminReferral` | 59 |
| POST | `/officeReferral/v1/closeId` | 70 |
| POST | `/officeReferral/v1/submit/{id}` | 79 |
| PUT | `/officeReferral/v1/{id}/index/{index}` | 88 |
| PUT | `/officeReferral/v1/rejected/{punishmentId}` | 97 |
| PUT | `/officeReferral/v1/descriptions` | 105 |

## GuidanceController

Source: `src/main/java/com/reps/demogcloud/controllers/GuidanceController.java`.

| Method | Path | Source line |
| --- | --- | --- |
| GET | `/guidance/v1/referrals` | 36 |
| GET | `/guidance/v1/referrals/` | 36 |
| GET | `/guidance/v1/guidanceStatus/{status}` | 44 |
| POST | `/guidance/v1/guidance/new` | 53 |
| PUT | `/guidance/v1/guidance/notes/{id}` | 62 |
| PUT | `/guidance/v1/guidance/followup/{id}` | 74 |
| PUT | `/guidance/v1/guidance/status/{id}` | 94 |
| PUT | `/guidance/v1/guidance/resources/{id}` | 104 |
| DELETE | `/guidance/v1/guidance/delete/{id}` | 113 |

## TrackedBehaviorController

Source: `src/main/java/com/reps/demogcloud/controllers/TrackedBehaviorController.java`.

| Method | Path | Source line |
| --- | --- | --- |
| POST | `/tracked-behaviors/v1/save` | 22 |
| GET | `/tracked-behaviors/v1/student/{studentEmail}/timeline` | 29 |
| GET | `/tracked-behaviors/v1/student/{studentEmail}/totals` | 36 |
| GET | `/tracked-behaviors/v1/types` | 43 |
| POST | `/tracked-behaviors/v1/students/totals` | 48 |

## AssignmentController

Source: `src/main/java/com/reps/demogcloud/controllers/AssignmentController.java`.

| Method | Path | Source line |
| --- | --- | --- |
| GET | `/assignments/v1/` | 32 |
| GET | `/assignments/v1/templates/for-punishment/{punishmentId}` | 39 |
| GET | `/assignments/v1/templates/search` | 49 |
| GET | `/assignments/v1/templates` | 70 |
| GET | `/assignments/v1/templates/by-infraction` | 78 |
| GET | `/assignments/v1/templates/{id}` | 90 |
| GET | `/assignments/v1/{id}/assignment-template` | 97 |
| GET | `/assignments/v1/by-punishment/{punishmentId}` | 115 |
| POST | `/assignments/v1/` | 127 |
| POST | `/assignments/v1/migrate-legacy` | 134 |
| POST | `/assignments/v1/templates` | 142 |
| PUT | `/assignments/v1/{id}` | 151 |
| PUT | `/assignments/v1/templates/{id}` | 161 |
| DELETE | `/assignments/v1/delete/{assignmentName}` | 173 |
| DELETE | `/assignments/v1/templates/{id}` | 180 |

## AssignmentBindingController

Source: `src/main/java/com/reps/demogcloud/controllers/AssignmentBindingController.java`.

| Method | Path | Source line |
| --- | --- | --- |
| POST | `/assignments/v1/bindings/teacher-default` | 33 |
| DELETE | `/assignments/v1/bindings/teacher-default` | 53 |
| GET | `/assignments/v1/bindings/teacher-default` | 70 |
| GET | `/assignments/v1/bindings/by-template/{templateId}` | 84 |

## AssignmentLifecycleController

Source: `src/main/java/com/reps/demogcloud/controllers/AssignmentLifecycleController.java`.

| Method | Path | Source line |
| --- | --- | --- |
| GET | `/assignments/v1/cases/{id}` | 11 |
| PUT | `/assignments/v1/cases/{id}/{operation}` | 12 |

## DTOController

Source: `src/main/java/com/reps/demogcloud/controllers/DTOController.java`.

| Method | Path | Source line |
| --- | --- | --- |
| GET | `/DTO/v1/AdminOverviewData` | 40 |
| GET | `/DTO/v1/TeacherOverviewData` | 51 |
| GET | `/DTO/v1/punishmentsDTO` | 71 |
| GET | `/DTO/v1/StudentOverviewData` | 81 |
| GET | `/DTO/v1/StudentOverviewData/{studentEmail}` | 92 |

## EmailController

Source: `src/main/java/com/reps/demogcloud/controllers/EmailController.java`.

| Method | Path | Source line |
| --- | --- | --- |
| GET | `/email/v1/notifications` | 25 |
| GET | `/email/v1/notifications/{kind}/{caseId}` | 28 |
| POST | `/email/v1/notifications/{kind}/{caseId}/{notificationId}/retry` | 33 |
| POST | `/email/v1/notifications/{kind}/{caseId}/{notificationId}/resolve` | 40 |
| POST | `/email/v1/classAnnouncement` | 52 |

## security/AccountAccessController

Source: `src/main/java/com/reps/demogcloud/security/controllers/AccountAccessController.java`.

| Method | Path | Source line |
| --- | --- | --- |
| POST | `/account-access/complete` | 11 |
| POST | `/accounts/v1/{id}/invitation` | 16 |
| GET | `/accounts/v1/students/{email}/access` | 18 |
| POST | `/accounts/v1/students/{email}/invitation` | 22 |
| POST | `/accounts/v1/students/{email}/setup` | 27 |

## security/UserControllers

Source: `src/main/java/com/reps/demogcloud/security/controllers/UserControllers.java`.

| Method | Path | Source line |
| --- | --- | --- |
| GET | `/users/v1/users` | 21 |
| GET | `/users/v1/users/{role}` | 25 |
| POST | `/users/v1/users/create/{school}` | 30 |
| PUT | `/users/v1/users/{school}` | 34 |
| PUT | `/users/v1/users/{id}/roles` | 39 |
| PUT | `/users/v1/users/{id}/enabled` | 43 |
| DELETE | `/users/v1/users/{id}` | 51 |

## security/AuthControllers

Source: `src/main/java/com/reps/demogcloud/security/controllers/AuthControllers.java`.

| Method | Path | Source line |
| --- | --- | --- |
| GET | `/auth/gmail/status` | 85 |
| POST | `/auth/gmail/connect` | 91 |
| POST | `/auth/gmail/complete` | 102 |
| POST | `/auth/gmail/disconnect` | 113 |
| POST | `/v1/logout` | 127 |
| GET | `/test` | 145 |
| POST | `/register` | 150 |
| POST | `/auth` | 156 |
| GET | `/auth/capabilities` | 185 |
| POST | `/users/create/{school}` | 218 |
| GET | `/v1/token-status` | 224 |
| POST | `/v1/renew-token` | 247 |
| POST | `/forgot-password` | 274 |
| POST | `/reset-password` | 280 |
| POST | `/contact-us` | 286 |

## Frontend caller/dependency locations

Matching desktop/mobile callers, shared HTTP/service wrappers and type dependencies below remain unchanged. Line numbers refer to frontend dev c302615; tests were excluded from this caller list. The API inventory above also includes legacy endpoints with no matching current caller. Dynamic URL composition must still be rechecked at cutover.

| Frontend source | Matching lines |
| --- | --- |
| `src/components/globalComponents/components/generic-components/classUpdate.tsx` | 54, 83, 128 |
| `src/components/globalComponents/components/generic-components/manageSpotters.tsx` | 36, 90, 113 |
| `src/components/globalComponents/components/generic-components/manageSpottersPopup.tsx` | 51, 53, 115, 116 |
| `src/components/globalComponents/components/modals/studentDetailsModal.tsx` | 55, 66 |
| `src/components/globalComponents/components/modals/teacherDetailsModal .tsx` | 55, 66 |
| `src/components/globalComponents/dataDisplay/adminSchoolReferralByTypePieChart.tsx` | 11, 20, 27, 33, 80 |
| `src/components/globalComponents/dataDisplay/incident-by-teacher-pie-chart.tsx` | 12, 19, 27 |
| `src/components/globalComponents/dataDisplay/incidentsByStudentTable.tsx` | 20, 26, 34, 38, 39, 94 |
| `src/components/globalComponents/dataDisplay/pieChartParentCommunication.tsx` | 12, 21, 25 |
| `src/components/globalComponents/dataDisplay/referralsByWeek.tsx` | 19, 24, 37, 38, 82, 137, 143 |
| `src/components/globalComponents/dataDisplay/studentRecentContacts.tsx` | 12, 26, 101 |
| `src/components/globalComponents/detentionTimer/detentionTimer.tsx` | 53 |
| `src/components/globalComponents/detentionWidget.tsx` | 17 |
| `src/components/globalComponents/issWidget.tsx` | 17 |
| `src/components/globalComponents/modals/functions/createGuidanceReportPanel.js` | 135, 148, 197 |
| `src/components/globalComponents/modals/notes/notes.tsx` | 30 |
| `src/components/globalComponents/modals/requestModal/guidanceRequestModal.tsx` | 35 |
| `src/components/globalComponents/officeReferrals/officeReferrals.tsx` | 69, 436 |
| `src/components/globalComponents/referrals/AssignmentStaffReview.tsx` | 22 |
| `src/components/globalComponents/referrals/createOfficeReferral.tsx` | 125, 201 |
| `src/components/globalComponents/referrals/createPunishmentPanel.tsx` | 173, 260 |
| `src/components/globalComponents/referrals/globalArchivedPunishmentPanel.tsx` | 63, 67, 156, 185 |
| `src/components/globalComponents/referrals/globalPunishmentPanel.tsx` | 73, 74, 192, 218 |
| `src/components/globalComponents/referrals/levelThreePanel.tsx` | 61, 162 |
| `src/components/globalComponents/spendPage/spend-page.tsx` | 29, 58, 93 |
| `src/components/globalComponents/trackedBehaviorPanel.tsx` | 174, 196, 202, 295, 324, 339, 664 |
| `src/components/globalComponents/users/SchoolAccountManagement.tsx` | 203 |
| `src/components/globalComponents/users/addTeacherForm.tsx` | 174 |
| `src/components/globalComponents/users/createNewStudentPanel.tsx` | 81 |
| `src/components/globalComponents/users/editStudentPanel.tsx` | 25, 71 |
| `src/components/mobile/admin/MobileAdminCreateRecord.tsx` | 44, 45, 78 |
| `src/components/mobile/admin/MobileAdminCreateRecordV2.tsx` | 55, 56, 108 |
| `src/components/mobile/admin/MobileAdminCreateStudentTeacher.tsx` | 51, 52, 105 |
| `src/components/mobile/admin/MobileAdminCreateStudentTeacherV2.tsx` | 27, 34 |
| `src/components/mobile/admin/MobileAdminHome.tsx` | 24, 31 |
| `src/components/mobile/admin/MobileAdminHomeV2.tsx` | 22 |
| `src/components/mobile/admin/MobileAdminOpenWriteUps.tsx` | 31 |
| `src/components/mobile/admin/MobileAdminPositiveBehavior.tsx` | 20 |
| `src/components/mobile/student/MobileStudentHome.tsx` | 15, 27, 51 |
| `src/components/mobile/teacher/MobileTeacherAddStudent.tsx` | 44, 51, 99, 122, 338 |
| `src/components/mobile/teacher/MobileTeacherAnalysis.tsx` | 27, 31, 32 |
| `src/components/mobile/teacher/MobileTeacherClasses.tsx` | 22, 28 |
| `src/components/mobile/teacher/MobileTeacherHome.tsx` | 26, 37 |
| `src/components/mobile/teacher/MobileTeacherRecords.tsx` | 29, 33, 47, 48 |
| `src/components/mobile/teacher/MobileTeacherShoutOut.tsx` | 32, 38, 63 |
| `src/components/roles/admin/adminDashboard.tsx` | 70 |
| `src/components/roles/admin/adminOverview.tsx` | 9, 35, 50, 64, 92, 134 |
| `src/components/roles/admin/adminTeacherPanel.tsx` | 43, 74 |
| `src/components/roles/admin/modals/teacher_profile_modal.tsx` | 53, 80, 111 |
| `src/components/roles/admin/modals/teacher_profile_widget_spotter.tsx` | 27, 54 |
| `src/components/roles/admin/reports/PDFReport.tsx` | 99, 119 |
| `src/components/roles/guidance/guidance-dashboard-v2.tsx` | 47, 66 |
| `src/components/roles/guidance/guidance-dashboard.tsx` | 60, 150, 166, 168, 179, 188, 198 |
| `src/components/roles/student/DashboardStudent .tsx` | 52, 56 |
| `src/components/roles/teacher/FTCpanel.tsx` | 46, 47, 97 |
| `src/components/roles/teacher/guidancePanelOverview.tsx` | 91, 110, 117, 129 |
| `src/components/roles/teacher/teacherDashboard.tsx` | 48, 67 |
| `src/components/roles/teacher/teacherOverview.tsx` | 89, 119, 129, 141 |
| `src/components/roles/teacher/teacherStudentPanel.tsx` | 80, 85, 120, 133, 149, 193, 237, 284, 663, 665, 766, 768 |
| `src/forms/FailureToComplete.tsx` | 30, 45, 102 |
| `src/helperComponents/helperComponents.tsx` | 201, 263 |
| `src/security/contactUsModal.tsx` | 127 |
| `src/types/responses.tsx` | 64, 118, 131, 143 |
| `src/utils/AssignmentManager.tsx` | 150, 172, 195 |
| `src/utils/api/assignmentApi.tsx` | 33, 42, 55, 64, 78 |
| `src/utils/api/assignmentLifecycle.ts` | 16, 19 |
| `src/utils/api/http.tsx` | 27 |
| `src/utils/guidanceSecurity.ts` | 2 |
