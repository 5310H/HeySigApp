"""Public word-timing metadata only: never read word text or infer listening scenes."""
import math
import xml.etree.ElementTree as E

def word_intervals(data,source_start=120.,source_end=150.):
    if not 0<=source_start<source_end<=7200:raise ValueError("Source interval bound")
    if not 1<=len(data)<=2_000_000:raise ValueError('Annotation size bound')
    intervals=[]
    for node in E.fromstring(data):
        if node.tag!='w' or node.get('punc')=='true':continue
        if 'starttime' not in node.attrib or 'endtime' not in node.attrib:continue
        start=float(node.get('starttime'));end=float(node.get('endtime'))
        if not math.isfinite(start) or not math.isfinite(end) or start<0 or end<start:raise ValueError('Invalid word timing')
        start=max(source_start,start);end=min(source_end,end)
        if end>start:intervals.append([start-source_start,end-source_start])
    return sorted(intervals)

def merge(intervals,start,end,limit=30):
    out=[]
    for left,right in sorted(intervals):
        if not all(math.isfinite(v) for v in (left,right)) or not 0<=left<right<=limit:raise ValueError('Invalid derived timing')
        left=max(start,left);right=min(end,right)
        if right<=left:continue
        if out and left<=out[-1][1]:out[-1][1]=max(out[-1][1],right)
        else:out.append([left,right])
    return out

def summarize(speakers,offset,seconds):
    if offset not in (0,10,20) or seconds not in (3,5,10) or offset+seconds>30:raise ValueError('Unreviewed window')
    if set(speakers)!=set('ABCD'):raise ValueError('Expected four annotated participants')
    return span_activity(speakers,offset,seconds,30)

def span_activity(speakers,offset,seconds,limit=7200):
    if set(speakers)!=set('ABCD') or not 0<=offset<offset+seconds<=limit<=7200:raise ValueError('Activity span bound')
    end=offset+seconds;events={offset:0,end:0};durations={}
    for speaker,intervals in speakers.items():
        merged=merge(intervals,offset,end,limit);durations[speaker]=sum(r-l for l,r in merged)
        for left,right in merged:
            events[left]=events.get(left,0)+1;events[right]=events.get(right,0)-1
    histogram=[0.]*5;active=0;last=offset
    for time,delta in sorted(events.items()):
        histogram[active]+=time-last;active+=delta;last=time
        if not 0<=active<=4:raise ValueError('Invalid timing sweep')
    if active!=0 or abs(sum(histogram)-seconds)>1e-8:raise ValueError('Unbalanced timing sweep')
    return dict(speakersWithWords=sum(v>0 for v in durations.values()),speakersWithAtLeastHalfSecond=sum(v>=.5 for v in durations.values()),
        perSpeakerWordSeconds=durations,wordActivityHistogramSeconds=histogram,wordOverlapFraction=sum(histogram[2:])/seconds,
        notes='Word timings only, excluding punctuation and vocal-sound tags; not total people present, acoustic VAD, scene ground truth or transcript content.')
