package boeren.com.appsuline.app.bmedical.appsuline.fragments;

import android.annotation.TargetApi;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothServerSocket;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.v4.app.FragmentManager;
import android.support.v7.app.ActionBar;
import android.support.v7.app.ActionBarActivity;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.Window;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;


import com.taidoc.pclinklibrary.android.bluetooth.util.BluetoothUtil;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import boeren.com.appsuline.app.bmedical.appsuline.NavigationDrawerFragment;
import boeren.com.appsuline.app.bmedical.appsuline.R;
import boeren.com.appsuline.app.bmedical.appsuline.constant.PCLinkLibraryDemoConstant;
import boeren.com.appsuline.app.bmedical.appsuline.utils.AEditText;
import boeren.com.appsuline.app.bmedical.appsuline.utils.GuiUtils;
import boeren.com.appsuline.app.bmedical.appsuline.utils.OnSearchBLEClickListener;
import boeren.com.appsuline.app.bmedical.appsuline.utils.SharePreferencesUtils;
import boeren.com.appsuline.app.bmedical.appsuline.views.SearchMeterDialog;
import boeren.com.appsuline.app.bmedical.appsuline.constant.PCLinkLibraryDemoEnum;

import static android.view.View.*;

/**
 * Created by Jamil on 14-9-2015.
 */
public class Meterv4fragment extends android.support.v4.app.Fragment implements OnSearchBLEClickListener {
    private static final int MESSAGE_SEARCHED_DEVICE = 0;
    private static final int MESSAGE_SCAN_DEVICE_TIMEOUT = 1;

    // views
    private ProgressBar mProgress;
    private Button mSearch;
    private ListView mListView = null;
    private RelativeLayout mRoot;
    private LinearLayout mLVBackground;
    private boolean isDualPan = false;

    private SearchMeterDialog mSearchMeterDialog;
    private boolean mScanning;
    // Stops scanning after 10 seconds.
    private static final long SCAN_PERIOD = 10000;
    private final BluetoothAdapter mAdapter;

    public boolean mAfterShowAlertDialogFlag;
    private AlertDialog alertDialog;
    Dialog dialog;
    Button btnOpen, btnCancel;

    private List<BluetoothDevice> mSearchedDevices;

    private Map<String, String> mPairedMeterNames;
    private Map<String, String> mPairedMeterAddrs;
    private Map<String, String> mBackupPairedMeterNames;
    private Map<String, String> mBackupPairedMeterAddrs;
    private int mPairedMeterCount;

    private ListAdapter mListAdapter;

    private LeAcceptThread mLeAcceptThread;
    private LeAcceptHandler mLeAcceptHandler;
    private NavigationDrawerFragment mNavigationDrawerFragment;
    // Listeners
    ViewTreeObserver.OnGlobalLayoutListener mGlobalLayoutListener = new ViewTreeObserver.OnGlobalLayoutListener() {

        @Override
        public void onGlobalLayout() {

            if (mLVBackground != null && mLVBackground.getHeight() > 0) {
                // ??event?????, ?????????
                ViewTreeObserver obs = mRoot.getViewTreeObserver();
                obs.removeGlobalOnLayoutListener(mGlobalLayoutListener);

                updateListViewHeight();
            }
        }
    };



    private Button.OnClickListener mSearchOnClickListener = new Button.OnClickListener() {
        @Override
        public void onClick(View v) {
            if (mSearch.getTag().toString().equals("0")) {
                mSearch.setTag("1");
                mSearch.setText(R.string.setting_meter_stop);
                mProgress.setVisibility(VISIBLE);

                if (mSearchedDevices.size() > 0) {
                    mSearchedDevices.clear();
                }
                mLeAcceptThread = new LeAcceptThread();
                mLeAcceptThread.start();
            }
            else {
                mLeAcceptThread.cancel();
            }
        }
    };

    public Meterv4fragment() {
        mAdapter = BluetoothUtil.getBluetoothAdapter();
        mLeAcceptHandler = new LeAcceptHandler(this);
    }

    public static Meterv4fragment newInstance() {
        Meterv4fragment f = new Meterv4fragment();
        return f;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if(getActivity().findViewById(R.id.container2) != null)isDualPan = true;
        setRetainInstance(true);
    }
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        super.onCreateView(inflater, container, savedInstanceState);
        View view = inflater.inflate(R.layout.setting_meter, container, false);


        findViews(view);
        setListeners();

        //setCancelable(false);
        mProgress.setIndeterminate(false);
        mProgress.setProgressDrawable(getResources().getDrawable(R.drawable.anim_progress));
        mProgress.setIndeterminateDrawable(getResources().getDrawable(R.drawable.anim_progress));
        mProgress.setMinimumHeight(20);

        view.setFocusableInTouchMode(true);
        view.requestFocus();

        view.setOnKeyListener(new OnKeyListener() {
            @Override
            public boolean onKey(View v, int keyCode, KeyEvent event) {
                if (event.getAction() == KeyEvent.ACTION_DOWN) {
                    if (keyCode == KeyEvent.KEYCODE_BACK) {
                        saveValue();
                        backupData();
                        getActivity().onBackPressed();
                        return true;
                    }
                }
                return false;
            }
        });
        return view;
    }
    @Override
    public void onPause(){
        super.onPause();
        saveValue();
        backupData();



    }
    @Override
    public void onStart() {
        super.onStart();

        initValue();
        backupData();
    }

    @Override
    public void onStop() {
        super.onStop();

        if (mSearchMeterDialog != null) {
            mSearchMeterDialog.dismiss();
            mSearchMeterDialog = null;
        }

        if (mSearch.getTag().toString().equals("1")) {
            mLeAcceptThread.cancel();
        }
    }

    @Override
    public void OnSearchBLEClicked(BluetoothDevice device) {
        mSearchMeterDialog = null;

        if (device != null) {
            String nameKey;
            String addrKey;
            String nameValue;
            String addrValue;

            nameKey = PCLinkLibraryDemoConstant.BLE_PAIRED_METER_NAME_ + String.valueOf(mPairedMeterCount);
            addrKey = PCLinkLibraryDemoConstant.BLE_PAIRED_METER_ADDR_ + String.valueOf(mPairedMeterCount);
            nameValue = device.getName();
            addrValue = device.getAddress();
            mPairedMeterNames.put(nameKey, nameValue);
            mPairedMeterAddrs.put(addrKey, addrValue);

            mPairedMeterCount++;
            updatePairedDevices();
            updateListViewHeight();
        }

        if (mSearch.getTag().toString().equals("1")) {
            mLeAcceptThread.cancel();
        }
    }


    private void findViews(View view) {
        mProgress = (ProgressBar) view.findViewById(R.id.pb_search);
        mSearch = (Button) view.findViewById(R.id.btn_search);
        mListView = (ListView) view.findViewById(R.id.lv_content);
        mRoot = (RelativeLayout) view.findViewById(R.id.root);
        mLVBackground = (LinearLayout) view.findViewById(R.id.lv_background);
    }

    private void setListeners() {
        mRoot.getViewTreeObserver().addOnGlobalLayoutListener(mGlobalLayoutListener);

        mSearch.setOnClickListener(mSearchOnClickListener);
    }

    private void initValue() {
        mPairedMeterCount = 0;
        mSearchedDevices = new ArrayList<BluetoothDevice>();
        mPairedMeterNames = new HashMap<String, String>();
        mPairedMeterAddrs = new HashMap<String, String>();

        String nameKey;
        String addrKey;
        String nameValue;
        String addrValue;
        for (int i=0; i<10; i++) {
            nameKey = PCLinkLibraryDemoConstant.BLE_PAIRED_METER_NAME_ + String.valueOf(i);
            addrKey = PCLinkLibraryDemoConstant.BLE_PAIRED_METER_ADDR_ + String.valueOf(i);
            nameValue = SharePreferencesUtils.getValueFromSharedPreferences(getActivity(),
                    nameKey, "").toString();
            addrValue = SharePreferencesUtils.getValueFromSharedPreferences(getActivity(),
                    addrKey, "").toString();

            if (SharePreferencesUtils.checkSharedPreferencesKey(getActivity(), nameKey)) {
                mPairedMeterNames.put(nameKey, nameValue);
                mPairedMeterAddrs.put(addrKey, addrValue);

                if (!TextUtils.isEmpty(nameValue)) {
                    mPairedMeterCount = i + 1;
                }
            }
            else {
                mPairedMeterNames.put(nameKey, "");
                mPairedMeterAddrs.put(addrKey, "");
            }
        }

        updatePairedDevices();
    }

    public static int getPairedMeters(Context context, Map<String, String> pairedMeterNames, Map<String, String> pairedMeterAddrs) {
        int pairedMeterCount = 0;
        String nameKey;
        String addrKey;
        String nameValue;
        String addrValue;
        for (int i=0; i<10; i++) {
            nameKey = PCLinkLibraryDemoConstant.BLE_PAIRED_METER_NAME_ + String.valueOf(i);
            addrKey = PCLinkLibraryDemoConstant.BLE_PAIRED_METER_ADDR_ + String.valueOf(i);
            nameValue = SharePreferencesUtils.getValueFromSharedPreferences(context,
                    nameKey, "").toString();
            addrValue = SharePreferencesUtils.getValueFromSharedPreferences(context,
                    addrKey, "").toString();

            if (SharePreferencesUtils.checkSharedPreferencesKey(context, nameKey)) {
                pairedMeterNames.put(nameKey, nameValue);
                pairedMeterAddrs.put(addrKey, addrValue);

                if (!TextUtils.isEmpty(nameValue)) {
                    pairedMeterCount = i + 1;
                }
            }
            else {
                pairedMeterNames.put(nameKey, "");
                pairedMeterAddrs.put(addrKey, "");
            }
        }

        return pairedMeterCount;
    }

    private void updatePairedDevices() {
        if (mListAdapter == null) {
            mListAdapter = new ListAdapter(getActivity(), mPairedMeterNames, mPairedMeterAddrs, mPairedMeterCount);
            mListView.setAdapter(mListAdapter);
        }
        else {
            mListAdapter.update(mPairedMeterNames, mPairedMeterAddrs, mPairedMeterCount);
            mListAdapter.notifyDataSetChanged();
        }
    }

    private void saveValue() {
        HashMap<String, String> tmpMap = new HashMap<String, String> ();

        int i=0;
        String nameKey;
        String addrKey;
        String nameValue;
        String addrValue;
        for (; i<mPairedMeterNames.size(); i++) {
            nameKey = PCLinkLibraryDemoConstant.BLE_PAIRED_METER_NAME_ + String.valueOf(i);
            addrKey = PCLinkLibraryDemoConstant.BLE_PAIRED_METER_ADDR_ + String.valueOf(i);
            nameValue = mPairedMeterNames.get(nameKey);
            addrValue = mPairedMeterAddrs.get(addrKey);

            if (!TextUtils.isEmpty(nameValue)) {
                mPairedMeterCount = i + 1;
            }

            // ??preference
            SharePreferencesUtils.setValueToSharedPreferences(getActivity(), nameKey, nameValue);
            SharePreferencesUtils.setValueToSharedPreferences(getActivity(), addrKey, addrValue);

            // ??setting db?map
            tmpMap.put(nameValue, nameValue);
            tmpMap.put(addrKey, addrValue);
        }

        backupData();
    }

    private void removeValue(int position) {
        String removeNameKey = PCLinkLibraryDemoConstant.BLE_PAIRED_METER_NAME_ + String.valueOf(position);
        String removeAddrKey = PCLinkLibraryDemoConstant.BLE_PAIRED_METER_ADDR_ + String.valueOf(position);
        mPairedMeterNames.remove(removeNameKey);
        mPairedMeterAddrs.remove(removeAddrKey);
        mPairedMeterCount--;

        String nameKey;
        String addrKey;
        String prevNameKey;
        String prevAddrKey;
        String nameValue;
        String addrValue;
        for (int i=position + 1; i<10; i++) {
            nameKey = PCLinkLibraryDemoConstant.BLE_PAIRED_METER_NAME_ + String.valueOf(i);
            addrKey = PCLinkLibraryDemoConstant.BLE_PAIRED_METER_ADDR_ + String.valueOf(i);
            prevNameKey = PCLinkLibraryDemoConstant.BLE_PAIRED_METER_NAME_ + String.valueOf(i - 1);
            prevAddrKey = PCLinkLibraryDemoConstant.BLE_PAIRED_METER_ADDR_ + String.valueOf(i - 1);

            nameValue = mPairedMeterNames.get(nameKey);
            addrValue = mPairedMeterAddrs.get(addrKey);

            mPairedMeterNames.put(prevNameKey, nameValue);
            mPairedMeterAddrs.put(prevAddrKey, addrValue);
        }

        nameKey = PCLinkLibraryDemoConstant.BLE_PAIRED_METER_NAME_ + "9";
        addrKey = PCLinkLibraryDemoConstant.BLE_PAIRED_METER_ADDR_ + "9";
        mPairedMeterNames.put(nameKey, "");
        mPairedMeterAddrs.put(addrKey, "");

        updatePairedDevices();

        updateListViewHeight();
    }

    private void updateListViewHeight() {
        if (mPairedMeterCount > 5) {
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT,
                    (int) (5.5 * (int)GuiUtils.convertTypeValueToPixel(getActivity(), 45f, TypedValue.COMPLEX_UNIT_DIP)));
            mLVBackground.setLayoutParams(params);
        }
        else if (mPairedMeterCount == 0) {
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT,
                    (int)GuiUtils.convertTypeValueToPixel(getActivity(), 45f, TypedValue.COMPLEX_UNIT_DIP));
            mLVBackground.setLayoutParams(params);
        }
        else {
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT);
            mLVBackground.setLayoutParams(params);
        }
    }

    private void updateNameValue(int position, String name) {
        String updateNameKey = PCLinkLibraryDemoConstant.BLE_PAIRED_METER_NAME_ + String.valueOf(position);
        mPairedMeterNames.put(updateNameKey, name);

        updatePairedDevices();
    }

    private void backupData() {
        if (mPairedMeterNames != null) {
            mBackupPairedMeterNames = new HashMap<String, String>();
            for (String key : mPairedMeterNames.keySet()) {
                mBackupPairedMeterNames.put(key, mPairedMeterNames.get(key));
            }
        }
        else {
            mBackupPairedMeterNames = null;
        }

        if (mPairedMeterAddrs != null) {
            mBackupPairedMeterAddrs = new HashMap<String, String>();
            for (String key : mPairedMeterAddrs.keySet()) {
                mBackupPairedMeterAddrs.put(key, mPairedMeterAddrs.get(key));
            }
        }
        else {
            mBackupPairedMeterAddrs = null;
        }
    }

    public boolean isModify() {
        if (mPairedMeterNames != null) {
            for (String key : mPairedMeterNames.keySet()) {
                if (!mPairedMeterNames.get(key).equals(mBackupPairedMeterNames.get(key))) {
                    return true;
                }
            }
        }
        else {
            if (mBackupPairedMeterNames != null) {
                return true;
            }
        }

        if (mPairedMeterAddrs != null) {
            for (String key : mPairedMeterAddrs.keySet()) {
                if (!mPairedMeterAddrs.get(key).equals(mBackupPairedMeterAddrs.get(key))) {
                    return true;
                }
            }
        }
        else {
            if (mBackupPairedMeterAddrs != null) {
                return true;
            }
        }

        return false;
    }

    private void processScanDeviceTimeout() {
        mLeAcceptThread = null;

        if (getActivity() != null) {
            if (mSearch.getTag().toString().equals("1")) {
                mSearch.setTag("0");
                mSearch.setText(R.string.meter_search);
                mProgress.setVisibility(INVISIBLE);
            }
        }
    }

    private void showSearchMeterDialog() {
        if (mSearchMeterDialog == null) {
            mSearchMeterDialog = new SearchMeterDialog(getActivity());
            mSearchMeterDialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
            mSearchMeterDialog.setCancelable(false);
            mSearchMeterDialog.setOnSearchBLEClickListener(this);
            mSearchMeterDialog.show();
        }

        mSearchMeterDialog.updateSearchedDevices(mSearchedDevices);
    }

 /*   private void showAlertDialog(final int position) {
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        int titleResId = R.string.setting_current_ble_meter;
        builder.setTitle(titleResId).setMessage("Are you sure you want to delete ?")
                .setPositiveButton(R.string.yes, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                       removeValue(position);

                    }
                });
        builder.setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                mAfterShowAlertDialogFlag = false;
            }
        });
        mAfterShowAlertDialogFlag = true;
        alertDialog = builder.show();
    }*/
   private void showAlertDialog(final int position) {

        dialog = new Dialog(getActivity(),
                android.R.style.Theme_Translucent);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setCancelable(true);
        dialog.setContentView(R.layout.layout_v4meter_dialog);
        btnCancel = (Button) dialog.findViewById(R.id.btncancel);
        btnOpen = (Button) dialog.findViewById(R.id.btnopen);
        btnCancel.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View view) {
                dialog.cancel();
            }

        });
        btnOpen.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View view) {
                removeValue(position);
                saveValue();
                backupData();
                dialog.dismiss();
            }

        });
        dialog.getWindow().setBackgroundDrawable(new ColorDrawable(0x7f000000));
        dialog.show();
    }

    private void fragmentPopUp(){
        if( getFragmentManager().getBackStackEntryCount()>0)
            getFragmentManager().popBackStack();
    }
    private class ListAdapter extends BaseAdapter {
        private LayoutInflater inflater = null;
        private Map<String, String> mNames;
        private Map<String, String> mAddrs;
        private int mCount;

        public ListAdapter(Context context, Map<String, String> names, Map<String, String> addrs, int count) {
            mNames = new HashMap<String, String>();
            mAddrs = new HashMap<String, String>();
            mNames.putAll(names);
            mAddrs.putAll(addrs);
            mCount = count;

            inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        }

        public int getCount() {
            return mCount;
        }

        public Object getItem(int position) {
            return position;
        }

        public long getItemId(int position) {
            return position;
        }

        public void update(Map<String, String> names, Map<String, String> addrs, int count) {
            mNames.putAll(names);
            mAddrs.putAll(addrs);
            mCount = count;
        }

        public View getView(final int position, View convertView, ViewGroup parent) {

            ViewHolder viewHolder = null;
            View vi = convertView;
            if(convertView == null) {
                vi = inflater.inflate(R.layout.listview_content_delete, null);
                viewHolder = new ViewHolder();
                viewHolder.tvTitle = (boeren.com.appsuline.app.bmedical.appsuline.utils.AEditText) vi.findViewById(R.id.tv_title);
                viewHolder.btnDel = (Button) vi.findViewById(R.id.btn_del);
                vi.setTag(viewHolder);
            }
            else {
                viewHolder = (ViewHolder) convertView.getTag();
            }

            String nameKey = PCLinkLibraryDemoConstant.BLE_PAIRED_METER_NAME_ + String.valueOf(position);
            final String nameValue = mNames.get(nameKey);

            viewHolder.tvTitle.setText(nameValue);

            viewHolder.btnDel.setOnClickListener(new OnClickListener() {

                @Override
                public void onClick(View v) {
                    showAlertDialog(position);
                   // removeValue(position);
                }
            });

            viewHolder.tvTitle.setOnEditTextImeBackListener(new AEditText.AEditTextListener() {

                @Override
                public void onImeBack(AEditText ctrl, String text) {
                    // ??edit text?focus
                    ctrl.setFocusable(false);
                    ctrl.setFocusableInTouchMode(false);
                    GuiUtils.setKeypadVisibility(getActivity(), ctrl, INVISIBLE);

                    String name = ctrl.getText().toString();
                    if (TextUtils.isEmpty(name)) {
                        ctrl.setText(nameValue);
                    }
                    else {
                        if (!name.equals(nameValue)) {
                            updateNameValue(position, name);
                        }
                    }
                }
            });

            viewHolder.tvTitle.setOnClickListener(mTvTitleOnClickListener);

            return vi;
        }

        OnClickListener mTvTitleOnClickListener = new OnClickListener() {

            @Override
            public void onClick(final View v) {
                if (!v.isFocused()) {
                    v.setFocusable(true);
                    v.setFocusableInTouchMode(true);
                    v.requestFocus();
                    GuiUtils.setKeypadVisibility(getActivity(), (EditText) v, VISIBLE);

                    // ????delay???event,???softkeyboard????
					/*(new Handler()).postDelayed(new Runnable() {
			            public void run() {
			            	v.dispatchTouchEvent(MotionEvent.obtain(SystemClock.uptimeMillis(), SystemClock.uptimeMillis(), MotionEvent.ACTION_DOWN , 0, 0, 0));
			            	v.dispatchTouchEvent(MotionEvent.obtain(SystemClock.uptimeMillis(), SystemClock.uptimeMillis(), MotionEvent.ACTION_UP , 0, 0, 0));
			            }
			        }, 200);*/
                }
            }
        };
    }

    private class ViewHolder {
        boeren.com.appsuline.app.bmedical.appsuline.utils.AEditText tvTitle;
        Button btnDel;
    }

    private class LeAcceptHandler extends Handler {
        private final WeakReference<Meterv4fragment> mPreference;

        /**
         * Construct a PairedMeterHandler object.
         *
         * @param preference
         *            ListViewDialog
         */
        public LeAcceptHandler(Meterv4fragment preference) {
            super();
            this.mPreference = new WeakReference<Meterv4fragment>(preference);
        }

        @Override
        public void handleMessage(Message msg) {
            Meterv4fragment preference = mPreference.get();
            if (preference != null) {
                switch (msg.what) {
                    case MESSAGE_SEARCHED_DEVICE:
                        showSearchMeterDialog();
                        break;
                    case MESSAGE_SCAN_DEVICE_TIMEOUT:
                        processScanDeviceTimeout();
                        break;
                    default:
                        break;
                }
            }
        }
    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
    private class LeAcceptThread extends Thread {
        private boolean cancel_flag;

        // Device scan callback.
        private BluetoothAdapter.LeScanCallback mLeScanCallback = new BluetoothAdapter.LeScanCallback() {

            @Override
            public void onLeScan(final BluetoothDevice device, int rssi, byte[] scanRecord) {
                boolean flag = false;
                if (device.getName() == null) {
                    return;
                }

                if (BluetoothUtil.checkMeterSeries(device.getName().toLowerCase())) {
                    flag = true;
                }
                else {
                    if (device.getName().toLowerCase().contains("test-") ||
                            device.getName().toLowerCase().contains("tng")) {
                        flag = true;
                    }
                    else if (device.getName().toLowerCase().contains("knv v125")) {
                        flag = true;
                    }
                }

                // If a connection was accepted
                if (flag) {
                    if (!mPairedMeterAddrs.containsValue(device.getAddress()) && !mSearchedDevices.contains(device)) {
                        mSearchedDevices.add(device);
                        mLeAcceptHandler.sendEmptyMessage(MESSAGE_SEARCHED_DEVICE);
                    }
                }
            }
        };

        /**
         * Constructor. Prepares a new {@link BluetoothServerSocket}.
         */
        public LeAcceptThread() {
            cancel_flag = false;
        }

        private void scanLeDevice(final boolean enable) {
            if (enable) {
                // Stops scanning after a pre-defined scan period.
                mLeAcceptHandler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        mScanning = false;
                        mAdapter.stopLeScan(mLeScanCallback);
                        if (mSearchedDevices.size() == 0) {
                            cancel();
                        }
                    }
                }, SCAN_PERIOD);

                mScanning = true;
                boolean flag = mAdapter.startLeScan(mLeScanCallback);
            }
            else {
                mScanning = false;
                mAdapter.stopLeScan(mLeScanCallback);
            }
        }
        /**
         * cancel Bluetooth connection
         */
        public void cancel() {
            if (!cancel_flag) {
                cancel_flag = true;
                mLeAcceptHandler.sendEmptyMessage(MESSAGE_SCAN_DEVICE_TIMEOUT);
                if (mAdapter != null) {
                    mAdapter.stopLeScan(mLeScanCallback);
                }
            }
        }

        /**
         * listen a connection to local device, if no connection, will terminate the socket. if
         * create a Bluetooth connection, will changed thread to {@link ConnectedThread}
         */
        public void run() {
            setName("LeAcceptThread");

            // Listen to the server socket if we're not connected
            while (true) {
                if (cancel_flag) {
                    break;
                }

                if (mAdapter != null && !mAdapter.isEnabled()) {
                    // if bluetooth was not enabled and get the exception, need to disconnect to
                    // prevent crash.
                    //listenConnectionLost();
                    break;
                }

                if (!mScanning) {
                    scanLeDevice(true);

                    try {
                        Thread.sleep(SCAN_PERIOD);
                    }
                    catch (InterruptedException e) {
                    }
                }
            } /* end of while */
        }
    }
}
