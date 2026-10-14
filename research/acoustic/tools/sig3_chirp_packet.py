"""Offline acoustic control-bit model. No waveform generation or device playback.

Positions below are protocol receiver positions, not Signia UI slider values.
The installed profile/address and UI conversion must be verified separately.
"""
import argparse
from dataclasses import asdict, dataclass
import json


def byte(value):
    if type(value) is not int or not 0 <= value <= 255:
        raise ValueError('Expected an integer byte, 0–255')
    return value


def position_command(family, position):
    ranges = {'volume': (16, 16), 'balance': (32, 16),
              'tinnitus': (48, 16), 'program': (64, 6)}
    if family not in ranges:
        raise ValueError('Unknown protocol command family')
    base, count = ranges[family]
    if type(position) is not int or not 0 <= position < count:
        raise ValueError('Protocol position outside the family range')
    return base + position


def crc8(command):
    """One byte, MSB first, polynomial 0x07, initial zero, no final XOR."""
    crc = byte(command)
    for _ in range(8):
        crc = ((crc << 1) ^ (7 if crc & 128 else 0)) & 255
    return crc


@dataclass(frozen=True)
class ControlBits:
    command: int
    address: int
    address_xor: bool
    checksum: int
    data_a: tuple
    data_b: tuple


def control_bits(command, address, *, address_xor):
    byte(command); byte(address)
    if type(address_xor) is not bool:
        raise ValueError('Select the profile address-XOR mode explicitly')
    checksum = crc8(command) ^ (address ^ 0xaa if address_xor else 0)
    # The encoder interleaves MSB-first command/checksum bits in two streams.
    a = tuple(bit for i in range(4)
              for bit in ((command >> (7-i)) & 1, (checksum >> (7-i)) & 1))
    b = tuple(bit for i in range(4, 8)
              for bit in ((checksum >> (7-i)) & 1, (command >> (7-i)) & 1))
    return ControlBits(command, address, address_xor, checksum, a, b)


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--command', type=lambda v: int(v, 0), required=True)
    parser.add_argument('--address', type=lambda v: int(v, 0), required=True)
    parser.add_argument('--address-xor', choices=('on', 'off'), required=True)
    args = parser.parse_args()
    print(json.dumps(asdict(control_bits(args.command, args.address,
                         address_xor=args.address_xor == 'on')), indent=2))
