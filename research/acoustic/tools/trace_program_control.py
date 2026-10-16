"""Fingerprint the manually traced program read/change paths; never execute Signia code."""
import argparse
import json
from pathlib import Path
import trace_setup_capabilities as trace

SELECTED = {
    'Component.RemoteControl.dll': {
        0x7c6: 'MoveNext', 0x7c8: 'MoveNext', 0x7ca: 'MoveNext',
        0x7bd: '<InternalWriteAsync>b__3', 0x7d0: 'MoveNext',
    },
    'WSA.Foundation.Acoustic.dll': {0x1b6: 'MoveNext', 0x1b8: 'MoveNext'},
}

if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('assemblies', type=Path)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    trace.SELECTED = SELECTED
    result = trace.fingerprint(args.assemblies)
    args.output.write_text(json.dumps(result, indent=2) + '\n')
    print('Fingerprinted seven program-control methods')
