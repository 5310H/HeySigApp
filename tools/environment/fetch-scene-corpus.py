"""Explicit developer download of only the reviewed, hash-pinned public sources."""
import argparse, hashlib, json, struct, urllib.parse, urllib.request, wave, io
from pathlib import Path
from scene_corpus import load_manifest, cached_source, DEFAULT_MANIFEST

p=argparse.ArgumentParser(description=__doc__)
p.add_argument('--manifest',type=Path,default=DEFAULT_MANIFEST)
p.add_argument('--cache',type=Path,required=True)
a=p.parse_args(); manifest=load_manifest(a.manifest); a.cache.mkdir(parents=True,exist_ok=True)
def read(url,limit,headers=None):
    if not url.startswith('https://'): raise ValueError('HTTPS required')
    with urllib.request.urlopen(urllib.request.Request(url,headers=headers or {}),timeout=90) as r:
        data=r.read(limit+1)
        if len(data)>limit: raise ValueError('Source exceeds download bound')
        return data,r.status,r.headers

def ranged(url,start,end):
    data,status,headers=read(url+'?range_start='+str(start),end-start+1,{'Range':f'bytes={start}-{end}'})
    if status!=206 or not headers.get('Content-Range','').startswith(f'bytes {start}-{end}/') or len(data)!=end-start+1:
        raise ValueError('Server did not honor bounded range')
    return data

for c in manifest['recordings']:
    target=a.cache/c['file']
    if target.exists(): cached_source(a.cache,c); print(c['id'],'cached'); continue
    if c['id'].startswith('fsd-'):
        params={'dataset':'philgzl/fsd50k','config':'default','split':c['mirrorSplit'],'offset':c['mirrorRowIndex'],'length':1}
        data,_,_=read('https://datasets-server.huggingface.co/rows?'+urllib.parse.urlencode(params),1_000_000)
        row=json.loads(data)['rows'][0]
        if row['row_idx']!=c['mirrorRowIndex'] or row['row']['name']!=c['mirrorRowName']: raise ValueError('Mirror row changed')
        url=row['row']['audio'][0]['src']; parsed=urllib.parse.urlparse(url)
        if parsed.scheme!='https' or parsed.hostname!='datasets-server.huggingface.co' or not parsed.path.startswith(('/assets/','/cached-assets/')) or '/'+c['mirrorRevision']+'/' not in parsed.path:
            raise ValueError('Unexpected mirror revision or host')
        data,_,_=read(url,8_000_000)
    elif c['id'].startswith('ami-'):
        url=c['download']
        if not url.startswith('https://huggingface.co/datasets/ggfox00000/dia-AMICorpus-all/resolve/a249db5/'): raise ValueError('Unexpected AMI mirror')
        header=ranged(url,0,1023)
        if header[:4]!=b'RIFF' or header[8:12]!=b'WAVE': raise ValueError('Expected WAV')
        cursor=12; fmt=None
        while cursor+8<len(header):
            tag=header[cursor:cursor+4]; size=struct.unpack_from('<I',header,cursor+4)[0]
            if tag==b'fmt ': fmt=struct.unpack_from('<HHIIHH',header,cursor+8)
            if tag==b'data': offset=cursor+8; break
            cursor+=8+size+(size%2)
        else: raise ValueError('Missing bounded WAV header')
        if fmt!=(1,1,16000,32000,2,16): raise ValueError('Unexpected source format')
        excerpt=c.get('audioExcerpt',{'startSeconds':120,'durationSeconds':30})
        first=offset+excerpt['startSeconds']*32000;count=excerpt['durationSeconds']*32000
        pcm=ranged(url,first,first+count-1); output=io.BytesIO()
        with wave.open(output,'wb') as f:
            f.setnchannels(1); f.setsampwidth(2); f.setframerate(16000); f.writeframes(pcm)
        data=output.getvalue()
    else:
        url=c['download']
        if urllib.parse.urlparse(url).hostname!='cdn.freesound.org': raise ValueError('Unexpected preview host')
        data,_,_=read(url,20_000_000)
    if hashlib.sha256(data).hexdigest()!=c['sha256']: raise ValueError('Changed source bytes: '+c['id'])
    temporary=target.with_suffix('.partial'); temporary.write_bytes(data); temporary.replace(target)
    print(c['id'],'verified')
