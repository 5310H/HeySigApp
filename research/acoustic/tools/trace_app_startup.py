"""Fingerprint manually inspected Signia startup methods without executing them."""
import argparse
import json
from pathlib import Path
import trace_setup_capabilities as trace

SELECTED = {
    'RTA.Maui.Signia.dll': {
        0xc8: 'OnStart', 0xd0: 'MoveNext', 0x147: 'OnCreate',
        0x33d: 'RegisterAcousticClasses',
    },
    'RTA.Core.dll': {
        0x171c: 'InitiateLaunchSequence', 0x1794: 'MoveNext',
        0x177e: 'MoveNext', 0x1782: 'MoveNext',
        0x171f: 'InitialiseAcousticStream', 0x177c: 'MoveNext',
        0x1743: 'CheckIsPairingCompleted',
        0x14e9: 'MoveNext', 0x14ed: 'MoveNext', 0x14ef: 'MoveNext',
        0x14eb: 'MoveNext',
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
    print('Fingerprinted nineteen startup methods')
