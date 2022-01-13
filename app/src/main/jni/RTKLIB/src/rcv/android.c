#include "rtklib.h"
#include "android.h"

extern int input_androidf (raw_t *raw, FILE *fp){
  unsigned char data;

  data = fgetc(fp);

  if (data == EOF) return (int) endOfFile;

  return input_android(raw, data);
}  

extern int input_android (raw_t *raw,  unsigned char data){

  /* === TODO: NOTES === /

   1. Some data is represented as unsigned chars in obsd_t but as doubles in androidStruct.
      How shall a double be represented in an unsigned char? The decimals are lost and a
      number greater than 255 causes overflow.

   2. TOW or gps week needs to be fetched and updated. it is currently set to a week 
      corresponding the week which starts 2019-02-25

   3. LLI (loss of lock indicator). getAccumulatedDeltaRangeState indicates the "loss of lock"
      state however the states are not the same in android as in rtklib. the current "conversion"
      between the states are probably WRONG.

   4. CODE_??? <--- what, que, vafan? rtklib_h, row number 283-339

   5. add return 'returncode'

   --------------- New additions-02/25 ------------------------------------ 

   6. Fix sizeof() error. 

  / ==== NOTES END ==== */

  android_clockd_t cl;
  android_measurements_t ms;
  int cl_size, ms_size, msd_size;
  int num_ms;
  unsigned char *bufptr;

  trace(5, "input_android, data=%02x\n", data);

   /* Store new byte */
  raw->buff[raw->nbyte++] = data;

  cl_size = ANDROID_CLOCKD_RECEIVED_SIZE;
  ms_size = 4;
  msd_size = ANDROID_MEASUREMENTSD_RECEIVED_SIZE;

   /* Check if finished receiving android_clockd_t and android_measurements_t */
  if (raw->nbyte == cl_size + ms_size) {
    bufptr = raw->buff + cl_size;
    num_ms = readInt(&bufptr);

    if (num_ms == 0) {
      raw->len = 0;
      raw->nbyte = 0;
      return noMsg;
    }

     /* Calculate and store expected total length of message */
    raw->len = cl_size + ms_size + num_ms * msd_size;
    trace(5, "raw->len = %d\n", raw->len);
  }

   /* Check if complete message is received */
  if (raw->len > 0 && raw->nbyte == raw->len) {

     /* Point the structs */
    bufptr = raw->buff;
    parseClockData(&cl, &bufptr);
    parseMeasurementData(&ms, &bufptr);

    trace(3, "received complete struct\n");

    /* Reset buffer */
    raw->len = 0;
    raw->nbyte = 0;

    return convertObservationData(&raw->obs, &cl, &ms);
  }

  return (int) noMsg; /* Keep buffering */
}

/* Fill obs_t with data from android_clockd_t and andorid_measurements_t */
int convertObservationData(obs_t *obs, android_clockd_t *cl, android_measurements_t *ms) {
  int i;
  obsd_t *obsd;
  android_measurementsd_t *android_obs;
  gtime_t rcv_time, sat_time;
  long msg_time_nanos;
  int rcv_week;
  double sat_tow;

  trace(3, "converting observation data\n");

  /* Set number of observations */
  obs->n = ms->n;
  trace(4, "obsd_t.n = %d\n", obs->n);

  /* Calculate GPS time for message */
  msg_time_nanos = cl->timeNanos - cl->fullBiasNanos;
  trace(4, "msg_time_nanos = %ld\n", msg_time_nanos);

  /* Fill obs_t->data */
  for (i = 0; i < ms->n; i++) {
    trace(4, "storing measurement %d\n", i);
    obsd = &obs->data[i];
    android_obs = &ms->measurements[i];

    /* === receiver sampling time (GPST) === */ 
    rcv_time = nano2gtime(msg_time_nanos + android_obs->timeOffsetNanos);
    obsd->time = rcv_time;
    trace(4, "obsd_t.time.time = %lu, obsd_t.time.sec = %f\n", obsd->time.time, obsd->time.sec);

    /* === satellite/receiver number === */
    obsd->sat = android_obs->svid;

    /* === satellite/receiver number === */
    /* obsd->rcv      = recId;                    */

    /* === signal strength (0.25 dBHz) === */
    obsd->SNR[0] = (unsigned char)(android_obs->cn0DbHz*4);
    trace(4, "obsd_t.SNR[0] = %d\n", obsd->SNR[0]);

    /* === loss of lock indicator === */
    /* obsd->LLI[0]   = lli;                      */

    /* === code indicator (CODE_???) === */
    /* obsd->code[0]  = null;                     */

    /* === quality of carrier phase measurement === */
    /* obsd->qualL[0] = null;                     */

    /* === quality of pseudorange measurement === */
    /* obsd->qualP[0] = null;                     */

    /* === observation data carrier-phase (cycle) === */
    /* obsd->L[0]     = carrierPhase;             */

    /*  === observation data pseudorange (m) === */

    /* Calculate GPST for satellite */
    /* We only receive satellite TOW from Android, so we use week from rcv_time instead */
    time2gpst(rcv_time, &rcv_week);  /* Calculate week number */
    trace(4, "week = %d\n", rcv_week);

    sat_tow = nano2sec(android_obs->receivedSvTimeNanos); /* Calculate time-of-week */
    trace(4, "sat_tow = %f\n", sat_tow);

    sat_time = nano2gtime(rcv_week*WEEK2SEC*SEC2NSEC + android_obs->receivedSvTimeNanos);

    trace(4, "sat_time.time = %lu, sat_time.sec = %f\n", sat_time.time, sat_time.sec);
    trace(4, "rcv_time.time = %lu, rcv_time.sec = %f\n", rcv_time.time, rcv_time.sec);

    obsd->P[0] = calcPseudoRange(rcv_time, sat_time);
    trace(4,"obsd_t.P[0] = %f\n", obsd->P[0]);

    /* === observation data doppler frequency (Hz) === */
    /* obsd->D[0]     = null;                     */
  }

  return (int) observationData;
}


/* ========= ============ ========= */ 
/* ========= Calculations ========= */ 
/* ========= ============ ========= */ 
double nano2sec(long t){
  return t/((double)SEC2NSEC);
}

gtime_t nano2gtime(long nanoSec){
  int week;
  double sec;

  week = nanoSec / (double)SEC2NSEC / (double)WEEK2SEC;
  sec = nanoSec / (double)SEC2NSEC - week * WEEK2SEC;

  return gpst2time(week, sec);
}

double calcPseudoRange(gtime_t rx, gtime_t tx){
  double diff = timediff(rx, tx);
  trace(5, "timediff = %f\n", diff);
  return diff * CLIGHT;
}


/* ========= ============ ========= */ 
/* ========= Parsing      ========= */ 
/* ========= ============ ========= */ 

/* Fill android_clockd_t struct from raw byte sequence */
void parseClockData(android_clockd_t *cl, unsigned char **ptr) {
  trace(4, "parsing clock data\n");
  trace(5, "cl->biasNanos\n");
  cl->biasNanos = readDouble(ptr);
  trace(5, "cl->biasUncertaintyNanos\n");
  cl->biasUncertaintyNanos = readDouble(ptr); 
  trace(5, "cl->driftNanosPerSecond\n");
  cl->driftNanosPerSecond = readDouble(ptr);
  trace(5, "cl->driftUncertaintyNanosPerSecond\n");
  cl->driftUncertaintyNanosPerSecond = readDouble(ptr);
  trace(5, "cl->fullBiasNanos\n");
  cl->fullBiasNanos = readLong(ptr); 
  trace(5, "cl->hardwareClockDiscontinuityCount\n");
  cl->hardwareClockDiscontinuityCount = readInt(ptr);
  trace(5, "cl->leapSecond\n");
  cl->leapSecond = readInt(ptr);
  trace(5, "cl->timeNanos\n");
  cl->timeNanos = readLong(ptr);
  trace(5, "cl->timeUncertaintyNanos\n");
  cl->timeUncertaintyNanos = readDouble(ptr);

  trace(5, "cl->hasBiasNanos\n");
  cl->hasBiasNanos = readInt(ptr);
  trace(5, "cl->hasBiasUncertaintyNanos\n");
  cl->hasBiasUncertaintyNanos = readInt(ptr); 
  trace(5, "cl->hasDriftNanosPerSecond\n");
  cl->hasDriftNanosPerSecond = readInt(ptr); 
  trace(5, "cl->hasDriftUncertaintyNanosPerSecond\n");
  cl->hasDriftUncertaintyNanosPerSecond = readInt(ptr); 
  trace(5, "cl->hasFullBiasNanos\n");
  cl->hasFullBiasNanos = readInt(ptr); 
  trace(5, "cl->hasLeapSecond\n");
  cl->hasLeapSecond = readInt(ptr); 
  trace(5, "cl->hasTimeUncertaintyNanos\n");
  cl->hasTimeUncertaintyNanos = readInt(ptr); 
}

/* Fill android_measurements_t struct from raw byte sequence */
void parseMeasurementData(android_measurements_t *ms, unsigned char **ptr) {
  int i;
  android_measurementsd_t *msd;

  trace(4, "parsing measurement data\n");

  ms->n = readInt(ptr);

  trace(4, "ms->n = %d\n", ms->n);
  
  for (i = 0; i < ms->n; i++) {
    msd = &ms->measurements[i];
    trace(4, "parsing measurement %d\n", i);

    trace(5, "msd->accumulatedDeltaRangeMeters\n");
    msd->accumulatedDeltaRangeMeters = readDouble(ptr);                        
    trace(5, "msd->accumulatedDeltaRangeState\n");
    msd->accumulatedDeltaRangeState = readInt(ptr);
    trace(5, "msd->accumulatedDeltaRangeUncertaintyMeters\n");
    msd->accumulatedDeltaRangeUncertaintyMeters = readDouble(ptr);
    trace(5, "msd->automaticGainControlLevelDbc\n");
    msd->automaticGainControlLevelDbc = readDouble(ptr);
    trace(5, "msd->carrierCycles\n");
    msd->carrierCycles = readLong(ptr);
    trace(5, "msd->carrierFrequencyHz\n");
    msd->carrierFrequencyHz = readFloat(ptr);
    trace(5, "msd->carrierPhase\n");
    msd->carrierPhase = readDouble(ptr);
    trace(5, "msd->carrierPhaseUncertainty\n");
    msd->carrierPhaseUncertainty = readDouble(ptr);
    trace(5, "msd->cn0DbHz\n");
    msd->cn0DbHz = readDouble(ptr);
    trace(5, "msd->constellationType\n");
    msd->constellationType = readInt(ptr);
    trace(5, "msd->multipathIndicator\n");
    msd->multipathIndicator = readInt(ptr);
    trace(5, "msd->pseudorangeRateUncertaintyMetersPerSecond\n");
    msd->pseudorangeRateUncertaintyMetersPerSecond = readDouble(ptr);
    trace(5, "msd->receivedSvTimeNanos\n");
    msd->receivedSvTimeNanos = readLong(ptr);
    trace(5, "msd->receivedSvTimeUncertaintyNanos\n");
    msd->receivedSvTimeUncertaintyNanos = readLong(ptr);
    trace(5, "msd->snrInDb\n");
    msd->snrInDb = readDouble(ptr);
    trace(5, "msd->state\n");
    msd->state = readInt(ptr);
    trace(5, "msd->svid\n");
    msd->svid = readInt(ptr);
    trace(5, "msd->timeOffsetNanos\n");
    msd->timeOffsetNanos = readDouble(ptr);

    trace(5, "msd->hasAutomaticGainControlLevelDb\n");
    msd->hasAutomaticGainControlLevelDb = readInt(ptr);
    trace(5, "msd->hasCarrierCycles\n");
    msd->hasCarrierCycles = readInt(ptr);
    trace(5, "msd->hasCarrierFrequencyHz\n");
    msd->hasCarrierFrequencyHz = readInt(ptr);
    trace(5, "msd->hasCarrierPhase\n");
    msd->hasCarrierPhase = readInt(ptr);
    trace(5, "msd->hasCarrierPhaseUncertainty\n");
    msd->hasCarrierPhaseUncertainty = readInt(ptr);
    trace(5, "msd->hasSnrInDb\n");
    msd->hasSnrInDb = readInt(ptr);
  }
}

int readInt(unsigned char **ptr) {
  int val;
  memcpy(&val, *ptr, sizeof(int));

  trace(5, "parsing int: %02X%02X%02X%02X -> %d\n",
      ((unsigned char*) *ptr)[0],
      ((unsigned char*) *ptr)[1],
      ((unsigned char*) *ptr)[2],
      ((unsigned char*) *ptr)[3],
      val);
  *ptr += sizeof(int);
  return val;
}

double readDouble(unsigned char **ptr) {
  double val;
  memcpy(&val, *ptr, sizeof(double));

  trace(5, "parsing double: %f\n", val);
  *ptr += sizeof(double);
  return val;
}

long readLong(unsigned char **ptr) {
  long val;
  memcpy(&val, *ptr, sizeof(long));

  trace(5, "parsing long: %02X%02X%02X%02X %02X%02X%02X%02X -> %ld\n", 
      ((unsigned char*) *ptr)[0],
      ((unsigned char*) *ptr)[1],
      ((unsigned char*) *ptr)[2],
      ((unsigned char*) *ptr)[3],
      ((unsigned char*) *ptr)[4],
      ((unsigned char*) *ptr)[5],
      ((unsigned char*) *ptr)[6],
      ((unsigned char*) *ptr)[7],
      val
      );
  *ptr += sizeof(long);
  return val;
}

float readFloat(unsigned char **ptr) {
  float val;
  memcpy(&val, *ptr, sizeof(float));
  trace(5, "parsing float: %f\n", val);
  *ptr += sizeof(float);
  return val;
}
