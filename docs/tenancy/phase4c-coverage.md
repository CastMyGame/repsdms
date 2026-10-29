# Phase 4C route and worker matrix

Static baseline: the 144 routes/aliases from Phase 4A, now mapped to the actual service integration. This is a source coverage inventory, not an assertion that every route was exercised by a test. See phase4c-integration.md for boundaries, test evidence and blocked legacy operations.

| Controller | Method | Route/alias | Canonical implementation |
| --- | --- | --- | --- |
| StudentController | GET | `/student/v1/enrollment-directory` | StudentQuery/Mutation/Admin/Spotter; destructive delete and legacy points retired |
| StudentController | GET | `/student/v1/` | StudentQuery/Mutation/Admin/Spotter; destructive delete and legacy points retired |
| StudentController | GET | `/student/v1/studentid/{studentId}` | StudentQuery/Mutation/Admin/Spotter; destructive delete and legacy points retired |
| StudentController | GET | `/student/v1/allStudents` | StudentQuery/Mutation/Admin/Spotter; destructive delete and legacy points retired |
| StudentController | GET | `/student/v1/allStudents/` | StudentQuery/Mutation/Admin/Spotter; destructive delete and legacy points retired |
| StudentController | GET | `/student/v1/archived` | StudentQuery/Mutation/Admin/Spotter; destructive delete and legacy points retired |
| StudentController | GET | `/student/v1/lastname/{lastName}` | StudentQuery/Mutation/Admin/Spotter; destructive delete and legacy points retired |
| StudentController | GET | `/student/v1/email/{email}` | StudentQuery/Mutation/Admin/Spotter; destructive delete and legacy points retired |
| StudentController | GET | `/student/v1/parentEmail/{parentEmail}` | StudentQuery/Mutation/Admin/Spotter; destructive delete and legacy points retired |
| StudentController | GET | `/student/v1/detentionList/{school}` | StudentQuery/Mutation/Admin/Spotter; destructive delete and legacy points retired |
| StudentController | GET | `/student/v1/issList/{school}` | StudentQuery/Mutation/Admin/Spotter; destructive delete and legacy points retired |
| StudentController | GET | `/student/v1/findBySpotter/{spotterEmail}` | StudentQuery/Mutation/Admin/Spotter; destructive delete and legacy points retired |
| StudentController | POST | `/student/v1/newStudent` | StudentQuery/Mutation/Admin/Spotter; destructive delete and legacy points retired |
| StudentController | POST | `/student/v1/addStudents` | StudentQuery/Mutation/Admin/Spotter; destructive delete and legacy points retired |
| StudentController | POST | `/student/v1/points/add` | StudentQuery/Mutation/Admin/Spotter; destructive delete and legacy points retired |
| StudentController | POST | `/student/v1/points/delete` | StudentQuery/Mutation/Admin/Spotter; destructive delete and legacy points retired |
| StudentController | POST | `/student/v1/points/transfer` | StudentQuery/Mutation/Admin/Spotter; destructive delete and legacy points retired |
| StudentController | POST | `/student/v1/getByEmailList` | StudentQuery/Mutation/Admin/Spotter; destructive delete and legacy points retired |
| StudentController | POST | `/student/v1/{studentEmail}/add-time` | StudentQuery/Mutation/Admin/Spotter; destructive delete and legacy points retired |
| StudentController | PUT | `/student/v1/assignSchool` | StudentQuery/Mutation/Admin/Spotter; destructive delete and legacy points retired |
| StudentController | PUT | `/student/v1/updateStudents` | StudentQuery/Mutation/Admin/Spotter; destructive delete and legacy points retired |
| StudentController | PUT | `/student/v1/archived/{studentId}` | StudentQuery/Mutation/Admin/Spotter; destructive delete and legacy points retired |
| StudentController | PUT | `/student/v1/notes/{id}` | StudentQuery/Mutation/Admin/Spotter; destructive delete and legacy points retired |
| StudentController | PUT | `/student/v1/addAsSpotter` | StudentQuery/Mutation/Admin/Spotter; destructive delete and legacy points retired |
| StudentController | PUT | `/student/v1/removeAsSpotter` | StudentQuery/Mutation/Admin/Spotter; destructive delete and legacy points retired |
| StudentController | DELETE | `/student/v1/delete` | StudentQuery/Mutation/Admin/Spotter; destructive delete and legacy points retired |
| StudentController | PUT | `/student/v1/remove-spotter/{email}` | StudentQuery/Mutation/Admin/Spotter; destructive delete and legacy points retired |
| EmployeeController | GET | `/employees/v1/employees` | UserAdministration + EmployeeQuery/Creation/Class + CurrencyService |
| EmployeeController | GET | `/employees/v1/employees/email/{email}` | UserAdministration + EmployeeQuery/Creation/Class + CurrencyService |
| EmployeeController | GET | `/employees/v1/employees/{role}` | UserAdministration + EmployeeQuery/Creation/Class + CurrencyService |
| EmployeeController | POST | `/employees/v1/employees` | UserAdministration + EmployeeQuery/Creation/Class + CurrencyService |
| EmployeeController | POST | `/employees/v1/employees/list` | UserAdministration + EmployeeQuery/Creation/Class + CurrencyService |
| EmployeeController | PUT | `/employees/v1/employees/{id}/roles` | UserAdministration + EmployeeQuery/Creation/Class + CurrencyService |
| EmployeeController | PUT | `/employees/v1/currency/spend` | UserAdministration + EmployeeQuery/Creation/Class + CurrencyService |
| EmployeeController | PUT | `/employees/v1/{school}` | UserAdministration + EmployeeQuery/Creation/Class + CurrencyService |
| EmployeeController | PUT | `/employees/v1/updateClass/{teacherEmail}` | UserAdministration + EmployeeQuery/Creation/Class + CurrencyService |
| EmployeeController | PUT | `/employees/v1/updateAll` | UserAdministration + EmployeeQuery/Creation/Class + CurrencyService |
| EmployeeController | DELETE | `/employees/v1/employees/{id}` | UserAdministration + EmployeeQuery/Creation/Class + CurrencyService |
| EmployeeController | POST | `/employees/v1/deleteClass/{teacherEmail}` | UserAdministration + EmployeeQuery/Creation/Class + CurrencyService |
| PunishController | GET | `/punish/v1/punishments` | PunishmentCreation/Query/Closure; legacy maintenance/state reporting blocked |
| PunishController | GET | `/punish/v1/archived` | PunishmentCreation/Query/Closure; legacy maintenance/state reporting blocked |
| PunishController | GET | `/punish/v1/student/punishments/{studentEmail}` | PunishmentCreation/Query/Closure; legacy maintenance/state reporting blocked |
| PunishController | GET | `/punish/v1/{id}` | PunishmentCreation/Query/Closure; legacy maintenance/state reporting blocked |
| PunishController | GET | `/punish/v1/punishStatus/{status}` | PunishmentCreation/Query/Closure; legacy maintenance/state reporting blocked |
| PunishController | GET | `/punish/v1/student/{email}` | PunishmentCreation/Query/Closure; legacy maintenance/state reporting blocked |
| PunishController | GET | `/punish/v1/openPunishments` | PunishmentCreation/Query/Closure; legacy maintenance/state reporting blocked |
| PunishController | GET | `/punish/v1/punishments/{studentEmail}` | PunishmentCreation/Query/Closure; legacy maintenance/state reporting blocked |
| PunishController | POST | `/punish/v1/punishId/close` | PunishmentCreation/Query/Closure; legacy maintenance/state reporting blocked |
| PunishController | POST | `/punish/v1/close/{id}` | PunishmentCreation/Query/Closure; legacy maintenance/state reporting blocked |
| PunishController | POST | `/punish/v1/startPunish/form` | PunishmentCreation/Query/Closure; legacy maintenance/state reporting blocked |
| PunishController | POST | `/punish/v1/startPunish/formList` | PunishmentCreation/Query/Closure; legacy maintenance/state reporting blocked |
| PunishController | POST | `/punish/v1/studentsReport/{studentEmail}` | PunishmentCreation/Query/Closure; legacy maintenance/state reporting blocked |
| PunishController | PUT | `/punish/v1/{id}/index/{index}` | PunishmentCreation/Query/Closure; legacy maintenance/state reporting blocked |
| PunishController | PUT | `/punish/v1/archived/{userId}/{punishmentId}` | PunishmentCreation/Query/Closure; legacy maintenance/state reporting blocked |
| PunishController | PUT | `/punish/v1/archived/restore/{punishmentId}` | PunishmentCreation/Query/Closure; legacy maintenance/state reporting blocked |
| PunishController | PUT | `/punish/v1/rejected/{punishmentId}` | PunishmentCreation/Query/Closure; legacy maintenance/state reporting blocked |
| PunishController | PUT | `/punish/v1/updates` | PunishmentCreation/Query/Closure; legacy maintenance/state reporting blocked |
| PunishController | PUT | `/punish/v1/descriptions` | PunishmentCreation/Query/Closure; legacy maintenance/state reporting blocked |
| PunishController | PUT | `/punish/v1/emails` | PunishmentCreation/Query/Closure; legacy maintenance/state reporting blocked |
| PunishController | PUT | `/punish/v1/schoolName` | PunishmentCreation/Query/Closure; legacy maintenance/state reporting blocked |
| PunishController | PUT | `/punish/v1/infractionName` | PunishmentCreation/Query/Closure; legacy maintenance/state reporting blocked |
| PunishController | PUT | `/punish/v1/infractionLevel` | PunishmentCreation/Query/Closure; legacy maintenance/state reporting blocked |
| PunishController | DELETE | `/punish/v1/delete` | PunishmentCreation/Query/Closure; legacy maintenance/state reporting blocked |
| OfficeReferralController | GET | `/officeReferral/v1/punishments` | OfficeReferralService + OfficeReferralUtils; no student assignments |
| OfficeReferralController | GET | `/officeReferral/v1/id/{id}` | OfficeReferralService + OfficeReferralUtils; no student assignments |
| OfficeReferralController | GET | `/officeReferral/v1/admin/{email}` | OfficeReferralService + OfficeReferralUtils; no student assignments |
| OfficeReferralController | POST | `/officeReferral/v1/startPunish/adminReferral` | OfficeReferralService + OfficeReferralUtils; no student assignments |
| OfficeReferralController | POST | `/officeReferral/v1/closeId` | OfficeReferralService + OfficeReferralUtils; no student assignments |
| OfficeReferralController | POST | `/officeReferral/v1/submit/{id}` | OfficeReferralService + OfficeReferralUtils; no student assignments |
| OfficeReferralController | PUT | `/officeReferral/v1/{id}/index/{index}` | OfficeReferralService + OfficeReferralUtils; no student assignments |
| OfficeReferralController | PUT | `/officeReferral/v1/rejected/{punishmentId}` | OfficeReferralService + OfficeReferralUtils; no student assignments |
| OfficeReferralController | PUT | `/officeReferral/v1/descriptions` | OfficeReferralService + OfficeReferralUtils; no student assignments |
| GuidanceController | GET | `/guidance/v1/referrals` | GuidanceService + CanonicalDomainAccess + managed resources |
| GuidanceController | GET | `/guidance/v1/referrals/` | GuidanceService + CanonicalDomainAccess + managed resources |
| GuidanceController | GET | `/guidance/v1/guidanceStatus/{status}` | GuidanceService + CanonicalDomainAccess + managed resources |
| GuidanceController | POST | `/guidance/v1/guidance/new` | GuidanceService + CanonicalDomainAccess + managed resources |
| GuidanceController | PUT | `/guidance/v1/guidance/notes/{id}` | GuidanceService + CanonicalDomainAccess + managed resources |
| GuidanceController | PUT | `/guidance/v1/guidance/followup/{id}` | GuidanceService + CanonicalDomainAccess + managed resources |
| GuidanceController | PUT | `/guidance/v1/guidance/status/{id}` | GuidanceService + CanonicalDomainAccess + managed resources |
| GuidanceController | PUT | `/guidance/v1/guidance/resources/{id}` | GuidanceService + CanonicalDomainAccess + managed resources |
| GuidanceController | DELETE | `/guidance/v1/guidance/delete/{id}` | GuidanceService + CanonicalDomainAccess + managed resources |
| TrackedBehaviorController | POST | `/tracked-behaviors/v1/save` | TrackedBehaviorServiceImpl + CanonicalDomainAccess |
| TrackedBehaviorController | GET | `/tracked-behaviors/v1/student/{studentEmail}/timeline` | TrackedBehaviorServiceImpl + CanonicalDomainAccess |
| TrackedBehaviorController | GET | `/tracked-behaviors/v1/student/{studentEmail}/totals` | TrackedBehaviorServiceImpl + CanonicalDomainAccess |
| TrackedBehaviorController | GET | `/tracked-behaviors/v1/types` | TrackedBehaviorServiceImpl + CanonicalDomainAccess |
| TrackedBehaviorController | POST | `/tracked-behaviors/v1/students/totals` | TrackedBehaviorServiceImpl + CanonicalDomainAccess |
| AssignmentController | GET | `/assignments/v1/` | AssignmentService/Resolver + WorkflowAccess; global legacy writes blocked |
| AssignmentController | GET | `/assignments/v1/templates/for-punishment/{punishmentId}` | AssignmentService/Resolver + WorkflowAccess; global legacy writes blocked |
| AssignmentController | GET | `/assignments/v1/templates/search` | AssignmentService/Resolver + WorkflowAccess; global legacy writes blocked |
| AssignmentController | GET | `/assignments/v1/templates` | AssignmentService/Resolver + WorkflowAccess; global legacy writes blocked |
| AssignmentController | GET | `/assignments/v1/templates/by-infraction` | AssignmentService/Resolver + WorkflowAccess; global legacy writes blocked |
| AssignmentController | GET | `/assignments/v1/templates/{id}` | AssignmentService/Resolver + WorkflowAccess; global legacy writes blocked |
| AssignmentController | GET | `/assignments/v1/{id}/assignment-template` | AssignmentService/Resolver + WorkflowAccess; global legacy writes blocked |
| AssignmentController | GET | `/assignments/v1/by-punishment/{punishmentId}` | AssignmentService/Resolver + WorkflowAccess; global legacy writes blocked |
| AssignmentController | POST | `/assignments/v1/` | AssignmentService/Resolver + WorkflowAccess; global legacy writes blocked |
| AssignmentController | POST | `/assignments/v1/migrate-legacy` | AssignmentService/Resolver + WorkflowAccess; global legacy writes blocked |
| AssignmentController | POST | `/assignments/v1/templates` | AssignmentService/Resolver + WorkflowAccess; global legacy writes blocked |
| AssignmentController | PUT | `/assignments/v1/{id}` | AssignmentService/Resolver + WorkflowAccess; global legacy writes blocked |
| AssignmentController | PUT | `/assignments/v1/templates/{id}` | AssignmentService/Resolver + WorkflowAccess; global legacy writes blocked |
| AssignmentController | DELETE | `/assignments/v1/delete/{assignmentName}` | AssignmentService/Resolver + WorkflowAccess; global legacy writes blocked |
| AssignmentController | DELETE | `/assignments/v1/templates/{id}` | AssignmentService/Resolver + WorkflowAccess; global legacy writes blocked |
| AssignmentBindingController | POST | `/assignments/v1/bindings/teacher-default` | AssignmentService binding CAS and tenant predicates |
| AssignmentBindingController | DELETE | `/assignments/v1/bindings/teacher-default` | AssignmentService binding CAS and tenant predicates |
| AssignmentBindingController | GET | `/assignments/v1/bindings/teacher-default` | AssignmentService binding CAS and tenant predicates |
| AssignmentBindingController | GET | `/assignments/v1/bindings/by-template/{templateId}` | AssignmentService binding CAS and tenant predicates |
| AssignmentLifecycleController | GET | `/assignments/v1/cases/{id}` | AssignmentLifecycleService work + revision CAS |
| AssignmentLifecycleController | PUT | `/assignments/v1/cases/{id}/{operation}` | AssignmentLifecycleService work + revision CAS |
| DTOController | GET | `/DTO/v1/AdminOverviewData` | shared WorkflowAccess + scoped Student/Punishment/Office/Employee domain reads |
| DTOController | GET | `/DTO/v1/TeacherOverviewData` | shared WorkflowAccess + scoped Student/Punishment/Office/Employee domain reads |
| DTOController | GET | `/DTO/v1/punishmentsDTO` | shared WorkflowAccess + scoped Student/Punishment/Office/Employee domain reads |
| DTOController | GET | `/DTO/v1/StudentOverviewData` | shared WorkflowAccess + scoped Student/Punishment/Office/Employee domain reads |
| DTOController | GET | `/DTO/v1/StudentOverviewData/{studentEmail}` | shared WorkflowAccess + scoped Student/Punishment/Office/Employee domain reads |
| EmailController | GET | `/email/v1/notifications` | RequiredNotificationService + CanonicalEmailOwnership |
| EmailController | GET | `/email/v1/notifications/{kind}/{caseId}` | RequiredNotificationService + CanonicalEmailOwnership |
| EmailController | POST | `/email/v1/notifications/{kind}/{caseId}/{notificationId}/retry` | RequiredNotificationService + CanonicalEmailOwnership |
| EmailController | POST | `/email/v1/notifications/{kind}/{caseId}/{notificationId}/resolve` | RequiredNotificationService + CanonicalEmailOwnership |
| EmailController | POST | `/email/v1/classAnnouncement` | RequiredNotificationService + CanonicalEmailOwnership |
| security/AccountAccessController | POST | `/account-access/complete` | AccountAccessService provisioned-target validation and owned fixed updates |
| security/AccountAccessController | POST | `/accounts/v1/{id}/invitation` | AccountAccessService provisioned-target validation and owned fixed updates |
| security/AccountAccessController | GET | `/accounts/v1/students/{email}/access` | AccountAccessService provisioned-target validation and owned fixed updates |
| security/AccountAccessController | POST | `/accounts/v1/students/{email}/invitation` | AccountAccessService provisioned-target validation and owned fixed updates |
| security/AccountAccessController | POST | `/accounts/v1/students/{email}/setup` | AccountAccessService provisioned-target validation and owned fixed updates |
| security/UserControllers | GET | `/users/v1/users` | UserAdministrationService / account-protected updates |
| security/UserControllers | GET | `/users/v1/users/{role}` | UserAdministrationService / account-protected updates |
| security/UserControllers | POST | `/users/v1/users/create/{school}` | UserAdministrationService / account-protected updates |
| security/UserControllers | PUT | `/users/v1/users/{school}` | UserAdministrationService / account-protected updates |
| security/UserControllers | PUT | `/users/v1/users/{id}/roles` | UserAdministrationService / account-protected updates |
| security/UserControllers | PUT | `/users/v1/users/{id}/enabled` | UserAdministrationService / account-protected updates |
| security/UserControllers | DELETE | `/users/v1/users/{id}` | UserAdministrationService / account-protected updates |
| security/AuthControllers | GET | `/auth/gmail/status` | CustomUserDetailsService / OAuth2LoginSuccessHandler / setup validation; existing JWT revocation |
| security/AuthControllers | POST | `/auth/gmail/connect` | CustomUserDetailsService / OAuth2LoginSuccessHandler / setup validation; existing JWT revocation |
| security/AuthControllers | POST | `/auth/gmail/complete` | CustomUserDetailsService / OAuth2LoginSuccessHandler / setup validation; existing JWT revocation |
| security/AuthControllers | POST | `/auth/gmail/disconnect` | CustomUserDetailsService / OAuth2LoginSuccessHandler / setup validation; existing JWT revocation |
| security/AuthControllers | POST | `/v1/logout` | CustomUserDetailsService / OAuth2LoginSuccessHandler / setup validation; existing JWT revocation |
| security/AuthControllers | GET | `/test` | CustomUserDetailsService / OAuth2LoginSuccessHandler / setup validation; existing JWT revocation |
| security/AuthControllers | POST | `/register` | CustomUserDetailsService / OAuth2LoginSuccessHandler / setup validation; existing JWT revocation |
| security/AuthControllers | POST | `/auth` | CustomUserDetailsService / OAuth2LoginSuccessHandler / setup validation; existing JWT revocation |
| security/AuthControllers | GET | `/auth/capabilities` | CustomUserDetailsService / OAuth2LoginSuccessHandler / setup validation; existing JWT revocation |
| security/AuthControllers | POST | `/users/create/{school}` | CustomUserDetailsService / OAuth2LoginSuccessHandler / setup validation; existing JWT revocation |
| security/AuthControllers | GET | `/v1/token-status` | CustomUserDetailsService / OAuth2LoginSuccessHandler / setup validation; existing JWT revocation |
| security/AuthControllers | POST | `/v1/renew-token` | CustomUserDetailsService / OAuth2LoginSuccessHandler / setup validation; existing JWT revocation |
| security/AuthControllers | POST | `/forgot-password` | CustomUserDetailsService / OAuth2LoginSuccessHandler / setup validation; existing JWT revocation |
| security/AuthControllers | POST | `/reset-password` | CustomUserDetailsService / OAuth2LoginSuccessHandler / setup validation; existing JWT revocation |
| security/AuthControllers | POST | `/contact-us` | CustomUserDetailsService / OAuth2LoginSuccessHandler / setup validation; existing JWT revocation |

Baseline route/alias count: 144.

Additional Phase 4C routes: authenticated GET `/history/v1/cases` and GET `/history/v1/cases/{kind}/{id}`; HistoricalCaseAccess returns allowlisted immutable snapshots. Disabled preparation returns unavailable.

| Worker/callback | Ownership / conditional effect |
| --- | --- |
| Notification scanner/claim | Parent + exact embedded actor/tenant/profile metadata, current recorded membership/relationship, owner-qualified claim |
| Notification accepted/failed update | Same attempt/state/ownership CAS; no provider fallback; no replay of accepted or uncertain outcome |
| Restart recovery | Owned DISPATCHING -> UNCERTAIN only; no inferred provider failure |
| Manual reminders | Same-school ADMIN; canonical cases; system SMTP intent; fresh copy relationships |
| OAuth refresh/disconnect/use | Owner-qualified mailbox metadata, token version CAS; no ciphertext migration |
| Currency commit/replay | Actual domain wallets plus v2 bridge; reviewed v1 manifest required; no historical changes |
| Account setup/Google callback | Persisted canonical membership including pending setup target; no profile repair from names |
| Stripe signed webhook | Existing verified signature/replay protections and recorded billing association; tenant-qualified confirmation; no provisioning |
| Unsupported global maintenance/state reporting | Blocked, no provider calls or guessed ownership |
