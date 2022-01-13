package gpsplus.rtkgps.settings;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceFragment;
import android.util.Log;

import javax.annotation.Nonnull;

import gpsplus.rtkgps.BuildConfig;
import gpsplus.rtkgps.MainActivity;
import gpsplus.rtkgps.R;
import gpsplus.rtklib.RtkServerSettings.TransportSettings;
import gpsplus.rtklib.constants.StreamType;

public class StreamInternalFragment extends PreferenceFragment {

    public static final String INTERNAL_SENSOR_STR = "Internal sensor";

    private static final boolean DBG = BuildConfig.DEBUG & true;

    private String mSharedPrefsName;


    public static final class Value implements TransportSettings {

        private String mPath;

        public Value() {
            mPath = null;
        }

        @Override
        public StreamType getType() {
            return StreamType.INTERNAL;
        }

        @Override
        public String getPath() {
            if (mPath == null) throw new IllegalStateException("Path not initialized. Call updatePath()");
            return mPath;
        }

        public void updatePath(Context context, String sharedPrefsName) {
            mPath = MainActivity.getLocalSocketPath(context,
                    internalLocalSocketName(sharedPrefsName)).getAbsolutePath();
        }

        @Nonnull
        public static String internalLocalSocketName(String stream) {
            return "int_" + stream; // + "_" + address.replaceAll("\\W", "_");
        }

        @Override
        public Value copy() {
            Value v = new Value();
            v.mPath = mPath;
            return v;
        }
    }

    public StreamInternalFragment() {
        super();
        mSharedPrefsName = StreamInternalFragment.class.getSimpleName();
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        final Bundle arguments;

        arguments = getArguments();
        if (arguments == null || !arguments.containsKey(StreamDialogActivity.ARG_SHARED_PREFS_NAME)) {
            throw new IllegalArgumentException("ARG_SHARED_PREFFS_NAME argument not defined");
        }

        mSharedPrefsName = arguments.getString(StreamDialogActivity.ARG_SHARED_PREFS_NAME);

        if (DBG) Log.v(mSharedPrefsName, "onCreate()");

        getPreferenceManager().setSharedPreferencesName(mSharedPrefsName);

        initPreferenceScreen();
    }



    protected void initPreferenceScreen() {
        if (DBG) Log.v(mSharedPrefsName, "initPreferenceScreen()");
        addPreferencesFromResource(R.xml.stream_internal_settings);
    }

    public static void setDefaultValue(Context ctx, String sharedPrefsName, Value value) {
        final SharedPreferences prefs;
        prefs = ctx.getSharedPreferences(sharedPrefsName, Context.MODE_PRIVATE);
        prefs
                .edit()
                .apply();
    }

    public static String readSummary(SharedPreferences prefs) {
        return INTERNAL_SENSOR_STR;
    }

    public static Value readSettings(Context context,
                                     SharedPreferences prefs, String sharedPrefsName) {
        final Value v;
        v = new Value();
        v.updatePath(context, sharedPrefsName);

        return v;
    }
}
