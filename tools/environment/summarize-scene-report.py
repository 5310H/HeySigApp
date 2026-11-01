"""Summarize actual Kotlin decisions; never infer scene decisions in Python."""
import argparse,csv,hashlib,json,math
from pathlib import Path
from collections import Counter
from scene_corpus import load_manifest,LABELS
p=argparse.ArgumentParser(description=__doc__)
p.add_argument('--manifest',type=Path,required=True)
p.add_argument('--report',type=Path,required=True)
p.add_argument('--output',type=Path,required=True)
p.add_argument('--all-validation-viewed',action='store_true',help='Treat every validation source as previously viewed for follow-up experiments')
a=p.parse_args();manifest=load_manifest(a.manifest)
clips={c['id']:c for c in manifest['recordings']};fresh=set() if a.all_validation_viewed else set(manifest.get('freshValidationIds',[]))
with a.report.open() as f:
 reader=csv.DictReader(f,delimiter='\t')
 if reader.fieldnames!=['clip','reference','partition','sourceGroup','seconds','gain','offsetSeconds','decision','score','outcome']:raise ValueError('Unexpected report header')
 rows=list(reader)
if not 1<=len(rows)<=4096:raise ValueError('Unexpected report size')
seen=set()
for r in rows:
 c=clips[r['clip']]
 if any(r[k]!=c[k] for k in ('reference','partition','sourceGroup')):raise ValueError('Report differs from frozen manifest')
 if int(r['seconds']) not in (3,5,10) or float(r['gain']) not in (.25,1.) or int(r['offsetSeconds']) not in (0,10,20):raise ValueError('Unreviewed window')
 key=(r['clip'],r['seconds'],r['gain'],r['offsetSeconds'])
 if key in seen:raise ValueError('Duplicate window')
 seen.add(key)
 score=float(r['score'])
 if not math.isfinite(score) or not 0<=score<=1:raise ValueError('Invalid confidence')
 if r['decision'] not in (*LABELS,'UNCERTAIN'):raise ValueError('Invalid decision')
 expected='ABSTAIN' if r['decision']=='UNCERTAIN' else ('CORRECT' if r['decision']==r['reference'] else 'WRONG')
 if r['outcome']!=expected:raise ValueError('Inconsistent decision outcome')
 if r['decision']!='UNCERTAIN' and score<.65:raise ValueError('Accepted below switching threshold')
 r['evaluation']='development' if r['partition']=='development' else ('freshValidation' if r['clip'] in fresh else 'previouslyViewedValidation')

def summarize(selected):
 counts=Counter(r['outcome'] for r in selected)
 return dict(variants=len(selected),recordings=len({r['clip'] for r in selected}),sourceGroups=len({r['sourceGroup'] for r in selected}),**{k:counts[k] for k in ('CORRECT','WRONG','ABSTAIN')})
result={'manifestSha256':hashlib.sha256(a.manifest.read_bytes()).hexdigest(),'decisionReportSha256':hashlib.sha256(a.report.read_bytes()).hexdigest(),
 'notes':'Actual shared Kotlin decisions at unchanged threshold. Correlated windows and provisional source references are not independent physical acceptance. Partition names refer to the original experiment; they do not establish that sources remain unseen in later experiments. Use --all-validation-viewed after examining evaluation results.', 'evaluations':{}}
for category in ('development','previouslyViewedValidation','freshValidation'):
 selected=[r for r in rows if r['evaluation']==category]
 result['evaluations'][category]=dict(total=summarize(selected),byScene={label:summarize([r for r in selected if r['reference']==label]) for label in LABELS},
 byRecording={identifier:summarize([r for r in selected if r['clip']==identifier]) for identifier in sorted({r['clip'] for r in selected})})
 print(category,result['evaluations'][category]['total'])
a.output.parent.mkdir(parents=True,exist_ok=True);a.output.write_text(json.dumps(result,indent=2)+'\n')
