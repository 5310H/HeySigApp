"""Select fixed bands using development mixtures only. No heldout fitting or user calibration."""
import argparse,csv,json
from pathlib import Path
import numpy as np

def main():
    p=argparse.ArgumentParser();p.add_argument('--work',type=Path,required=True);p.add_argument('--baseline',type=Path,required=True)
    p.add_argument('--original-decisions',type=Path,required=True);a=p.parse_args()
    records=json.loads(str(np.load(a.baseline)['records']))
    measurements=list(csv.DictReader((a.work/'measurements.tsv').open(),delimiter='\t'))
    original=list(csv.DictReader(a.original_decisions.open(),delimiter='\t'))
    assert len(records)==len(measurements)==len(original)
    rows=[(r,m,d) for r,m,d in zip(records,measurements,original) if r['partition']=='development' and r['kind']=='mixture']
    names=['CLEAR','MODERATE','HEAVY'];results=[]
    # A fixed 2dB abstention margin is declared before evaluating heldout decisions.
    for heavy in range(-20,6):
        for clear in range(0,21):
            if clear-heavy<=4:continue
            correct=wrong=held=0
            for r,m,d in rows:
                ratio=float(m['ratioDb']) if m['ratioDb'] else None
                predicted='UNKNOWN'
                if ratio is not None and m['status'] in ('ESTIMATED','SPEECH_IN_RESIDUAL'):
                    predicted='HEAVY' if ratio<=heavy-2 else 'CLEAR' if ratio>=clear+2 else 'MODERATE' if heavy+2<=ratio<=clear-2 else 'UNKNOWN'
                if predicted!=d['severity']:predicted='UNKNOWN'
                if predicted=='UNKNOWN':held+=1
                elif predicted==names[r['reference']]:correct+=1
                else:wrong+=1
            results.append(dict(heavyMaximumDb=heavy,clearMinimumDb=clear,marginDb=2.,correct=correct,wrong=wrong,held=held,cost=2*wrong+held))
    best=min(results,key=lambda r:(r['cost'],r['wrong'],r['heavyMaximumDb'],r['clearMinimumDb']))
    text='HSGSEPARATION1\n'+','.join(str(best[k]) for k in ('heavyMaximumDb','clearMinimumDb','marginDb'))+'\n'
    (a.work/'separation-bands.txt').write_text(text)
    (a.work/'bands-fit.json').write_text(json.dumps(dict(selected=best,candidates=results,
        selection='development mixtures only; minimize 2*wrong + held; fixed 2dB uncertainty margin; tie-break wrong/heavy/clear ascending; no controls or heldout fitting; operational SNR labels, not clinical boundaries'),indent=2)+'\n')
    print(json.dumps(best,indent=2))

if __name__=='__main__':main()
