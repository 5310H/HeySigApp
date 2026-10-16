#!/usr/bin/env python3
"""Configure own development apps over already-authorized ADB; never print secrets."""
import argparse
import json
from pathlib import Path
import subprocess

parser = argparse.ArgumentParser()
parser.add_argument('--watch',required=True,help='Watch ADB connection IP:port (not pairing port)')
parser.add_argument('--android',default='emulator-5554')
parser.add_argument('--directory',type=Path,default=Path('private/watch-relay'))
args = parser.parse_args()
config = json.loads((args.directory/'pairing.json').read_text())

def run(arguments, data=None):
    result = subprocess.run(['adb']+arguments,input=data,capture_output=True)
    if result.returncode:
        raise SystemExit('ADB setup failed. Check authorized device connections; configuration secrets were not printed.')
    return result.stdout.decode(errors='replace')

run(['-s',args.watch,'shell',"run-as com.heysig.watchdev sh -c 'umask 077; mkdir -p files; cat > files/pairing.json'"],json.dumps(config).encode())
reply = run(['-s',args.android,'shell','am','broadcast','-n','com.heysig.batteryprototype/com.heysig.app.WatchBridgeSetup',
             '-a','com.heysig.PAIR_WATCH_BRIDGE','--es','token',config['token']])
if 'result=1' not in reply:
    raise SystemExit('Chromebook app did not accept relay setup. Install the watch-enabled app build first.')
run(['-s',args.android,'forward','tcp:18765','tcp:18765'])
print('Private watch configuration and Chromebook bridge token installed; loopback forwarding ready.')
print('Open HeySig on Chromebook and enable Watch Wi-Fi. Start the relay in status-only mode first.')
