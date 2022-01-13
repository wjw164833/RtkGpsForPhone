package gpsplus.rtkgps;

import android.Manifest;
import android.app.Activity;
import android.app.Fragment;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.Configuration;
import android.hardware.usb.UsbManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.IBinder;
import android.os.Parcel;
import android.preference.PreferenceActivity;
import android.text.InputType;
import android.text.TextUtils;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.EditText;
import android.widget.Switch;

import androidx.annotation.RequiresApi;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.app.AlertDialog;
import androidx.drawerlayout.widget.DrawerLayout;

import com.karumi.dexter.Dexter;
import com.karumi.dexter.listener.multi.DialogOnAnyDeniedMultiplePermissionsListener;
import com.karumi.dexter.listener.multi.MultiplePermissionsListener;

import java.io.BufferedOutputStream;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;

import javax.annotation.Nonnull;

import butterknife.BindString;
import butterknife.BindView;
import butterknife.ButterKnife;
import gpsplus.ntripcaster.NTRIPCaster;
import gpsplus.rtkgps.settings.NTRIPCasterSettingsFragment;
import gpsplus.rtkgps.settings.ProcessingOptions1Fragment;
import gpsplus.rtkgps.settings.SettingsActivity;
import gpsplus.rtkgps.settings.SettingsHelper;
import gpsplus.rtkgps.settings.SolutionOutputSettingsFragment;
import gpsplus.rtkgps.settings.StreamSettingsActivity;
import gpsplus.rtkgps.utils.ChangeLog;
import gpsplus.rtkgps.utils.FileUtils;
import gpsplus.rtkgps.utils.GpsTime;

public class MainActivity extends Activity implements OnSharedPreferenceChangeListener {

    private static final boolean DBG = BuildConfig.DEBUG & true;
    //    public static final int REQUEST_LINK_TO_DBX = 2654;
    static final String TAG = MainActivity.class.getSimpleName();

    /**
     * The serialization (saved instance state) Bundle key representing the
     * current dropdown position.
     */
    private static final String STATE_SELECTED_NAVIGATION_ITEM = "selected_navigation_item";
    public static final String APP_KEY = "6ffqsgh47v9y5dc";
    public static final String APP_SECRET = "hfmsbkv4ktyl60h";
    public static final String RTKGPS_CHILD_DIRECTORY = "RtkGps/";
//    private DbxAccountManager mDbxAcctMgr;

    RtkNaviService mRtkService;
    boolean mRtkServiceBound = false;
    private static DemoModeLocation mDemoModeLocation;
    private String mSessionCode;
    String m_PointName = "POINT";
    boolean m_bRet_pointName = false;

    @BindView(R.id.drawer_layout)
    DrawerLayout mDrawerLayout;
    @BindView(R.id.navigation_drawer)
    View mNavDrawer;

    @BindView(R.id.navdraw_server_switch)
    Switch mNavDrawerServerSwitch;
    @BindView(R.id.navdraw_ntripcaster_switch)
    Switch mNavDrawerCasterSwitch;

    @BindString(R.string.permissions_request_title)
    String permissionTitle;
    @BindString(R.string.permissions_request_message)
    String permissionMessage;

    private ActionBarDrawerToggle mDrawerToggle;

    private int mNavDraverSelectedItem;
    private static String mApplicationDirectory = "";

    @RequiresApi(api = Build.VERSION_CODES.KITKAT)
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        MultiplePermissionsListener dialogMultiplePermissionsListener =
                DialogOnAnyDeniedMultiplePermissionsListener.Builder
                        .withContext(this)
                        .withTitle(permissionTitle)
                        .withMessage(permissionMessage)
                        .withButtonText(android.R.string.ok)
                        .build();
        // New Permissions request for newer Android SDK
        Dexter.withActivity(this)
                .withPermissions(
                        Manifest.permission.ACCESS_NETWORK_STATE,
                        Manifest.permission.BLUETOOTH,
                        Manifest.permission.INTERNET,
                        Manifest.permission.WAKE_LOCK,
                        Manifest.permission.ACCESS_WIFI_STATE,
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION,
                        Manifest.permission.BLUETOOTH_ADMIN,
                        Manifest.permission.WRITE_EXTERNAL_STORAGE
                ).withListener(dialogMultiplePermissionsListener)
                .check();

        PackageManager m = getPackageManager();
        String s = getPackageName();
        try {
            PackageInfo p = m.getPackageInfo(s, 0);
            MainActivity.mApplicationDirectory = p.applicationInfo.dataDir;
        } catch (NameNotFoundException e) {
            Log.w(TAG, "Error Package name not found ", e);
        }

        // copy assets/data
        try {
            copyAssetsToApplicationDirectory();
            copyAssetsToWorkingDirectory();
        } catch (FileNotFoundException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }

//        mDbxAcctMgr = DbxAccountManager.getInstance(getApplicationContext(), APP_KEY, APP_SECRET);

        mDemoModeLocation = new DemoModeLocation(this.getApplicationContext());

        setContentView(R.layout.activity_main);

        ButterKnife.bind(this);

        toggleCasterSwitch();

        createDrawerToggle();

        if (savedInstanceState == null) {
            SettingsHelper.setDefaultValues(this, true);
            proxyIfUsbAttached(getIntent());
            selectDrawerItem(R.id.navdraw_item_status);
            mDrawerLayout.openDrawer(mNavDrawer);
        }

        mNavDrawerServerSwitch.setOnCheckedChangeListener(new OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                GpsTime gpsTime = new GpsTime();
                gpsTime.setTime(System.currentTimeMillis());
                mSessionCode = String.format("%s_%s", gpsTime.getStringGpsWeek(), gpsTime.getStringGpsTOW());
                mDrawerLayout.closeDrawer(mNavDrawer);
                if (isChecked) {
                    startRtkService(mSessionCode);
                } else {
                    stopRtkService();
                }
                invalidateOptionsMenu();
            }
        });
        mNavDrawerCasterSwitch.setOnCheckedChangeListener(new OnCheckedChangeListener() {
            private NTRIPCaster mCaster = null;

            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                mDrawerLayout.closeDrawer(mNavDrawer);
                if (isChecked) {
                    if (mCaster == null) {
                        mCaster = new NTRIPCaster(getFileStorageDirectory() + "/ntripcaster/conf");
                    }
                    mCaster.start(2101, "none");
                    //TEST
                } else {
                    if (getCasterBrutalEnding()) {
                        stopRtkService();
                        int ret = mCaster.stop(1);
                        android.os.Process.killProcess(android.os.Process.myPid()); //in case of not stopping
                    } else {
                        int ret = mCaster.stop(0);
                        Log.v(TAG, "NTRIPCaster.stop(0)=" + ret);

                    }
                }
                invalidateOptionsMenu();
            }
        });

//        Log.e("path","path================>"+RtkGps.getInstance().getGnssPath());
//        FileUtils.dumpToSDCard(RtkGps.getInstance().getGnssPath(), "GnssMeasurementsEvent" + System.currentTimeMillis(), "GnssMeasurementsEvent" + System.currentTimeMillis());


        Parcel p = Parcel.obtain();
        p.writeString(new String("abc"));
        byte[] bytes = p.marshall();
        Log.e("Parcel1", p.toString() + "");
        Log.e("Parcel2", p.dataSize() + "");
        Log.e("Parcel3", p.dataAvail() + "");
        Log.e("Parcel4", p.dataCapacity() + "");
        Log.e("Parcel5", p.dataPosition() + "");
        Log.e("Parcel6", bytes.toString());
        Log.e("Parcel7", new String(bytes, StandardCharsets.UTF_8));
        Log.e("Parcel8", Util.bytesToString(bytes));
        Log.e("Parcel9", String.valueOf(p.readSerializable()));
//        FileUtils.dumpToSDCard(RtkGps.getInstance().getGnssPath(), "GNSS" + System.currentTimeMillis(), new String(bytes, StandardCharsets.UTF_8));

        ChangeLog cl = new ChangeLog(this);
        if (cl.firstRun())
            cl.getLogDialog().show();
    }

    /**
     * Java中16进制bety[](数组)与String字符串相互转换
     *
     * @author qiheo.com
     */
    public static class Util {
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

        /**
         * 16进制bety[]转换String字符串.方法二
         *
         * @param bytes
         * @return String 返回字符串有空格
         */
        public static String bytesToString2(byte[] bytes) {
            final char[] hexArray = "0123456789ABCDEF".toCharArray();
            char[] hexChars = new char[bytes.length * 2];
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < bytes.length; i++) {
                int v = bytes[i] & 0xFF;
                hexChars[i * 2] = hexArray[v >>> 4];
                hexChars[i * 2 + 1] = hexArray[v & 0x0F];
                sb.append(hexChars[i * 2]);
                sb.append(hexChars[i * 2 + 1]);
                sb.append(' ');
            }
            return sb.toString();
        }

        /**
         * 16进制bety[]转换String字符串.方法三
         *
         * @param arg
         * @return String 返回字符串有空格
         */
        private static String bytesToString3(byte[] arg) {
            String result = new String();
            if (arg != null) {
                for (int i = 0; i < arg.length; i++) {
                    result = result
                            + (Integer.toHexString(
                            arg[i] < 0 ? arg[i] + 256 : arg[i]).length() == 1 ? "0"
                            + Integer.toHexString(arg[i] < 0 ? arg[i] + 256
                            : arg[i])
                            : Integer.toHexString(arg[i] < 0 ? arg[i] + 256
                            : arg[i])) + " ";
                }
                return result;
            }
            return "";
        }

        /**
         * String字符串转换16进制bety[].方法一
         *
         * @param s
         * @return byte[]
         */
        public static byte[] stringToBytes(String s) {
            s = s.replace(" ", "");
            s = s.replace("#", "");
            byte[] baKeyword = new byte[s.length() / 2];
            for (int i = 0; i < baKeyword.length; i++) {
                try {
                    baKeyword[i] = (byte) (0xff & Integer.parseInt(s.substring(i * 2, i * 2 + 2), 16));
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            return baKeyword;
        }

        /**
         * String字符串转换16进制bety[].方法二
         *
         * @param arg
         * @return byte[]
         */
        private static byte[] stringToBytes2(String arg) {
            if (arg != null) {
                char[] NewArray = new char[1000];
                char[] array = arg.toCharArray();
                int length = 0;
                for (int i = 0; i < array.length; i++) {
                    if (array[i] != ' ') {
                        NewArray[length] = array[i];
                        length++;
                    }
                }
                int EvenLength = (length % 2 == 0) ? length : length + 1;
                if (EvenLength != 0) {
                    int[] data = new int[EvenLength];
                    data[EvenLength - 1] = 0;
                    for (int i = 0; i < length; i++) {
                        if (NewArray[i] >= '0' && NewArray[i] <= '9') {
                            data[i] = NewArray[i] - '0';
                        } else if (NewArray[i] >= 'a' && NewArray[i] <= 'f') {
                            data[i] = NewArray[i] - 'a' + 10;
                        } else if (NewArray[i] >= 'A' && NewArray[i] <= 'F') {
                            data[i] = NewArray[i] - 'A' + 10;
                        }
                    }
                    byte[] byteArray = new byte[EvenLength / 2];
                    for (int i = 0; i < EvenLength / 2; i++) {
                        byteArray[i] = (byte) (data[i * 2] * 16 + data[i * 2 + 1]);
                    }
                    return byteArray;
                }
            }
            return new byte[]{};
        }

        /**
         * String字符串转换16进制bety[].方法三
         *
         * @param str
         * @return byte[]
         */
        public static byte[] stringToBytes3(String str) {
            if (str == null || str.trim().equals("")) {
                return new byte[0];
            }
            str = str.replace(" ", "");
            byte[] bytes = new byte[str.length() / 2];
            for (int i = 0; i < str.length() / 2; i++) {
                String subStr = str.substring(i * 2, i * 2 + 2);
                bytes[i] = (byte) Integer.parseInt(subStr, 16);
            }
            return bytes;
        }

        /**
         * String字符串转换16进制bety[].方法四
         *
         * @param hex
         * @return byte[]
         */
        public static byte[] stringToBytes4(String hex) {
            hex = hex.replace(" ", "");
            if ((hex == null) || (hex.equals(""))) {
                return null;
            } else if (hex.length() % 2 != 0) {
                return null;
            } else {
                hex = hex.toUpperCase();
                int len = hex.length() / 2;
                byte[] b = new byte[len];
                char[] hc = hex.toCharArray();
                for (int i = 0; i < len; i++) {
                    int p = 2 * i;
                    b[i] = (byte) (charToByte(hc[p]) << 4 | charToByte(hc[p + 1]));
                }
                return b;
            }
        }

        /**
         * char进制转换
         *
         * @param c
         * @return byte
         */
        private static byte charToByte(char c) {
            return (byte) "0123456789ABCDEF".indexOf(c);
        }
    }

    private void toggleCasterSwitch() {
        SharedPreferences casterSolution = getSharedPreferences(NTRIPCasterSettingsFragment.SHARED_PREFS_NAME, 0);
        boolean bIsCasterEnabled = casterSolution.getBoolean(NTRIPCasterSettingsFragment.KEY_ENABLE_CASTER, false);
        mNavDrawerCasterSwitch.setEnabled(bIsCasterEnabled);
    }

    private boolean getCasterBrutalEnding() {
        SharedPreferences casterSolution = getSharedPreferences(NTRIPCasterSettingsFragment.SHARED_PREFS_NAME, 0);
        return casterSolution.getBoolean(NTRIPCasterSettingsFragment.KEY_BRUTAL_ENDING_CASTER, true);
    }

    public static DemoModeLocation getDemoModeLocation() {
        return mDemoModeLocation;
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (!mRtkServiceBound) {
            final Intent intent = new Intent(this, RtkNaviService.class);
            bindService(intent, mConnection, Context.BIND_AUTO_CREATE);
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        proxyIfUsbAttached(intent);
    }

    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
        mDrawerToggle.syncState();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        mDrawerToggle.onConfigurationChanged(newConfig);
    }

    @Override
    protected void onStop() {
        super.onStop();

        // Unbind from the service
        if (mRtkServiceBound) {
            unbindService(mConnection);
            mRtkServiceBound = false;
            mRtkService = null;
        }

    }

    @Override
    public void onRestoreInstanceState(Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        if (savedInstanceState.containsKey(STATE_SELECTED_NAVIGATION_ITEM)) {
            mNavDraverSelectedItem = savedInstanceState.getInt(STATE_SELECTED_NAVIGATION_ITEM);
            setNavDrawerItemChecked(mNavDraverSelectedItem);
        }
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        if (mNavDraverSelectedItem != 0) {
            outState.putInt(STATE_SELECTED_NAVIGATION_ITEM, mNavDraverSelectedItem);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.activity_main, menu);
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        boolean serviceActive = mNavDrawerServerSwitch.isChecked();
        menu.findItem(R.id.menu_start_service).setVisible(!serviceActive);
        menu.findItem(R.id.menu_stop_service).setVisible(serviceActive);
        menu.findItem(R.id.menu_add_point).setVisible(serviceActive);
        menu.findItem(R.id.menu_tools).setVisible(true);
//        if (mDbxAcctMgr.hasLinkedAccount())
//        {
//            menu.findItem(R.id.menu_dropbox).setVisible(false);
//        }
        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {

        if (mDrawerToggle.onOptionsItemSelected(item)) {
            return true;
        }

        switch (item.getItemId()) {
            case R.id.menu_start_service:
                mNavDrawerServerSwitch.setChecked(true);
                break;
            case R.id.menu_stop_service:
                mNavDrawerServerSwitch.setChecked(false);
                break;
            case R.id.menu_add_point:
                askToAddPointToCrw();
                break;
            case R.id.menu_tools:
                startActivity(new Intent(this, ToolsActivity.class));
                break;
//        case R.id.menu_dropbox:
//            mDbxAcctMgr.startLink(this, REQUEST_LINK_TO_DBX);
//            break;
            case R.id.menu_settings:
                mDrawerLayout.openDrawer(mNavDrawer);
                break;
            case R.id.menu_about:
                startActivity(new Intent(this, AboutActivity.class));
                break;
            default:
                return super.onOptionsItemSelected(item);
        }
        return true;
    }

    private boolean askForPointName() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this, R.style.PointNameAlertDialogStyle);
        builder.setTitle(R.string.point_name_input_title);


        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        builder.setView(input);

        builder.setPositiveButton(R.string.button_ok, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                m_PointName = input.getText().toString();
                m_bRet_pointName = true;
            }
        });
        builder.setNegativeButton(R.string.button_cancel, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                m_bRet_pointName = false;
                dialog.cancel();
            }
        });

        builder.show();
        return m_bRet_pointName;
    }

    private void askToAddPointToCrw() {
        if (askForPointName()) {
            final Intent intent = new Intent(RtkNaviService.ACTION_STORE_POINT);
            intent.setClass(this, RtkNaviService.class);
            intent.putExtra(RtkNaviService.EXTRA_POINT_NAME, m_PointName);
            startService(intent);
        }
    }

    private void copyAssetsDirToApplicationDirectory(String sourceDir, File destDir) throws FileNotFoundException, IOException {
        //copy assets/data to appdir/data
        java.io.InputStream stream = null;
        java.io.OutputStream output = null;

        for (String fileName : this.getAssets().list(sourceDir)) {
            stream = this.getAssets().open(sourceDir + File.separator + fileName);
            String dest = destDir + File.separator + sourceDir + File.separator + fileName;
            File fdest = new File(dest);
            if (fdest.exists()) continue;

            File fpdestDir = new File(fdest.getParent());
            if (!fpdestDir.exists()) fpdestDir.mkdirs();

            output = new BufferedOutputStream(new FileOutputStream(dest));

            byte data[] = new byte[1024];
            int count;

            while ((count = stream.read(data)) != -1) {
                output.write(data, 0, count);
            }

            output.flush();
            output.close();
            stream.close();

            stream = null;
            output = null;
        }
    }

    private void copyAssetsToApplicationDirectory() throws FileNotFoundException, IOException {
        copyAssetsDirToApplicationDirectory("data", this.getFilesDir());
        copyAssetsDirToApplicationDirectory("proj4", this.getFilesDir());
    }

    private void copyAssetsToWorkingDirectory() throws FileNotFoundException, IOException {
        copyAssetsDirToApplicationDirectory("ntripcaster", getFileStorageDirectory());
    }

    private void proxyIfUsbAttached(Intent intent) {

        if (intent == null) return;

        if (!UsbManager.ACTION_USB_DEVICE_ATTACHED.equals(intent.getAction())) return;

        if (DBG) Log.v(TAG, "usb device attached");

        final Intent proxyIntent = new Intent(UsbToRtklib.ACTION_USB_DEVICE_ATTACHED);
        proxyIntent.putExtras(intent.getExtras());
        sendBroadcast(proxyIntent);
    }

    private void createDrawerToggle() {
        mDrawerToggle = new ActionBarDrawerToggle(
                this,
                mDrawerLayout,
                /*R.drawable.ic_drawer,*/
                R.string.drawer_open,
                R.string.drawer_close
        ) {
            @Override
            public void onDrawerClosed(View view) {
                //getActionBar().setTitle(mTitle);
            }

            @Override
            public void onDrawerOpened(View drawerView) {
                //getActionBar().setTitle(mDrawerTitle);
            }

        };
    }

    private void selectDrawerItem(int itemId) {
        switch (itemId) {
            case R.id.navdraw_item_status:
            case R.id.navdraw_item_map:
                setNavDrawerItemFragment(itemId);
                break;
            case R.id.navdraw_item_input_streams:
                showInputStreamSettings();
                break;
            case R.id.navdraw_item_output_streams:
                showOutputStreamSettings();
                break;
            case R.id.navdraw_item_log_streams:
                showLogStreamSettings();
                break;
            case R.id.navdraw_item_processing_options:
            case R.id.navdraw_item_solution_options:
            case R.id.navdraw_item_ntripcaster_options:
                showSettings(itemId);
                break;
            default:
                throw new IllegalStateException();
        }
    }

    private void setNavDrawerItemFragment(int itemId) {
        Fragment fragment;
        mDrawerLayout.closeDrawer(mNavDrawer);

        if (mNavDraverSelectedItem == itemId) {
            return;
        }

        switch (itemId) {
            case R.id.navdraw_item_status:
                fragment = new StatusFragment();
                break;
            case R.id.navdraw_item_map:
                fragment = new MapFragment();
                break;
            default:
                throw new IllegalArgumentException();
        }

        getFragmentManager()
                .beginTransaction()
                .replace(R.id.container, fragment)
                .commit();
        setNavDrawerItemChecked(itemId);
    }

    private void setNavDrawerItemChecked(int itemId) {
        final int[] items = new int[]{
                R.id.navdraw_item_status,
                R.id.navdraw_item_input_streams,
                R.id.navdraw_item_output_streams,
                R.id.navdraw_item_log_streams,
                R.id.navdraw_item_solution_options,
                R.id.navdraw_item_solution_options
        };

        for (int i : items) {
            mNavDrawer.findViewById(i).setActivated(itemId == i);
        }
        mNavDraverSelectedItem = itemId;
    }

    private void refreshServiceSwitchStatus() {
        boolean serviceActive = mRtkServiceBound && (mRtkService.isServiceStarted());
        mNavDrawerServerSwitch.setChecked(serviceActive);
    }

    private void startRtkService(String sessionCode) {
        mSessionCode = sessionCode;
        final Intent rtkServiceIntent = new Intent(RtkNaviService.ACTION_START);
        rtkServiceIntent.putExtra(RtkNaviService.EXTRA_SESSION_CODE, mSessionCode);
        rtkServiceIntent.setClass(this, RtkNaviService.class);
        startService(rtkServiceIntent);
    }

    public String getSessionCode() {
        return mSessionCode;
    }

    private void stopRtkService() {
        final Intent intent = new Intent(RtkNaviService.ACTION_STOP);
        intent.setClass(this, RtkNaviService.class);
        startService(intent);
    }

    public RtkNaviService getRtkService() {
        return mRtkService;
    }

    private void showSettings(int itemId) {
        final Intent intent = new Intent(this, SettingsActivity.class);
        switch (itemId) {
            case R.id.navdraw_item_processing_options:
                intent.putExtra(PreferenceActivity.EXTRA_SHOW_FRAGMENT,
                        ProcessingOptions1Fragment.class.getName());
                break;
            case R.id.navdraw_item_solution_options:
                intent.putExtra(PreferenceActivity.EXTRA_SHOW_FRAGMENT,
                        SolutionOutputSettingsFragment.class.getName());
                break;
            case R.id.navdraw_item_ntripcaster_options:
                intent.putExtra(PreferenceActivity.EXTRA_SHOW_FRAGMENT,
                        NTRIPCasterSettingsFragment.class.getName());
                break;
            default:
                throw new IllegalStateException();
        }
        startActivity(intent);
    }

    private void showInputStreamSettings() {
        final Intent intent = new Intent(this, StreamSettingsActivity.class);
        intent.putExtra(StreamSettingsActivity.ARG_STEAM,
                StreamSettingsActivity.STREAM_INPUT_SETTINGS);
        startActivity(intent);
    }

    private void showOutputStreamSettings() {
        final Intent intent = new Intent(this, StreamSettingsActivity.class);
        intent.putExtra(StreamSettingsActivity.ARG_STEAM,
                StreamSettingsActivity.STREAM_OUTPUT_SETTINGS);
        startActivity(intent);
    }

    private void showLogStreamSettings() {
        final Intent intent = new Intent(this, StreamSettingsActivity.class);
        intent.putExtra(StreamSettingsActivity.ARG_STEAM,
                StreamSettingsActivity.STREAM_LOG_SETTINGS);
        startActivity(intent);
    }


    public void onNavDrawevItemClicked(View v) {
        selectDrawerItem(v.getId());
    }

    private ServiceConnection mConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName className, IBinder service) {
            // We've bound to LocalService, cast the IBinder and get
            // LocalService instance
            RtkNaviService.RtkNaviServiceBinder binder = (RtkNaviService.RtkNaviServiceBinder) service;
            mRtkService = binder.getService();
            mRtkServiceBound = true;
            refreshServiceSwitchStatus();
            invalidateOptionsMenu();
        }

        @Override
        public void onServiceDisconnected(ComponentName arg0) {
            mRtkServiceBound = false;
            mRtkService = null;
            refreshServiceSwitchStatus();
            invalidateOptionsMenu();
        }
    };

    @Nonnull
    public static File getFileStorageDirectory() {
        File externalLocation = new File(Environment.getExternalStorageDirectory(), RTKGPS_CHILD_DIRECTORY);
        if (!externalLocation.isDirectory()) {
            if (externalLocation.mkdirs()) {
                Log.v(TAG, "Local storage created on external card");
            } else {
                externalLocation = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), RTKGPS_CHILD_DIRECTORY);
                if (!externalLocation.isDirectory()) {
                    if (externalLocation.mkdirs()) {
                        Log.v(TAG, "Local storage created on public storage");
                    } else {
                        externalLocation = new File(Environment.getDownloadCacheDirectory(), RTKGPS_CHILD_DIRECTORY);
                        if (!externalLocation.isDirectory()) {
                            if (externalLocation.mkdirs()) {
                                Log.v(TAG, "Local storage created on cache directory");
                            } else {
                                externalLocation = new File(Environment.getDataDirectory(), RTKGPS_CHILD_DIRECTORY);
                                if (!externalLocation.isDirectory()) {
                                    if (externalLocation.mkdirs()) {
                                        Log.v(TAG, "Local storage created on data storage");
                                    } else {
                                        Log.e(TAG, "NO WAY TO CREATE FILE SOTRAGE?????");
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        return externalLocation;
    }

    @Nonnull
    public static File getFileInStorageDirectory(String nameWithExtension) {
        return new File(Environment.getExternalStorageDirectory(), RTKGPS_CHILD_DIRECTORY + nameWithExtension);
    }

    public static String getAndCheckSessionDirectory(String code) {
        String szSessionDirectory = MainActivity.getFileStorageDirectory() + File.separator + code;
        File fsessionDirectory = new File(szSessionDirectory);
        if (!fsessionDirectory.exists()) {
            fsessionDirectory.mkdirs();
        }
        return szSessionDirectory;
    }

    public static File getFileInStorageSessionDirectory(String code, String nameWithExtension) {
        String szSessionDirectory = MainActivity.getAndCheckSessionDirectory(code);
        return new File(szSessionDirectory + File.separator + nameWithExtension);
    }


    @Nonnull
    public static File getLocalSocketPath(Context ctx, String socketName) {
        return ctx.getFileStreamPath(socketName);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
//        if (requestCode == REQUEST_LINK_TO_DBX) {
//            if (resultCode == Activity.RESULT_OK) {
        // ... Start using Dropbox files.
//            } else {
        // ... Link failed or was cancelled by the user.
        //           }
        //       } else {
        super.onActivityResult(requestCode, resultCode, data);
        //       }
    }

    public static String getApplicationDirectory() {
        return MainActivity.mApplicationDirectory;
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences prefs, String key) {
        if (key.equalsIgnoreCase(NTRIPCasterSettingsFragment.KEY_ENABLE_CASTER)) {
            toggleCasterSwitch();
        }

    }

    @Override
    protected void onPause() {
        super.onPause();
        getSharedPreferences(NTRIPCasterSettingsFragment.SHARED_PREFS_NAME, MODE_PRIVATE).registerOnSharedPreferenceChangeListener(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        getSharedPreferences(NTRIPCasterSettingsFragment.SHARED_PREFS_NAME, MODE_PRIVATE).registerOnSharedPreferenceChangeListener(this);
    }

}
