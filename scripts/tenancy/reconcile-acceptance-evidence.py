"""Offline route acceptance reconciliation. Requires green real MongoDB cohort reports."""
import csv,json,re,collections,xml.etree.ElementTree as ET
from pathlib import Path
from urllib.parse import urlsplit,unquote
root=Path('.')
rows=list(csv.DictReader((root/'docs/tenancy/activation-route-coverage.csv').open(encoding='utf-8')))
suites={'Students':'CanonicalStudentAdminAcceptanceMongoTest','Cases':'CanonicalCaseAssignmentAcceptanceMongoTest','Communication':'CanonicalCommunicationAcceptanceMongoTest','Accounts':'CanonicalAccountCurrencyAcceptanceMongoTest','Google':'CanonicalGoogleAccessAcceptanceMongoTest','Full':'FullApplicationCanonicalMongoTest'}
evidence=[];counts={}
for suite,cls in suites.items():
 directory=root/'target/acceptance-runs'/suite
 report=directory/('TEST-com.reps.demogcloud.services.tenancy.'+cls+'.xml')
 if not report.exists():raise SystemExit('Missing cohort report: '+str(report))
 data=ET.parse(report).getroot()
 if any(int(data.get(k,0)) for k in ['failures','errors','skipped']):raise SystemExit('Cohort not fully green: '+suite)
 counts[suite]=int(data.get('tests'))
 tests={re.sub(r'\(.*','',t.get('name')) for t in data.findall('testcase')}
 records=directory/'evidence.jsonl'
 if records.exists():
  for line in records.read_text(encoding='utf-8').splitlines():
   item=json.loads(line)
   if item['test'].split('#')[0]!=cls or item['test'].split('#')[1] not in tests:raise SystemExit('Evidence test missing from green report')
   evidence.append(item)
# Reviewed exact test-to-route bindings for retained full-composition scenarios.
full_routes={
 '/email/v1/classAnnouncement':'realAnnouncementSenderNeverReceivesCrossTenantOrUnownedBatch',
 '/employees/v1/updateClass/{teacherEmail}':'actualRosterAndTrackedBehaviorRoutesValidateWholeBatchAndCurrentRelationships',
 '/history/v1/cases/{kind}/{id}':'actualAssignmentSavesRejectStaleWritesAndNeverGrantFormerAuthorReview',
 '/officeReferral/v1/startPunish/adminReferral':'officeCreationReplayAndAdminClosureUseRealOwnedCaseAndNoAssignment',
 '/officeReferral/v1/closeId':'officeCreationReplayAndAdminClosureUseRealOwnedCaseAndNoAssignment',
 '/punish/v1/startPunish/formList':'ordinaryCreationAssignmentAndTenantDenialsAreRealDomainOperations',
 '/tracked-behaviors/v1/save':'actualRosterAndTrackedBehaviorRoutesValidateWholeBatchAndCurrentRelationships',
 '/tracked-behaviors/v1/student/{studentEmail}/timeline':'actualRosterAndTrackedBehaviorRoutesValidateWholeBatchAndCurrentRelationships',
 '/account-access/complete':'pendingCanonicalStudentCanUseSchoolAssistedSetupWithoutTenantChanges',
 '/accounts/v1/students/{email}/setup':'pendingCanonicalStudentCanUseSchoolAssistedSetupWithoutTenantChanges',
 '/auth/gmail/status':'passwordLoginAndActualGmailConnectPersistEncryptedOwnershipAndPreventGrantBorrowing',
 '/auth/gmail/connect':'passwordLoginAndActualGmailConnectPersistEncryptedOwnershipAndPreventGrantBorrowing',
 '/auth/gmail/complete':'passwordLoginAndActualGmailConnectPersistEncryptedOwnershipAndPreventGrantBorrowing',
 '/auth':'passwordLoginAndActualGmailConnectPersistEncryptedOwnershipAndPreventGrantBorrowing',
 '/assignments/v1/cases/{id}':'ordinaryCreationAssignmentAndTenantDenialsAreRealDomainOperations',
 '/assignments/v1/cases/{id}/{operation}':'actualStudentSubmissionRejectionAndResubmissionPreserveHistoryAndSafeReviewReplay'
}
for route,test in full_routes.items():
 if test not in tests:raise SystemExit('Retained Full test missing: '+test)
for row in rows:
 pattern='^'+re.sub(r'\{[^}]+\}',r'[^/]+',row['path'])+'$'
 if '*' in row['path']:pattern=r'(?!)'
 matched=[e for e in evidence if e['method']==row['method'] and re.fullmatch(pattern,unquote(urlsplit(e['path']).path))]
 # Prefer exact source routes over parameter routes which can also match static aliases.
 matched=[e for e in matched if not any(r['method']==e['method'] and r['path']==unquote(urlsplit(e['path']).path) and r['path']!=row['path'] for r in rows)]
 if matched:
  baseline_selected='selected workflow' in row['category']
  row['category']='Accepted actual route/domain/Mongo'
  if all('unsupportedLegacyAssignmentAliasesNeverWrite' in e['test'] for e in matched):row['category']='Accepted intentional unsupported operation'
  if row['path'] in ['/student/v1/','/assignments/v1/','/tracked-behaviors/v1/types']:row['category']='Accepted global metadata/no tenant data'
  row['evidence']='; '.join(sorted({e['test'] for e in matched})) + ('; FullApplicationCanonicalMongoTest original lifecycle acceptance retained' if baseline_selected else '')
  row['policy']='; '.join(sorted({e['policy'] for e in matched}))
  if row['path']=='/student/v1/':row['policy']='Authenticated empty legacy root; exposes no student or disciplinary records'
  row['authorized_scenario']='; '.join(sorted({e['authorized'] for e in matched}))
  row['denied_scenario']='; '.join(sorted({e['denied'] for e in matched}))
  row['gap']='; '.join(sorted({e['limitation'] for e in matched}))
 else:
  row['policy']='Existing source policy; see baseline matrix and report'
  row['authorized_scenario']='Not newly exercised' if 'incomplete' in row['category'] else 'See FullApplicationCanonicalMongoTest or intentional exclusion policy'
  row['denied_scenario']='See baseline evidence; binding probes alone are not acceptance'
for row in rows:
 if row['path'] in full_routes:
  reference='FullApplicationCanonicalMongoTest#'+full_routes[row['path']]
  if reference not in row['evidence']:row['evidence']+='; '+reference
  if 'selected workflow' in row['category']:
   row['category']='Accepted retained full workflow/Mongo'
   row['policy']='Exact persisted tenant plus independent current role/self/roster/caseload or immutable snapshot provenance'
   row['authorized_scenario']='Real owned operation and persisted result; see exact named test'
   row['denied_scenario']='Cross-tenant, unowned or revoked relationship rejected; see exact named test and scoped limitations'
   row['gap']='Synthetic real chain/services/Mongo; providers mocked. This is grouped representative acceptance, not every possible role/state permutation.'
for row in rows:
 if row['path']=='/account-access/complete':
  row['policy']='Public single-use account-bound setup credential; persisted membership must remain unchanged'
  row['authorized_scenario']='School-assisted student password setup succeeds without changing tenant or roles'
  row['denied_scenario']='Credential replay rejected with HTTP 400; issuance denied to cross-school teacher in same test'
  row['gap']='Completion has no staff-selected tenant target; token expiry/attempt/disabled rules also covered by existing account regressions'
 if row['path']=='/auth':
  row['policy']='Password proof authenticates a persisted account; caller cannot select tenant membership'
  row['authorized_scenario']='Existing canonical teacher password login succeeds through actual filter/controller/provider path'
  row['denied_scenario']='Credential/disabled-account failure coverage in existing security regressions; this Full scenario is the successful canonical login path'
  row['gap']='No cross-tenant target parameter; Google callback and account/profile conflicts have separate actual filter acceptance'
 if row['path'] in ['/auth/gmail/connect','/auth/gmail/complete','/auth/gmail/status']:
  row['policy']='Authenticated staff account + bound OAuth state/proof + exact persisted grant ownership'
  row['denied_scenario']='Other account sees only its own grant state; altered grant tenant produces 503 rather than borrowed authorization'
  row['gap']='Provider HTTP intercepted; do not interpret self-service access by school B to its own grant as an unauthorized request'
fields=list(rows[0]);out=root/'docs/tenancy/canonical-acceptance-route-coverage.csv'
with out.open('w',newline='',encoding='utf-8') as f:
 writer=csv.DictWriter(f,fieldnames=fields);writer.writeheader();writer.writerows(rows)
workers=[e for e in evidence if e['method']=='WORKER']
(root/'docs/tenancy/canonical-acceptance-worker-evidence.json').write_text(json.dumps(workers,indent=2)+'\n',encoding='utf-8')
summary={'routes':len(rows),'categories':dict(collections.Counter(r['category'] for r in rows)),'mongoCohorts':counts,'mongoTotal':sum(counts.values()),'unverified':[r['method']+' '+r['path'] for r in rows if 'incomplete' in r['category']],'warning':'Local synthetic evidence only; deployed activation prohibited.'}
(root/'docs/tenancy/canonical-acceptance-summary.json').write_text(json.dumps(summary,indent=2)+'\n',encoding='utf-8');print(json.dumps(summary,indent=2))
