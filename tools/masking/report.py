"""Export scalar evidence to Kotlin; summarize its actual abstentions and proposals, not Python replicas."""
import argparse,csv,json,subprocess
from pathlib import Path
import numpy as np
p=argparse.ArgumentParser();p.add_argument('--work',type=Path,required=True);p.add_argument('--classes',type=Path);p.add_argument('--compiled-jar',type=Path);p.add_argument('--baseline-head',type=Path);a=p.parse_args()
root=Path(__file__).resolve().parents[2]
data=np.load(a.work/'features.npz');records=json.loads(str(data['records']))
features=data['features'];events=data['events']
labels={'Speech','Conversation','Narration, monologue','Hubbub, speech noise, speech babble','Crowd','Chatter','Silence','Vehicle','Traffic noise, roadway noise','Engine','Tools','Mechanisms','Dishes, pots, and pans','Vacuum cleaner','Water','Wind','Mechanical fan','Music'}
# Kotlin insertion order is the frozen EnvironmentClassifier.requiredLabels order.
ordered=['Speech','Conversation','Narration, monologue','Hubbub, speech noise, speech babble','Crowd','Chatter','Silence','Vehicle','Traffic noise, roadway noise','Engine','Tools','Mechanisms','Dishes, pots, and pans','Vacuum cleaner','Water','Wind','Mechanical fan','Music']
allLabels=(root/'audioAndroid/src/main/assets/environment/labels.txt').read_text().splitlines()
with (a.work/'evidence.tsv').open('w') as f:
    for i,r in enumerate(records):
        vals=list(features[i])+[events[i,allLabels.index(k)] for k in ordered]+[r['rms'],r['peak']]
        f.write(str(i)+'\t'+','.join(map(str,vals))+'\n')
jar=a.compiled_jar or a.work/'report.jar'
sources=['-cp',str(a.classes)] if a.classes else [str(p) for p in (root/'shared/src/commonMain/kotlin/com/heysig/noise').glob('*.kt')]
if not a.compiled_jar: subprocess.run(['kotlinc',*sources,str(root/'tools/masking/MaskingReport.kt'),'-include-runtime','-d',str(jar)],check=True)
subprocess.run(['java','-cp',str(jar)+((':'+str(a.classes)) if a.classes else ''),'com.heysig.masking.MaskingReportKt',str(a.work/'masking-head.txt'),str(a.work/'evidence.tsv'),str(a.work/'decisions.tsv')]+([str(a.baseline_head)] if a.baseline_head else []),check=True)
decisions=list(csv.DictReader((a.work/'decisions.tsv').open(),delimiter='\t'))
names=['CLEAR','MODERATE','HEAVY']
summary={}
for part in ('development','heldout'):
    rows=[(r,d) for r,d in zip(records,decisions) if r['partition']==part]
    mixes=[(r,d) for r,d in rows if r['kind']=='mixture'];controls=[(r,d) for r,d in rows if r['kind']!='mixture']
    confusion={k:{p:0 for p in names+['QUIET','UNKNOWN']} for k in names}
    for r,d in mixes: confusion[names[r['reference']]][d['severity']]+=1
    pairs={}
    for r,d in mixes: pairs.setdefault(r['pair'],{})[r['snr']]=d['severity']
    matched=sum(v.get(15)=='CLEAR' and v.get(-8)=='HEAVY' for v in pairs.values())
    summary[part]=dict(mixtures=len(mixes),confusion=confusion,uncertain=sum(d['severity']=='UNKNOWN' for r,d in mixes),
        noisyVsVeryNoisyConfusions=sum((r['reference']==1 and d['severity']=='HEAVY') or (r['reference']==2 and d['severity']=='MODERATE') for r,d in mixes),
        maskingCorrect=sum(d['severity']==names[r['reference']] for r,d in mixes),
        levelOnlyCorrect=sum((d['levelProposal'] if d['levelProposal']!='hold' else '1')==str(r['reference']+1) for r,d in mixes),
        matchedLoudnessPairs=len(pairs),clearVersusBuriedPairsBothCorrect=matched,
        controls=[dict(kind=r['kind'],background=r.get('background'),severity=d['severity'],proposalFromEveryday=d['maskingProposal'],proposalFromNoisy=d['returnProposal'],falseSwitchFromEveryday=d['maskingProposal']!='hold' if r['reference']==-1 else False,falseSwitchFromNoisy=d['returnProposal']!='hold' if r['reference']==-1 else False) for r,d in controls])
summary['mode']='cancellation corroboration' if a.baseline_head else 'single head'
summary['limitations']=['Correlated mix variants, not independent users/trials.','Heldout sources never fit coefficients, but not guaranteed absent from YAMNet pretraining.','Digital filters/echoes are position proxies, not physical phone placements.','TV and music controls are surrogates, not representative TV/music recordings.','SNR references describe constructed mixtures, not measured wearer intelligibility.','No detector or physical acceptance claimed.']
(a.work/'summary.json').write_text(json.dumps(summary,indent=2)+'\n')
print(json.dumps({k:{n:v for n,v in s.items() if n!='controls'} for k,s in summary.items() if k not in ('limitations','mode')},indent=2))
