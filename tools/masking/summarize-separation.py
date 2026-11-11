"""Summarize actual Kotlin decisions, known-source diagnostics and correlated-pair tests."""
import argparse,csv,json
from pathlib import Path
import numpy as np

def main():
    p=argparse.ArgumentParser();p.add_argument('--work',type=Path,required=True);p.add_argument('--baseline',type=Path,required=True);a=p.parse_args()
    records=json.loads(str(np.load(a.baseline)['records']))
    measured=list(csv.DictReader((a.work/'measurements.tsv').open(),delimiter='\t'))
    decisions=list(csv.DictReader((a.work/'decisions.tsv').open(),delimiter='\t'))
    assert len(records)==len(measured)==len(decisions)
    names=['CLEAR','MODERATE','HEAVY'];summary={}
    for part in ('development','heldout'):
        rows=[(r,m,d) for r,m,d in zip(records,measured,decisions) if r['partition']==part]
        mixes=[(r,m,d) for r,m,d in rows if r['kind']=='mixture'];controls=[(r,m,d) for r,m,d in rows if r['kind']!='mixture']
        confusion={k:{v:0 for v in names+['UNKNOWN']} for k in names};pairs={};ratio_pairs={}
        for r,m,d in mixes:
            confusion[names[r['reference']]][d['severity']]+=1
            pairs.setdefault(r['pair'],{})[r['snr']]=d['severity']
            ratio_pairs.setdefault(r['pair'],{})[r['snr']]=float(m['ratioDb']) if m['ratioDb'] else None
        errors=[abs(float(m['ratioDb'])-float(m['oracleWeightedSnrDb'])) for r,m,d in mixes if m['ratioDb']]
        correct=sum(d['severity']==names[r['reference']] for r,m,d in mixes);held=sum(d['severity']=='UNKNOWN' for r,m,d in mixes)
        by_background={}
        for bg in sorted({r['background'] for r,m,d in mixes}):
            group=[(r,m,d) for r,m,d in mixes if r['background']==bg]
            by_background[bg]=dict(count=len(group),correct=sum(d['severity']==names[r['reference']] for r,m,d in group),held=sum(d['severity']=='UNKNOWN' for r,m,d in group))
        summary[part]=dict(mixtures=len(mixes),confusion=confusion,correct=correct,wrong=len(mixes)-correct-held,uncertain=held,
            correctAmongNonUncertain=correct/(len(mixes)-held) if held<len(mixes) else None,
            noisyVsVeryNoisyConfusions=sum((r['reference']==1 and d['severity']=='HEAVY') or (r['reference']==2 and d['severity']=='MODERATE') for r,m,d in mixes),
            matchedLoudnessPairs=len(pairs),clearVersusBuriedPairsBothCorrect=sum(v.get(15)=='CLEAR' and v.get(-8)=='HEAVY' for v in pairs.values()),
            matchedPairsRatioOrderingCorrect=sum(v.get(15) is not None and v.get(-8) is not None and v[15]>v[-8] for v in ratio_pairs.values()),
            ratioVsKnownSource=dict(count=len(errors),maeDb=float(np.mean(errors)),medianAbsoluteErrorDb=float(np.median(errors)),p90AbsoluteErrorDb=float(np.quantile(errors,.9))),
            removedSignalSpeechLike=sum(m['status']=='SPEECH_IN_RESIDUAL' for r,m,d in mixes),
            clearForegroundProjectionLossOver3Db=sum(r['reference']==0 and float(m['foregroundProjectionGainDb']) < -3 for r,m,d in mixes),
            byBackground=by_background,
            controls=[dict(kind=r['kind'],background=r.get('background'),status=m['status'],severity=d['severity'],proposalFromEveryday=d['maskingProposal'],proposalFromNoisy=d['returnProposal'],falseSwitchFromEveryday=r['reference']==-1 and d['maskingProposal']!='hold',falseSwitchFromNoisy=r['reference']==-1 and d['returnProposal']!='hold') for r,m,d in controls])
    summary['hostCpuSecondsMedian']=float(np.median([float(m['cpuSeconds']) for m in measured]))
    summary['limitations']=['Viewed correlated regression corpus, not fresh blind field acceptance.',
        'Developer speakers/backgrounds are disjoint across partitions, not guaranteed unseen in pretrained denoisers.',
        'Ratio is retained speech/residual, not target-speaker acoustic SNR; residual can contain wanted speech.',
        'SNR references describe constructed mixtures, not wearer intelligibility.',
        'TV/music/quiet controls include surrogates; positions are digital filter/echo proxies.',
        'Known-source projections and SI-SDR are offline diagnostics, unavailable during first use.',
        'Host CPU time is not Pixel battery impact.']
    (a.work/'summary.json').write_text(json.dumps(summary,indent=2)+'\n')
    print(json.dumps({k:{n:v for n,v in s.items() if n not in ('controls','byBackground')} for k,s in summary.items() if k in ('development','heldout')},indent=2))

if __name__=='__main__':main()
