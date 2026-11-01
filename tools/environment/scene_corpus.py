"""Validate the reviewed developer-only scene corpus, including split identity."""
import hashlib
import json
from pathlib import Path
import re
from urllib.parse import urlparse

LABELS=('QUIET','CONVERSATION','SMALL_GROUP','RESTAURANT','LARGE_GROUP','NON_SPEECH_NOISE')
ROOT=Path(__file__).resolve().parents[2]
DEFAULT_MANIFEST=ROOT/'shared/src/commonTest/fixtures/environment/scene-head/corpus.json'


def load_manifest(path):
    manifest=json.loads(path.read_text())
    if manifest['version']!=2:
        raise ValueError('Unsupported scene corpus')
    for key in ('encoderSha256','sourceEncoderSha256'):
        if not re.fullmatch('[0-9a-f]{64}',manifest[key]):
            raise ValueError('Missing encoder pin')
    identities={}
    identifiers=set()
    hashes=set()
    filenames=set()
    for clip in manifest['recordings']:
        if not re.fullmatch('[A-Za-z0-9_-]+',clip['id']) or clip['id'] in identifiers:
            raise ValueError('Invalid/repeated recording ID')
        identifiers.add(clip['id'])
        if clip['reference'] not in LABELS or clip['partition'] not in ('development','validation'):
            raise ValueError('Invalid scene or partition')
        for key in ('author','sourceGroup','splitGroup','referenceBasis','title'):
            if not clip[key].strip() or any(c in clip[key] for c in '\t\r\n'):
                raise ValueError('Missing or multiline reference field')
        if not re.fullmatch('[0-9a-f]{64}',clip['sha256']) or clip['sha256'] in hashes:
            raise ValueError('Missing pin or duplicate recording bytes')
        hashes.add(clip['sha256'])
        if not re.fullmatch('[A-Za-z0-9_-]+\.(mp3|wav)',clip['file']) or clip['file'] in filenames:
            raise ValueError('Unsafe/repeated cache path')
        filenames.add(clip['file'])
        explicit_windows(clip)
        if 'audioExcerpt' in clip:
            excerpt=clip['audioExcerpt']
            if not clip['id'].startswith('ami-') or not isinstance(excerpt,dict) or set(excerpt)!={'startSeconds','durationSeconds'}:
                raise ValueError('Unreviewed explicit audio excerpt')
            start=excerpt['startSeconds'];seconds=excerpt['durationSeconds']
            if type(start)!=int or not 0<=start<=7200 or type(seconds)!=int or seconds not in (3,5,10) or start+seconds>7200:
                raise ValueError('Invalid audio excerpt bounds')
            if explicit_windows(clip)!=[(0,seconds)]:raise ValueError('Curated excerpt must use only its annotated complete window')
        parsed=urlparse(clip['source'])
        if parsed.scheme!='https' or parsed.username or parsed.password or parsed.port not in (None,443):
            raise ValueError('Invalid source URL')
        identities_for_clip=[('splitGroup',clip['splitGroup']),('sourceGroup',clip['sourceGroup'])]
        if clip['id'].startswith('ami-'):
            if parsed.hostname!='groups.inf.ed.ac.uk' or clip['license']!='CC-BY-4.0' or clip['licenseUrl']!='https://creativecommons.org/licenses/by/4.0/':
                raise ValueError('Unreviewed AMI license/source')
            people=clip.get('participants',[])
            if len(people)!=4 or len(set(people))!=4 or not clip.get('attribution') or not clip.get('transforms'):
                raise ValueError('AMI needs four participant IDs, attribution and transformations')
            identities_for_clip += [('participant',person) for person in people]
        else:
            licenses={'CC0-1.0':'https://creativecommons.org/publicdomain/zero/1.0/',
                      'CC-BY-3.0':'https://creativecommons.org/licenses/by/3.0/'}
            if parsed.hostname!='freesound.org' or licenses.get(clip['license'])!=clip['licenseUrl']:
                raise ValueError('Unreviewed Freesound license/source')
            if clip['license']=='CC-BY-3.0' and (not clip.get('attribution') or not clip.get('transforms')):
                raise ValueError('Attribution license requires credit and transformations')
            identities_for_clip.append(('creator',clip['author']))
        for identity in identities_for_clip:
            previous=identities.setdefault(identity,clip['partition'])
            if previous!=clip['partition']:
                raise ValueError('Creator/participant/session leaks across partitions')
    for partition in ('development','validation'):
        if {c['reference'] for c in manifest['recordings'] if c['partition']==partition}!=set(LABELS):
            raise ValueError('Both partitions must cover all six scenes')
    fresh=manifest.get('freshValidationIds',[])
    if not isinstance(fresh,list) or not all(isinstance(item,str) for item in fresh) or len(fresh)!=len(set(fresh)) or not set(fresh).issubset({c['id'] for c in manifest['recordings'] if c['partition']=='validation'}):
        raise ValueError('Fresh evaluation IDs must be unique validation recordings')
    return manifest


def cached_source(cache, clip):
    path=cache/clip['file']
    if path.stat().st_size>20_000_000 or hashlib.sha256(path.read_bytes()).hexdigest()!=clip['sha256']:
        raise ValueError('Unexpected cached source: '+clip['id'])
    return path


def explicit_windows(clip):
    if 'windows' not in clip:return None
    windows=clip['windows']
    if not isinstance(windows,list) or not 1<=len(windows)<=9:raise ValueError('Invalid explicit windows')
    result=[]
    for w in windows:
        if not isinstance(w,dict) or set(w)!={'offsetSeconds','seconds'}:raise ValueError('Unknown window fields')
        offset=w['offsetSeconds'];seconds=w['seconds']
        if type(offset)!=int or offset not in (0,10,20) or type(seconds)!=int or seconds not in (3,5,10) or offset+seconds>30:
            raise ValueError('Unreviewed window dimensions')
        result.append((offset,seconds))
    if len(set(result))!=len(result):raise ValueError('Duplicate explicit window')
    return result


def capture_windows(clip,samples):
    if type(samples)!=int or not 0<=samples<=480000:raise ValueError('Decoded source exceeds bounded duration')
    explicit=explicit_windows(clip)
    if explicit is not None:
        if any((offset+seconds)*16000>samples for offset,seconds in explicit):raise ValueError('Annotated window longer than decoded source')
        return [(offset*16000,seconds) for offset,seconds in explicit]
    return [(offset,seconds) for offset in range(0,min(samples,480000),160000) for seconds in (3,5,10) if offset+seconds*16000<=samples]
