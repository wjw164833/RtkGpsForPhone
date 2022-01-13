package gpsplus.rtkgps;

import android.content.Context;
import android.location.GnssClock;
import android.location.GnssMeasurement;
import android.location.GnssMeasurementsEvent;
import android.location.GpsSatellite;
import android.location.GpsStatus;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Parcel;
import android.util.Log;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Collection;
import java.util.Iterator;

import javax.annotation.Nonnull;

import gpsplus.rtkgps.settings.StreamInternalFragment.Value;

@SuppressWarnings("ALL")
public class InternalReceiverToRtklib implements GpsStatus.Listener, LocationListener {

    private static final boolean DBG = BuildConfig.DEBUG & true;
    static final String TAG = InternalReceiverToRtklib.class.getSimpleName();

    final LocalSocketThread mLocalSocketThread;
    private static Context mParentContext = null;
    private Value mInternalReceiverSettings;
    LocationManager locationManager = null;
    FileOutputStream autoCaptureFileOutputStream = null;
    File autoCaptureFile = null;
    private int nbSat = 0;
    private boolean isStarting = false;
    private String mSessionCode;
    private int rawMeasurementStatus;

    public InternalReceiverToRtklib(Context parentContext, @Nonnull Value internalReceiverSettings, String sessionCode) {
        InternalReceiverToRtklib.mParentContext = parentContext;
        mSessionCode = sessionCode;
        this.mInternalReceiverSettings = internalReceiverSettings;
        mLocalSocketThread = new LocalSocketThread(mInternalReceiverSettings.getPath());
        mLocalSocketThread.setBindpoint(mInternalReceiverSettings.getPath());
    }

    public void start() {
        isStarting = true;
        locationManager = (LocationManager) mParentContext.getSystemService(Context.LOCATION_SERVICE);
        locationManager.addGpsStatusListener(this);
        locationManager.requestLocationUpdates(
                LocationManager.GPS_PROVIDER, 1000, 0.5f, this);

        locationManager.registerGnssMeasurementsCallback(mRawMeasurementListener);

    }

    public void stop() {
        locationManager.removeUpdates(this);
        locationManager.unregisterGnssMeasurementsCallback(mRawMeasurementListener);
        mLocalSocketThread.cancel();
    }


    public boolean isRawMeasurementsSupported() {
        return (rawMeasurementStatus == GnssMeasurementsEvent.Callback.STATUS_READY);
    }

    private String isNaN(double number) {
        return Double.isNaN(number) ? "" : number + "";
    }

    private final GnssMeasurementsEvent.Callback mRawMeasurementListener =
            new GnssMeasurementsEvent.Callback() {
                @Override
                public void onStatusChanged(int status) {
                    Log.e("resuslt", "onStatusChanged===============>");
                    rawMeasurementStatus = status;
                }

                @Override
                public void onGnssMeasurementsReceived(GnssMeasurementsEvent event) {
                    Log.e("resuslt", "onGnssMeasurementsReceived===============>");
                    if (isStarting) // run only if starting
                    {
                        Log.i(TAG, "Starting streaming from internal receiver");
                        mLocalSocketThread.start();
                        isStarting = false;
                    }

                    GnssClock c = event.getClock();
                    Collection<GnssMeasurement> measurements = event.getMeasurements();

                    Parcel p = Parcel.obtain();
                    Parcel p1 = Parcel.obtain();
                    try {
                        // byte[] syncWord = {0x00, 0x00};
                        // p.writeByteArray(syncWord);

                        p.writeDouble(c.getBiasNanos());
                        p.writeDouble(c.getBiasUncertaintyNanos());
                        p.writeDouble(c.getDriftNanosPerSecond());
                        p.writeDouble(c.getDriftUncertaintyNanosPerSecond());
                        p.writeLong(c.getFullBiasNanos());
                        p.writeInt(c.getHardwareClockDiscontinuityCount());
                        p.writeInt(c.getLeapSecond());
                        p.writeLong(c.getTimeNanos());
                        p.writeDouble(c.getTimeUncertaintyNanos());
                        p.writeInt(c.hasBiasNanos() ? 1 : 0);
                        p.writeInt(c.hasBiasUncertaintyNanos() ? 1 : 0);
                        p.writeInt(c.hasDriftNanosPerSecond() ? 1 : 0);
                        p.writeInt(c.hasDriftUncertaintyNanosPerSecond() ? 1 : 0);
                        p.writeInt(c.hasFullBiasNanos() ? 1 : 0);
                        p.writeInt(c.hasLeapSecond() ? 1 : 0);
                        p.writeInt(c.hasTimeUncertaintyNanos() ? 1 : 0);
                        p.writeInt(measurements.size());

                        for (GnssMeasurement m : measurements) {
                            p.writeDouble(m.getAccumulatedDeltaRangeMeters());
                            p.writeInt(m.getAccumulatedDeltaRangeState());
                            p.writeDouble(m.getAccumulatedDeltaRangeUncertaintyMeters());
                            p.writeDouble(m.getAutomaticGainControlLevelDb());
                            p.writeLong(m.getCarrierCycles());
                            p.writeFloat(m.getCarrierFrequencyHz());
                            p.writeDouble(m.getCarrierPhase());
                            p.writeDouble(m.getCarrierPhaseUncertainty());
                            p.writeDouble(m.getCn0DbHz());
                            p.writeInt(m.getConstellationType());
                            p.writeInt(m.getMultipathIndicator());
                            p.writeDouble(m.getPseudorangeRateUncertaintyMetersPerSecond());
                            p.writeLong(m.getReceivedSvTimeNanos());
                            p.writeLong(m.getReceivedSvTimeUncertaintyNanos());
                            p.writeDouble(m.getSnrInDb());
                            p.writeInt(m.getState());
                            p.writeInt(m.getSvid());
                            p.writeDouble(m.getTimeOffsetNanos());
                            p.writeInt(m.hasAutomaticGainControlLevelDb() ? 1 : 0);
                            p.writeInt(m.hasCarrierCycles() ? 1 : 0);
                            p.writeInt(m.hasCarrierFrequencyHz() ? 1 : 0);
                            p.writeInt(m.hasCarrierPhase() ? 1 : 0);
                            p.writeInt(m.hasCarrierPhaseUncertainty() ? 1 : 0);
                            p.writeInt(m.hasSnrInDb() ? 1 : 0);
                        }

                        /*Raw, utcTimeMillis, TimeNanos, LeapSecond, TimeUncertaintyNanos, FullBiasNanos, BiasNanos,
                                BiasUncertaintyNanos, DriftNanosPerSecond, DriftUncertaintyNanosPerSecond,
                                HardwareClockDiscontinuityCount, Svid, TimeOffsetNanos, State, ReceivedSvTimeNanos,
                                ReceivedSvTimeUncertaintyNanos, Cn0DbHz, PseudorangeRateMetersPerSecond,
                                PseudorangeRateUncertaintyMetersPerSecond, AccumulatedDeltaRangeState, AccumulatedDeltaRangeMeters,
                                AccumulatedDeltaRangeUncertaintyMeters, CarrierFrequencyHz, CarrierCycles, CarrierPhase,
                                CarrierPhaseUncertainty, MultipathIndicator, SnrInDb, ConstellationType, AgcDb, BasebandCn0DbHz,
                                FullInterSignalBiasNanos, FullInterSignalBiasUncertaintyNanos, SatelliteInterSignalBiasNanos,
                                SatelliteInterSignalBiasUncertaintyNanos, CodeType, ChipsetElapsedRealtimeNanos*/
                        StringBuilder sb = new StringBuilder("Raw,");
                        //Raw
//                        sb.append("Raw,");
                        //utcTimeMillis
                        sb.append("1625472193999,");
                        //TimeNanos
                        sb.append("582000000000,");
                        //LeapSecond
                        sb.append("18,");
                        //TimeUncertaintyNanos
                        sb.append("0.0,");
                        //FullBiasNanos
                        sb.append("-1309506829999934208,");
                        //BiasNanos
                        sb.append("0.0,");
                        //BiasUncertaintyNanos
                        sb.append("333.564095198152,");
                        //DriftNanosPerSecond
                        sb.append("125.06177259403403,");
                        //DriftUncertaintyNanosPerSecond
                        sb.append("0.0,");
                        //HardwareClockDiscontinuityCount
                        sb.append("0,");
                        //Svid
                        sb.append("36,");
                        //TimeOffsetNanos
                        sb.append("0.0,");
                        //State
                        sb.append("23560,");
                        //ReceivedSvTimeNanos
                        sb.append("115411912454492,");
                        //ReceivedSvTimeUncertaintyNanos
                        sb.append("5,");
                        //Cn0DbHz
                        sb.append("42.0,");
                        //PseudorangeRateMetersPerSecond
                        sb.append("308.41229041070113,");
                        //PseudorangeRateUncertaintyMetersPerSecond
                        sb.append("0.01,");
                        //AccumulatedDeltaRangeState
                        sb.append("25,");
                        //AccumulatedDeltaRangeMeters
                        sb.append("1540.8472240536496,");
                        //AccumulatedDeltaRangeUncertaintyMeters
                        sb.append("0.1,");
                        //CarrierFrequencyHz
                        sb.append("1.57542003E9,");
                        //CarrierCycles
                        sb.append(",");
                        //CarrierPhase
                        sb.append(",");
                        //CarrierPhaseUncertainty
                        sb.append(",");
                        //MultipathIndicator
                        sb.append("2,");
                        //SnrInDb
                        sb.append(",");
                        //ConstellationType
                        sb.append("6,");
                        //AgcDb
                        sb.append("26.0,");
                        //BasebandCn0DbHz
                        sb.append(",");
                        //FullInterSignalBiasNanos
                        sb.append(",");
                        //FullInterSignalBiasUncertaintyNanos
                        sb.append(",");
                        //SatelliteInterSignalBiasNanos
                        sb.append(",");
                        //SatelliteInterSignalBiasUncertaintyNanos
                        sb.append(",");
                        //CodeType
                        sb.append("A,");
                        //ChipsetElapsedRealtimeNanos
                        sb.append("1689260931440522");

//                        byte[] packet = p.marshall();
                        p1.writeString(sb.toString());
                        byte[] packet = p1.marshall();

                        mLocalSocketThread.write(packet, 0, packet.length);
                    } catch (IOException e) {
                        e.printStackTrace();
                    } finally {
                        p.recycle();
                        p1.recycle();
                    }
                }

            };

    /**
     * 16进制bety[]转换String字符串.方法一
     *
     * @param data
     * @return String 返回字符串无空格
     */
    public static String bytesToString(byte[] data) {
        char[] hexArray = "0123456789ABCDEF".toCharArray();
        char[] hexChars = new char[data.length * 2];
        for (int j = 0; j < data.length; j++) {
            int v = data[j] & 0xFF;
            hexChars[j * 2] = hexArray[v >>> 4];
            hexChars[j * 2 + 1] = hexArray[v & 0x0F];
        }
        String result = new String(hexChars);
        return result;
    }

    @Override
    public void onGpsStatusChanged(int i) {
        GpsStatus gpsStatus = locationManager.getGpsStatus(null);
        if (DBG) {
            Log.d(TAG, "GPS Status changed");
        }
        Log.e("gpsStatus", gpsStatus + "");
        if (gpsStatus != null) {

            Iterable<GpsSatellite> satellites = gpsStatus.getSatellites();
            Iterator<GpsSatellite> sat = satellites.iterator();
            nbSat = 0;
            while (sat.hasNext()) {
                Log.e("gpsStatus", ".........1");
                GpsSatellite satellite = sat.next();
                if (satellite.usedInFix()) {
                    Log.e("gpsStatus", ".........2");
                    nbSat++;
                    Log.d(TAG, "PRN:" + satellite.getPrn() + ", SNR:" + satellite.getSnr() + ", AZI:" + satellite.getAzimuth() + ", ELE:" + satellite.getElevation());
                }
            }
        }
    }

    @Override
    public void onLocationChanged(Location location) {

    }

    @Override
    public void onStatusChanged(String s, int i, Bundle bundle) {

    }

    @Override
    public void onProviderEnabled(String s) {

    }

    @Override
    public void onProviderDisabled(String s) {

    }

    private final class LocalSocketThread extends RtklibLocalSocketThread {

        public LocalSocketThread(String socketPath) {
            super(socketPath);
        }

        @Override
        protected boolean isDeviceReady() {
            return isRawMeasurementsSupported();
        }

        @Override
        protected void waitDevice() {

        }

        @Override
        protected boolean onDataReceived(byte[] buffer, int offset, int count) {
            /*if (count <= 0) return true;
                   PoGoPin.writeDevice(BytesTool.get(buffer,offset), count);
                   */
            return true;
        }

        @Override
        protected void onLocalSocketConnected() {

        }
    }
}