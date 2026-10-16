"""Fingerprint manually inspected slider read/write/storage methods; no code execution."""
import argparse
import json
from pathlib import Path
import trace_setup_capabilities as trace

SELECTED = {
    'Component.RemoteControl.dll': {
        0x7fb: 'MoveNext', 0x805: 'MoveNext', 0x807: 'MoveNext',
        0x839: 'MoveNext', 0x845: 'MoveNext', 0x847: 'MoveNext',
        0x862: 'MoveNext', 0x86c: 'MoveNext', 0x86e: 'MoveNext',
    },
    'WSA.Foundation.Acoustic.dll': {
        0x1cc: 'MoveNext', 0x1ce: 'MoveNext', 0x1f4: 'MoveNext',
        0x1f6: 'MoveNext', 0x205: 'MoveNext', 0x207: 'MoveNext',
    },
}

if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('assemblies', type=Path)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    trace.SELECTED = SELECTED
    result = trace.fingerprint(args.assemblies)
    args.output.write_text(json.dumps(result, indent=2) + '\n')
    print('Fingerprinted fifteen slider-control methods')
