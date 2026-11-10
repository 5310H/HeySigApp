/* HeySig paired speech/residual adapter; float PCM normalized to [-1,1].
 * FIR: 61-tap Kaiser(beta=5), cutoff 1/3, matching scipy resample_poly's
 * default 16kHz <-> 48kHz conversion with centered zero extension.
 * Neural states reset per capture; no wearer enrollment or stored profile.
 */
#include <math.h>
#include <stdlib.h>
#include <string.h>
#ifdef __ANDROID__
#include <jni.h>
#endif
int heysig_separate(const float *,float *,float *,float *);
static const float fir[61] = {
  -4.773070466e-19f,
  -0.000507575809f,
  -0.0007171615143f,
  1.23630952e-18f,
  0.001275830902f,
  0.001636325731f,
  -2.329636736e-18f,
  -0.00255169021f,
  -0.003121165792f,
  3.737965495e-18f,
  0.004526422825f,
  0.005382746924f,
  -5.394486902e-18f,
  -0.007467288524f,
  -0.008729113266f,
  7.188058902e-18f,
  0.01180763636f,
  0.01369067002f,
  -8.973913721e-18f,
  -0.01839848794f,
  -0.0213858299f,
  1.059067297e-17f,
  0.02934103832f,
  0.03484134004f,
  -1.188119136e-17f,
  -0.0518280901f,
  -0.06626323611f,
  1.271399184e-17f,
  0.1365522146f,
  0.2751477361f,
  0.333535403f,
  0.2751477361f,
  0.1365522146f,
  1.271399184e-17f,
  -0.06626323611f,
  -0.0518280901f,
  -1.188119136e-17f,
  0.03484134004f,
  0.02934103832f,
  1.059067297e-17f,
  -0.0213858299f,
  -0.01839848794f,
  -8.973913721e-18f,
  0.01369067002f,
  0.01180763636f,
  7.188058902e-18f,
  -0.008729113266f,
  -0.007467288524f,
  -5.394486902e-18f,
  0.005382746924f,
  0.004526422825f,
  3.737965495e-18f,
  -0.003121165792f,
  -0.00255169021f,
  -2.329636736e-18f,
  0.001636325731f,
  0.001275830902f,
  1.23630952e-18f,
  -0.0007171615143f,
  -0.000507575809f,
  -4.773070466e-19f
};
static void wipe(void *p,size_t n) { volatile unsigned char *b=p;while(n--) *b++=0; }
void heysig_upsample(const float *input,float *output) {
  for(int j=0;j<144000;j++) {
    float sum=0;
    for(int k=0;k<61;k++) {
      int position=j+30-k;
      if(position>=0 && position%3==0 && position/3<48000)
        sum+=input[position/3]*(fir[k]*3.f);
    }
    output[j]=sum;
  }
}
void heysig_downsample(const float *input,float *output) {
  for(int j=0;j<48000;j++) {
    float sum=0;
    for(int k=0;k<61;k++) {
      int position=3*j+30-k;
      if(position>=0 && position<144000) sum+=input[position]*fir[k];
    }
    output[j]=sum;
  }
}
static int process16(const float *input,float *reference,float *enhanced,float *vad) {
  float *up=calloc(144000,sizeof(float)),*ref=calloc(144000,sizeof(float)),*out=calloc(144000,sizeof(float));
  float *normalized=calloc(48000,sizeof(float));int result=-1;
  if(!up || !ref || !out || !normalized) goto done;
  double power=0,peak=0;
  for(int i=0;i<48000;i++) {
    if(!isfinite(input[i]) || fabs(input[i])>2) goto done;
    power+=(double)input[i]*input[i];if(fabs(input[i])>peak) peak=fabs(input[i]);
  }
  double gain=fmax(1.,fmin(16.,fmin(.05/fmax(sqrt(power/48000),1e-12),.95/fmax(peak,1e-12))));
  for(int i=0;i<48000;i++) normalized[i]=input[i]*(float)gain;
  heysig_upsample(normalized,up);
  for(int i=0;i<144000;i++) up[i]*=32768.f;
  if(heysig_separate(up,ref,out,vad)) goto done;
  for(int i=0;i<144000;i++) { ref[i]/=(float)(32768*gain);out[i]/=(float)(32768*gain); }
  heysig_downsample(ref,reference);heysig_downsample(out,enhanced);result=0;
 done:
  if(up) { wipe(up,144000*sizeof(float));free(up); }
  if(ref) { wipe(ref,144000*sizeof(float));free(ref); }
  if(out) { wipe(out,144000*sizeof(float));free(out); }
  if(normalized) { wipe(normalized,48000*sizeof(float));free(normalized); }
  return result;
}
/* Packed output: reference[48000], enhanced[48000], VAD[300], residual VAD[300]. */
int heysig_analyze16(const float *input,float *packed) {
  float *residual=calloc(48000,sizeof(float)),*ref=calloc(48000,sizeof(float)),*out=calloc(48000,sizeof(float));
  int result=-1;
  if(!residual || !ref || !out) goto done;
  if(process16(input,packed,packed+48000,packed+96000)) goto done;
  for(int i=0;i<48000;i++) residual[i]=packed[i]-packed[i+48000];
  if(process16(residual,ref,out,packed+96300)) goto done;
  result=0;
 done:
  if(residual) { wipe(residual,48000*sizeof(float));free(residual); }
  if(ref) { wipe(ref,48000*sizeof(float));free(ref); }
  if(out) { wipe(out,48000*sizeof(float));free(out); }
  if(result) wipe(packed,96600*sizeof(float));
  return result;
}
#ifdef __ANDROID__
JNIEXPORT jfloatArray JNICALL Java_com_heysig_app_NeuralSeparation_analyzeNative(JNIEnv *env,jobject owner,jshortArray pcm) {
  (void)owner;
  if(!pcm || (*env)->GetArrayLength(env,pcm)!=48000) {
    (*env)->ThrowNew(env,(*env)->FindClass(env,"java/lang/IllegalArgumentException"),"Expected three-second capture");return NULL;
  }
  short *raw=calloc(48000,sizeof(short));float *input=calloc(48000,sizeof(float)),*packed=calloc(96600,sizeof(float));
  jfloatArray result=NULL;
  if(!raw || !input || !packed) goto failure;
  (*env)->GetShortArrayRegion(env,pcm,0,48000,raw);
  if((*env)->ExceptionCheck(env)) goto done;
  for(int i=0;i<48000;i++) input[i]=raw[i]/32768.f;
  if(heysig_analyze16(input,packed)) goto failure;
  result=(*env)->NewFloatArray(env,96600);
  if(result) (*env)->SetFloatArrayRegion(env,result,0,96600,packed);
  goto done;
 failure:
  (*env)->ThrowNew(env,(*env)->FindClass(env,"java/lang/IllegalStateException"),"Speech separation unavailable");
 done:
  if(raw) { wipe(raw,48000*sizeof(short));free(raw); }
  if(input) { wipe(input,48000*sizeof(float));free(input); }
  if(packed) { wipe(packed,96600*sizeof(float));free(packed); }
  return result;
}
#endif
