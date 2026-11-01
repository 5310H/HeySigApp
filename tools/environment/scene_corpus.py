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
            if parsed.hostname!='freesound.org' or clip['license']!='CC0-1.0' or clip['licenseUrl']!='https://creativecommons.org/publicdomain/zero/1.0/':
                raise ValueError('Unreviewed Freesound license/source')
            identities_for_clip.append(('creator',clip['author']))
        for identity in identities_for_clip:
            previous=identities.setdefault(identity,clip['partition'])
            if previous!=clip['partition']:
                raise ValueError('Creator/participant/session leaks across partitions')
    for partition in ('development','validation'):
        if {c['reference'] for c in manifest['recordings'] if c['partition']==partition}!=set(LABELS):
            raise ValueError('Both partitions must cover all six scenes')
    return manifest


def cached_source(cache, clip):
    path=cache/clip['file']
    if path.stat().st_size>20_000_000 or hashlib.sha256(path.read_bytes()).hexdigest()!=clip['sha256']:
        raise ValueError('Unexpected cached source: '+clip['id'])
    return path
