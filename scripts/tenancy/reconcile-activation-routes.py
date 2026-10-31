"""Reconcile source mappings with full Boot runtime probes. Offline; never connects to data/services."""
from pathlib import Path
import re,json,csv,collections,argparse
args=argparse.ArgumentParser();args.add_argument('--evidence',default='target/canonical-runtime-route-evidence.json');args.add_argument('--output',default='docs/tenancy/activation-route-coverage.csv');opt=args.parse_args()
root=Path('.');out=Path(opt.output)
def strip_comments(s):
 return re.sub(r'("(?:\\.|[^"\\])*"|\'(?:\\.|[^\'\\])*\')|(/\*[\s\S]*?\*/|//[^\r\n]*)',lambda m:m[1] if m[1] else re.sub(r'[^\n]',' ',m[0]),s)
def balanced(s,start,opening='(',closing=')'):
 depth=0;quote=None;esc=False
 for i in range(start,len(s)):
  c=s[i]
  if quote:
   if esc:esc=False
   elif c=='\\':esc=True
   elif c==quote:quote=None
  elif c in ['"',"'"]:quote=c
  elif c==opening:depth+=1
  elif c==closing:
   depth-=1
   if depth==0:return i
 raise ValueError('Unbalanced Java')
rows=[]
for p in sorted((root/'src/main/java').rglob('*Controller*.java')):
 raw=p.read_text(encoding='utf-8');s=strip_comments(raw);cl=re.search(r'public\s+(?:final\s+)?class\s+(\w+)',s)
 if not cl:continue
 controller=cl[1];head=s[:cl.start()];base=re.search(r'@RequestMapping\s*\(\s*(?:value\s*=\s*)?"([^"]*)"',head);prefix=base[1] if base else ''
 for m in re.finditer(r'@(Get|Post|Put|Patch|Delete|Request)Mapping\b',s[cl.end():]):
  pos=cl.end()+m.start();at=cl.end()+m.end()
  while at<len(s) and s[at].isspace():at+=1
  if s[at]=='(':end=balanced(s,at);args=s[at+1:end]
  else:end=at;args=''
  routes=re.findall(r'"([^"\n]*)"',args) or [''];methods=re.findall(r'RequestMethod\.([A-Z]+)',args) if m[1]=='Request' else [m[1].upper()];methods=methods or ['ANY']
  method=re.search(r'public\s+[^;{=]+?\s+(\w+)\s*\(',s[end:]);assert method,(p,pos)
  handler=method[1];body_start=s.index('{',end+method.end());body_end=balanced(s,body_start,'{','}');body=s[body_start:body_end+1]
  calls=sorted(set(re.findall(r'\b([a-z]\w*\.\w+)\s*\(',body)))
  for route in routes:
   full=re.sub('/+','/',('/'+prefix.strip('/')+('/'+route.lstrip('/') if route else ''))) or '/'
   for http in methods:rows.append(dict(controller=controller,method=http,path=full,handler=handler,source=str(p.relative_to(root)).replace('\\','/'),line=raw[:pos].count('\n')+1,calls='; '.join(calls),body=body))

evidence=json.loads(Path(opt.evidence).read_text())
runtime={(r['method'],r['path']):r for r in evidence};source={(r['method'],r['path']) for r in rows}
if source!=set(runtime):raise SystemExit('Runtime/source mapping mismatch; inspect added/removed routes before accepting evidence.')
selected={('POST','/punish/v1/startPunish/formList'),('POST','/officeReferral/v1/startPunish/adminReferral'),('POST','/officeReferral/v1/closeId'),('GET','/assignments/v1/cases/{id}'),('PUT','/assignments/v1/cases/{id}/{operation}'),('POST','/guidance/v1/guidance/new'),('GET','/history/v1/cases/{kind}/{id}'),('POST','/accounts/v1/students/{email}/setup'),('POST','/account-access/complete'),('POST','/email/v1/classAnnouncement'),('PUT','/employees/v1/updateClass/{teacherEmail}'),('POST','/tracked-behaviors/v1/save'),('GET','/tracked-behaviors/v1/student/{studentEmail}/timeline'),('POST','/auth'),('POST','/auth/gmail/connect'),('POST','/auth/gmail/complete'),('GET','/auth/gmail/status')}
for r in rows:
 key=(r['method'],r['path']);r['category']='Integrated; acceptance incomplete';r['evidence']='Full Boot mapping/binding probe only; see dedicated acceptance tests before activation';r['gap']='Successful binding is not proof of complete role/state/side-effect coverage'
 if key in selected:r['category']='Integrated; selected workflow verified';r['evidence']='FullApplicationCanonicalMongoTest real security chain/services/Mongo; SMTP/translation mocked, Gmail HTTP intercepted';r['gap']='Selected operations; exhaustive all-role/state acceptance remains required'
 global_case=r['controller'] in ['InfractionController','GmailPoCController'] or r['controller']=='SchoolController' and r['handler'] in ['getAllSchools','searchSchools','createSchool','unsupportedMaintenance'] or r['controller']=='StripeController' and r['handler']=='createCheckoutSession'
 retired=any(x in r['body'] for x in ['denyMaintenance()','denyLegacyWrite()','Legacy registration is disabled']) or r['path'] in ['/student/v1/points/add','/student/v1/points/delete','/student/v1/points/transfer','/student/v1/delete','/student/v1/assignSchool']
 public=r['controller']=='AuthControllers' and r['path'] in ['/forgot-password','/reset-password','/contact-us','/test']
 if global_case or retired or public:r['category']='Intentional global/public or retired';r['evidence']='SecurityConfig + explicit catalog/maintenance/checkout denial or credential-bootstrap/global-directory policy';r['gap']='Global/public status does not bypass token, signature or mutation restrictions'
 if r['handler']=='webhook':r['gap']='Unsigned rejection covered; signed existing-subscription full HTTP-to-persistence callback acceptance still required'
 if r['path']=='/assignments/v1/cases/{id}/{operation}':r['gap']='save/submit/review exercised; bind/recovery not full acceptance'
 r['probe_statuses']='; '.join(k+':'+str(v) for k,v in runtime[key]['bindingProbeStatuses'].items());r.pop('body',None)
fields=list(rows[0]);out.parent.mkdir(parents=True,exist_ok=True)
with out.open('w',newline='',encoding='utf-8') as f:w=csv.DictWriter(f,fieldnames=fields);w.writeheader();w.writerows(rows)
old=[]
for line in (root/'docs/tenancy/core-endpoints.md').read_text().splitlines():
 m=re.match(r'\| (GET|POST|PUT|PATCH|DELETE) \| `([^`]+)`',line)
 if m:old.append((m[1],m[2]))
summary={'sourceMappings':len(rows),'runtimeMappings':len(runtime),'controllers':len(set(r['controller'] for r in rows)),'originalInventoryMappings':len(old),'missingOriginal':sorted(set(old)-source),'addedSince144':sorted(source-set(old)),'categoryCounts':dict(collections.Counter(r['category'] for r in rows)),'warning':'Binding probes are discovery evidence, not exhaustive security acceptance. Global deployed activation stays prohibited.'}
out.with_suffix('.json').write_text(json.dumps(summary,indent=2)+'\n');print(json.dumps(summary,indent=2))
