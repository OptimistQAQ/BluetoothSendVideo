package com.example.ipcamera;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Bundle;

import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import java.util.Set;

/**
 * @author 65667
 */
public class BluetoothActivity extends AppCompatActivity {
    private BluetoothAdapter mBtAdapter;
    private ArrayAdapter<String> mPairedDevicesArrayAdapter;
    private Set<BluetoothDevice> pairedDevices;
    private Button scanButton;
    private SharedPreferences mSp;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        int listId = getIntent().getIntExtra("layout_list", R.layout.device_list);
        setContentView(listId);
        String strBluetoothDevices = getIntent().getStringExtra("bluetooth_devices");
        if (strBluetoothDevices == null)
            strBluetoothDevices = "Bluetooth Devices";
        setTitle(strBluetoothDevices);
        setResult(Activity.RESULT_CANCELED);
        scanButton = (Button) findViewById(R.id.button_scan);
        String strScanDevice = getIntent().getStringExtra("scan_for_devices");
        if (strScanDevice == null)
            strScanDevice = "SCAN FOR DEVICES";
        scanButton.setText("搜索");
        scanButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                doDiscovery();
            }
        });

        int layout_text = getIntent().getIntExtra("layout_text", R.layout.device_name);
        mPairedDevicesArrayAdapter = new ArrayAdapter<String>(this, layout_text);

        ListView pairedListView = (ListView) findViewById(R.id.list_devices);
        pairedListView.setAdapter(mPairedDevicesArrayAdapter);
        pairedListView.setOnItemClickListener(mDeviceClickListener);
        IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_FOUND);
        this.registerReceiver(mReceiver, filter);

        filter = new IntentFilter(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
        this.registerReceiver(mReceiver, filter);
        mBtAdapter = BluetoothAdapter.getDefaultAdapter();
        pairedDevices = mBtAdapter.getBondedDevices();
        if (pairedDevices.size() > 0) {
            for (BluetoothDevice device : pairedDevices) {
                mPairedDevicesArrayAdapter.add(device.getName() + "\n" + device.getAddress());
            }
        } else {
            String noDevices = "No devices found";
            mPairedDevicesArrayAdapter.add(noDevices);
        }
    }

    @Override
    protected void onStart() {
        super.onStart();

    }

    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();

            //当发现蓝牙设备
            if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                if (device.getBondState() != BluetoothDevice.BOND_BONDED) {
                    String strNoFound = getIntent().getStringExtra("no_devices_found");
                    if (strNoFound == null)
                        strNoFound = "No devices found";

                    if (mPairedDevicesArrayAdapter.getItem(0).equals(strNoFound)) {
                        mPairedDevicesArrayAdapter.remove(strNoFound);
                    }
                    mPairedDevicesArrayAdapter.add(device.getName() + "\n" + device.getAddress());
                }

            } else if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(action)) {
                setProgressBarIndeterminateVisibility(false);
                String strSelectDevice = getIntent().getStringExtra("select_device");
                if (strSelectDevice == null)
                    strSelectDevice = "Select a device to connect";
                setTitle(strSelectDevice);
            }
        }
    };

    //携带蓝牙地址返回主界面
    private AdapterView.OnItemClickListener mDeviceClickListener = new AdapterView.OnItemClickListener() {
        public void onItemClick(AdapterView<?> av, View v, int arg2, long arg3) {
            if (mBtAdapter.isDiscovering())
                mBtAdapter.cancelDiscovery();

            String strNoFound = getIntent().getStringExtra("no_devices_found");
            if (strNoFound == null)
                strNoFound = "No devices found";
            if (!((TextView) v).getText().toString().equals(strNoFound)) {
                String info = ((TextView) v).getText().toString();
                String address = info.substring(info.length() - 17);
                Intent intent = new Intent();
                intent.putExtra(BluetoothState.EXTRA_DEVICE_ADDRESS, address);
                setResult(Activity.RESULT_OK, intent);
                finish();
            }
        }
    };

    //扫描蓝牙设备
    private void doDiscovery() {
        mPairedDevicesArrayAdapter.clear();
        if (pairedDevices.size() > 0) {
            for (BluetoothDevice device : pairedDevices) {
                mPairedDevicesArrayAdapter.add(device.getName() + "\n" + device.getAddress());
            }
        } else {
            String strNoFound = getIntent().getStringExtra("no_devices_found");
            if (strNoFound == null)
                strNoFound = "No devices found";
            mPairedDevicesArrayAdapter.add(strNoFound);
        }

        String strScanning = getIntent().getStringExtra("scanning");
        if (strScanning == null)
            strScanning = "Scanning for devices...";
        setProgressBarIndeterminateVisibility(true);
        setTitle(strScanning);

        if (mBtAdapter.isDiscovering()) {
            mBtAdapter.cancelDiscovery();
        }
        mBtAdapter.startDiscovery();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mBtAdapter != null) {
            mBtAdapter.cancelDiscovery();
        }
        this.unregisterReceiver(mReceiver);
        this.finish();
    }
}
