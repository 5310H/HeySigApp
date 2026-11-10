"""Development-only pinned RNNoise adapter. Never reads a microphone or writes audio."""
import ctypes
from pathlib import Path
import numpy as np
from scipy.signal import resample_poly


class Denoiser:
    def __init__(self, library: Path):
        self.lib=ctypes.CDLL(str(library.resolve()))
        pointer=np.ctypeslib.ndpointer(dtype=np.float32,ndim=1,flags='C_CONTIGUOUS')
        self.lib.heysig_separate.argtypes=[pointer,pointer,pointer,pointer]
        self.lib.heysig_separate.restype=ctypes.c_int
        self.lib.heysig_reference.argtypes=[pointer,pointer]
        self.lib.heysig_reference.restype=ctypes.c_int
        self.lib.heysig_analyze16.argtypes=[pointer,pointer]
        self.lib.heysig_analyze16.restype=ctypes.c_int

    @staticmethod
    def upsample(samples):
        x=np.asarray(samples,dtype=np.float32)
        if x.shape!=(48000,) or not np.isfinite(x).all() or np.max(np.abs(x))>2:
            raise ValueError('Expected bounded three-second normalized 16kHz audio')
        return np.ascontiguousarray(resample_poly(x,3,1)*32768,dtype=np.float32)

    def reference(self,samples):
        x=self.upsample(samples);reference=np.zeros(144000,dtype=np.float32)
        try:
            if self.lib.heysig_reference(x,reference):raise RuntimeError('Reference path failed')
            return np.ascontiguousarray(resample_poly(reference/32768,1,3),dtype=np.float32)
        finally:x.fill(0);reference.fill(0)

    def process(self,samples):
        original=np.asarray(samples,dtype=np.float32)
        rms=float(np.sqrt(np.mean(original.astype(np.float64)**2)))
        peak=float(np.max(np.abs(original)))
        # Same bounded per-capture digital gain rule as the existing encoder.
        # This is not an acoustic SPL calibration or a learned wearer profile.
        gain=max(1.,min(16.,.05/max(rms,1e-12),.95/max(peak,1e-12)))
        x=self.upsample(original*gain);reference=np.zeros(144000,dtype=np.float32)
        enhanced=np.zeros_like(reference);vad=np.zeros(300,dtype=np.float32)
        try:
            if self.lib.heysig_separate(x,reference,enhanced,vad):raise RuntimeError('Denoiser failed')
            result=(np.ascontiguousarray(resample_poly(reference/(32768*gain),1,3),dtype=np.float32),
                np.ascontiguousarray(resample_poly(enhanced/(32768*gain),1,3),dtype=np.float32),vad.copy())
            if any(not a.size or not np.isfinite(a).all() for a in result):raise RuntimeError('Invalid denoiser output')
            return result
        finally:x.fill(0);reference.fill(0);enhanced.fill(0);vad.fill(0)

    def separate_scipy(self,samples):
        reference,enhanced,vad=self.process(samples)
        residual=reference-enhanced
        try:
            residual_reference,residual_enhanced,residual_vad=self.process(residual)
            residual_reference.fill(0);residual_enhanced.fill(0)
            return reference,enhanced,vad,residual_vad
        except BaseException:
            reference.fill(0);enhanced.fill(0);vad.fill(0);raise
        finally:residual.fill(0)

    def separate(self,samples):
        x=np.array(samples,dtype=np.float32,copy=True,order='C');packed=np.zeros(96600,dtype=np.float32)
        try:
            if x.shape!=(48000,) or not np.isfinite(x).all() or np.max(np.abs(x))>2:
                raise ValueError('Expected bounded three-second normalized audio')
            if self.lib.heysig_analyze16(x,packed):raise RuntimeError('Native separation failed')
            if not np.isfinite(packed).all():raise RuntimeError('Nonfinite native output')
            return packed[:48000].copy(),packed[48000:96000].copy(),packed[96000:96300].copy(),packed[96300:].copy()
        finally:x.fill(0);packed.fill(0)
