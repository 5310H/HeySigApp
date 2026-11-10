"""Explicit, pinned development build. No Android library or model is packaged by this tool."""
import argparse,hashlib,json,platform,shutil,subprocess,tarfile,tempfile,urllib.request
from pathlib import Path

REVISION='70f1d256acd4b34a572f999a05c87bf00b67730d'
MODEL='0a8755f8e2d834eff6a54714ecc7d75f9932e845df35f8b59bc52a7cfe6e8b37'
ROOT=Path(__file__).resolve().parents[2]
def sha(path):return hashlib.sha256(path.read_bytes()).hexdigest()
def run(*args,**kwargs):return subprocess.run(args,check=True,**kwargs)

def main():
    p=argparse.ArgumentParser();p.add_argument('--cache',type=Path,required=True)
    p.add_argument('--android-ndk',type=Path);p.add_argument('--android-output',type=Path)
    p.add_argument('--download',action='store_true',help='Explicitly allow upstream source/model downloads')
    a=p.parse_args();cache=a.cache.resolve()
    if not (cache/'.git').exists():
        if not a.download:raise SystemExit('Missing source cache; downloads require --download')
        if cache.exists() and any(cache.iterdir()):raise SystemExit('Refusing to overwrite a non-repository cache')
        cache.mkdir(parents=True,exist_ok=True)
        run('git','init',str(cache));run('git','-C',str(cache),'remote','add','origin','https://github.com/xiph/rnnoise.git')
        run('git','-C',str(cache),'fetch','--depth','1','origin',REVISION)
        run('git','-C',str(cache),'checkout','--detach',REVISION)
    assert subprocess.check_output(['git','-C',str(cache),'rev-parse','HEAD'],text=True).strip()==REVISION
    run('git','-C',str(cache),'diff','--exit-code',REVISION,'--','src','include','COPYING')
    archive=cache/f'rnnoise_data-{MODEL}.tar.gz'
    if not archive.exists():
        if not a.download:raise SystemExit('Missing model archive; downloads require --download')
        url=f'https://media.xiph.org/rnnoise/models/{archive.name}'
        with urllib.request.urlopen(url,timeout=60) as response,archive.open('xb') as output:
            count=0
            while True:
                block=response.read(65536)
                if not block:break
                count+=len(block)
                if count>64*1024*1024:raise ValueError('Model archive exceeds download bound')
                output.write(block)
    assert sha(archive)==MODEL,'Model archive checksum mismatch; no automatic replacement'
    with tarfile.open(archive) as source:
        for name in ('src/rnnoise_data_little.c','src/rnnoise_data_little.h'):
            member=source.getmember(name)
            assert member.isfile() and member.size<32*1024*1024
            (cache/name.replace('_little','')).write_bytes(source.extractfile(member).read())
    assert (cache/'COPYING').read_bytes()==(ROOT/'third_party/rnnoise/COPYING').read_bytes()
    resampler=ROOT/'tools/masking/rnnoise_resample.c'
    sources=['rnn.c','pitch.c','kiss_fft.c','celt_lpc.c','nnet.c','nnet_default.c','parse_lpcnet_weights.c','rnnoise_data.c','rnnoise_tables.c']
    compiler=shutil.which('gcc')
    if not compiler:raise SystemExit('A host C compiler is required')
    output=cache/'libheysig_separation.so';bridge=ROOT/'tools/masking/rnnoise_bridge.c'
    with tempfile.NamedTemporaryFile(prefix='rnnoise-build-',suffix='.so',dir=cache,delete=False) as handle:
        temporary=Path(handle.name)
    try:
        run(compiler,'-O3','-fPIC','-shared','-DRNNOISE_BUILD','-I'+str(cache/'include'),'-I'+str(cache/'src'),str(bridge),str(resampler),
            *(str(cache/'src'/name) for name in sources),'-o',str(temporary),'-lm')
        temporary.replace(output)
    finally:
        temporary.unlink(missing_ok=True)
    proof=dict(sourceRevision=REVISION,modelArchiveSha256=MODEL,modelVariant='little',
        modelSourceSha256=sha(cache/'src/rnnoise_data.c'),modelHeaderSha256=sha(cache/'src/rnnoise_data.h'),
        bridgeSha256=sha(bridge),resamplerSha256=sha(resampler),librarySha256=sha(output),licenseSha256=sha(cache/'COPYING'),
        machine=platform.machine(),compiler=subprocess.check_output([compiler,'--version'],text=True).splitlines()[0],
        scope='development-only host library; not an Android ABI/runtime qualification or licensed-data independence claim')
    if a.android_ndk:
        ndk=a.android_ndk.resolve()
        assert 'Pkg.Revision = 27.2.12479018' in (ndk/'source.properties').read_text()
        compiler=ndk/'toolchains/llvm/prebuilt/linux-x86_64/bin/clang'
        output_root=(a.android_output or cache/'android').resolve();native={}
        for abi,target in [('arm64-v8a','aarch64-linux-android29'),('armeabi-v7a','armv7a-linux-androideabi29'),('x86','i686-linux-android29'),('x86_64','x86_64-linux-android29')]:
            destination=output_root/abi/'libheysig_rnnoise.so';destination.parent.mkdir(parents=True,exist_ok=True)
            run(str(compiler),'--target='+target,'-O3','-fPIC','-shared','-DRNNOISE_BUILD',
                '-Wl,-z,max-page-size=16384','-Wl,-z,common-page-size=16384','-Wl,--no-undefined',
                '-I'+str(cache/'include'),'-I'+str(cache/'src'),str(bridge),str(resampler),
                *(str(cache/'src'/name) for name in sources),'-o',str(destination),'-lm')
            run(str(compiler.parent/'llvm-strip'),'--strip-unneeded',str(destination))
            native[abi+'/libheysig_rnnoise.so']=sha(destination)
        proof['android']=dict(ndkRevision='27.2.12479018',minSdk=29,native=native)
        (output_root/'build.json').write_text(json.dumps(proof,indent=2)+'\n')
    (cache/'heysig-build.json').write_text(json.dumps(proof,indent=2)+'\n')
    print(json.dumps(proof,indent=2))

if __name__=='__main__':main()
