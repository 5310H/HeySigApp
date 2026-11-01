"""Model-blind candidate references from annotated word activity; not scene qualification."""
from ami_timing_audit import span_activity,merge

POLICY=dict(durationOrderSeconds=[10,5,3],windowsPerDuration=2,minimumSpeakerWordSeconds=.5,minimumSpeakers=3,minimumWordCoverageFraction=.4,maximumWordOverlapFraction=.2,minimumGapSeconds=5,selection="earliest eligible integer-second start, ten-second windows first; no model outputs consulted")

def select_windows(speakers,duration,reserved,policy):
    if policy!=POLICY:raise ValueError('Unreviewed curation policy')
    if type(duration)!=int or not 10<=duration<=7200:raise ValueError('Meeting duration bound')
    if set(speakers)!=set('ABCD'):raise ValueError('Four annotated speakers required')
    for intervals in speakers.values():merge(intervals,0,7200,7200)
    if not isinstance(reserved,list) or any(len(pair)!=2 or not 0<=pair[0]<pair[1]<=7200 for pair in reserved):raise ValueError('Reserved interval bounds')
    selected=[];used=list(reserved)
    for seconds in policy['durationOrderSeconds']:
        if seconds not in (3,5,10):raise ValueError('Unsupported sample duration')
        count=0
        for start in range(duration-seconds+1):
            end=start+seconds;gap=policy['minimumGapSeconds']
            if any(start<right+gap and end>left-gap for left,right in used):continue
            stats=span_activity(speakers,start,seconds)
            active=sum(v>=policy['minimumSpeakerWordSeconds'] for v in stats['perSpeakerWordSeconds'].values())
            coverage=1-stats['wordActivityHistogramSeconds'][0]/seconds
            if active<policy['minimumSpeakers'] or coverage<policy['minimumWordCoverageFraction'] or stats['wordOverlapFraction']>policy['maximumWordOverlapFraction']:continue
            selected.append(dict(startSeconds=start,durationSeconds=seconds,activity=stats));used.append([start,end]);count+=1
            if count==policy['windowsPerDuration']:break
    return sorted(selected,key=lambda w:(w['startSeconds'],w['durationSeconds']))


def validate_plan(plan):
    import re
    if plan.get('version')!=1 or plan.get('policy')!=POLICY:raise ValueError('Unknown curation protocol')
    if not re.fullmatch('[0-9a-f]{64}',plan.get('metadataSha256','')):raise ValueError('Missing participant metadata pin')
    if plan.get('metadataSource')!='https://huggingface.co/datasets/ggfox00000/dia-AMICorpus-all/resolve/a249db5/ami_public_manual_1.6.2/corpusResources/meetings.xml':raise ValueError('Unreviewed participant source')
    meetings=plan['meetings']
    if not 1<=len(meetings)<=16 or len({m['meeting'] for m in meetings})!=len(meetings):raise ValueError('Repeated/unbounded meeting plan')
    identities={}
    for m in meetings:
        if not re.fullmatch(r'[A-Z]{2}[0-9]{4}[a-z]',m['meeting']) or m['partition'] not in ('development','validation'):raise ValueError('Unreviewed meeting')
        if type(m['durationSeconds'])!=int or not 10<=m['durationSeconds']<=7200 or type(m['previouslyViewedParticipants'])!=bool:raise ValueError('Meeting bounds')
        if set(m['participantsByChannel'])!=set('ABCD') or len(set(m['participantsByChannel'].values()))!=4:raise ValueError('Four unique participant identities required')
        for person in m['participantsByChannel'].values():
            if not isinstance(person,str) or not person.strip() or identities.setdefault(person,m['partition'])!=m['partition']:raise ValueError('Participant crosses development/evaluation')
    return {m['meeting']:m for m in meetings}
