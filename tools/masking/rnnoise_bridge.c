/* HeySig development-only RNNoise adapter. Upstream BSD notices are retained
 * in third_party/rnnoise/COPYING. Pin the upstream source before compiling.
 * Include the private DSP implementation to provide a matched unity-gain path:
 * comparing against raw input would miscount its high-pass filter as noise.
 */
#include "denoise.c"
#include <string.h>

static void erase(void *p, size_t count) {
  volatile unsigned char *bytes = p;
  while (count--) *bytes++ = 0;
}

static void reference_frame(DenoiseState *st, float *out, const float *in) {
  static const float a[2] = {-1.99599f, .99600f};
  static const float b[2] = {-2.f, 1.f};
  float filtered[FRAME_SIZE], energy[NB_BANDS];
  kiss_fft_cpx spectrum[FREQ_SIZE];
  rnn_biquad(filtered, st->mem_hp_x, in, b, a, FRAME_SIZE);
  rnn_frame_analysis(st, spectrum, energy, filtered);
  frame_synthesis(st, out, st->delayed_X);
  RNN_COPY(st->delayed_X, spectrum, FREQ_SIZE);
  erase(filtered, sizeof(filtered)); erase(energy, sizeof(energy));
  erase(spectrum, sizeof(spectrum));
}

/* Exactly three seconds at 48kHz, float PCM in int16 amplitude units.
 * Both paths have the same 960-sample/20ms delay. Flush it, then crop it
 * identically. No alignment is estimated from speech or tuned on test audio.
 * VAD is the network score associated with each applied suppression frame;
 * it is not target-speaker identity or a word intelligibility probability.
 */
int heysig_separate(const float *input, float *reference, float *enhanced, float *vad) {
  DenoiseState *model = rnnoise_create(NULL), *unity = rnnoise_create(NULL);
  float clean[FRAME_SIZE], matched[FRAME_SIZE], zero[FRAME_SIZE] = {0};
  int status = -1;
  if (!model || !unity) goto done;
  for (int f = 0; f < 302; f++) {
    const float *in = f < 300 ? input + f*FRAME_SIZE : zero;
    float probability = rnnoise_process_frame(model, clean, in);
    reference_frame(unity, matched, in);
    if (f >= 2) {
      memcpy(reference+(f-2)*FRAME_SIZE, matched, sizeof(matched));
      memcpy(enhanced+(f-2)*FRAME_SIZE, clean, sizeof(clean));
      vad[f-2] = probability;
    }
  }
  status = 0;
done:
  erase(clean,sizeof(clean)); erase(matched,sizeof(matched)); erase(zero,sizeof(zero));
  if (model) { erase(model,rnnoise_get_size()); rnnoise_destroy(model); }
  if (unity) { erase(unity,rnnoise_get_size()); rnnoise_destroy(unity); }
  return status;
}

/* Cheap oracle/reference path for development alignment and speech-loss tests. */
int heysig_reference(const float *input, float *reference) {
  DenoiseState *unity = rnnoise_create(NULL);
  float output[FRAME_SIZE], zero[FRAME_SIZE] = {0};
  if (!unity) return -1;
  for (int f=0; f<302; f++) {
    reference_frame(unity,output,f<300 ? input+f*FRAME_SIZE : zero);
    if (f>=2) memcpy(reference+(f-2)*FRAME_SIZE,output,sizeof(output));
  }
  erase(output,sizeof(output));erase(zero,sizeof(zero));
  erase(unity,rnnoise_get_size());rnnoise_destroy(unity);
  return 0;
}
