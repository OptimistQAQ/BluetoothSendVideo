/*
 * Copyright (C) 2014 Akexorcist
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.ipcamera;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.widget.Toast;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.util.ArrayList;
import java.util.Set;

@SuppressLint("NewApi")
public class BluetoothUtil {
    private BluetoothStateListener mBluetoothStateListener = null;
    private OnDataReceivedListener mDataReceivedListener = null;
    private BluetoothConnectionListener mBluetoothConnectionListener = null;
    private AutoConnectionListener mAutoConnectionListener = null;
    private Context mContext;
    private BluetoothAdapter mBluetoothAdapter = null;
    // Member object for the chat services
    private BluetoothService mChatService = null;

    // Name and Address of the connected device
    private String mDeviceName = null;
    private String mDeviceAddress = null;

    private boolean isAutoConnecting = false;
    private boolean isAutoConnectionEnabled = false;
    private boolean isConnected = false;
    private boolean isConnecting = false;
    private boolean isServiceRunning = false;

    private String keyword = "";
    private boolean isAndroid = BluetoothState.DEVICE_ANDROID;

    private BluetoothConnectionListener bcl;
    private int c = 0;

    private static int headInfoLength = 14;

    public BluetoothUtil(Context context) {
        mContext = context;
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
    }

    public interface BluetoothStateListener {
        public void onServiceStateChanged(int state);
    }

    public interface OnDataReceivedListener {
        public void onDataReceived(byte[] data, String message);
    }

    public interface BluetoothConnectionListener {
        public void onDeviceConnected(String name, String address);

        public void onDeviceDisconnected();

        public void onDeviceConnectionFailed();
    }

    public interface AutoConnectionListener {
        public void onAutoConnectionStarted();

        public void onNewConnection(String name, String address);
    }

    public boolean isBluetoothAvailable() {
        try {
            if (mBluetoothAdapter == null || mBluetoothAdapter.getAddress().equals(null))
                return false;
        } catch (NullPointerException e) {
            return false;
        }
        return true;
    }

    public boolean isBluetoothEnabled() {
        return mBluetoothAdapter.isEnabled();
    }

    public boolean isServiceAvailable() {
        return mChatService != null;
    }

    public void setupService() {
        mChatService = new BluetoothService(mContext, mHandler);
    }

    public int getServiceState() {
        if (mChatService != null)
            return mChatService.getState();
        else
            return -1;
    }

    public void startService(boolean isAndroid) {
        if (mChatService != null) {
            if (mChatService.getState() == BluetoothState.STATE_NONE) {
                isServiceRunning = true;
                mChatService.start(isAndroid);
                BluetoothUtil.this.isAndroid = isAndroid;
            }
        }
    }

    public void stopService() {
        if (mChatService != null) {
            isServiceRunning = false;
            mChatService.stop();
        }
        new Handler().postDelayed(new Runnable() {
            public void run() {
                if (mChatService != null) {
                    isServiceRunning = false;
                    mChatService.stop();
                }
            }
        }, 500);
    }

    public void setDeviceTarget(boolean isAndroid) {
        stopService();
        startService(isAndroid);
        BluetoothUtil.this.isAndroid = isAndroid;
    }

    @SuppressLint("HandlerLeak")
    private final Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case BluetoothState.MESSAGE_WRITE:
                    break;
                case BluetoothState.MESSAGE_READ:
                    byte[] readBuf = (byte[]) msg.obj;
//                String readMessage = new String(readBuf);
                    if (readBuf != null && readBuf.length > 0) {
                        if (mDataReceivedListener != null)
                            mDataReceivedListener.onDataReceived(readBuf, null);
                    }
                    break;
                case BluetoothState.MESSAGE_DEVICE_NAME:
                    mDeviceName = msg.getData().getString(BluetoothState.DEVICE_NAME);
                    mDeviceAddress = msg.getData().getString(BluetoothState.DEVICE_ADDRESS);
                    if (mBluetoothConnectionListener != null)
                        mBluetoothConnectionListener.onDeviceConnected(mDeviceName, mDeviceAddress);
                    isConnected = true;
                    break;
                case BluetoothState.MESSAGE_TOAST:
                    Toast.makeText(mContext, msg.getData().getString(BluetoothState.TOAST)
                            , Toast.LENGTH_SHORT).show();
                    break;
                case BluetoothState.MESSAGE_STATE_CHANGE:
                    if (mBluetoothStateListener != null)
                        mBluetoothStateListener.onServiceStateChanged(msg.arg1);
                    if (isConnected && msg.arg1 != BluetoothState.STATE_CONNECTED) {
                        if (mBluetoothConnectionListener != null)
                            mBluetoothConnectionListener.onDeviceDisconnected();
                        if (isAutoConnectionEnabled) {
                            isAutoConnectionEnabled = false;
                            autoConnect(keyword);
                        }
                        isConnected = false;
                        mDeviceName = null;
                        mDeviceAddress = null;
                    }

                    if (!isConnecting && msg.arg1 == BluetoothState.STATE_CONNECTING) {
                        isConnecting = true;
                    } else if (isConnecting) {
                        if (msg.arg1 != BluetoothState.STATE_CONNECTED) {
                            if (mBluetoothConnectionListener != null)
                                mBluetoothConnectionListener.onDeviceConnectionFailed();
                        }
                        isConnecting = false;
                    }
                    break;
            }
        }
    };

    public static byte[] intToByteArray(int i) throws Exception {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(buf);
        dos.writeInt(i);
        byte[] b = buf.toByteArray();
        dos.close();
        buf.close();
        return b;
    }

    public void connect(Intent data) {
        String address = data.getExtras().getString(BluetoothState.EXTRA_DEVICE_ADDRESS);
        BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(address);
        mChatService.connect(device);
    }

    public void connect(String address) {
        BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(address);
        mChatService.connect(device);
    }

    public void disconnect() {
        if (mChatService != null) {
            isServiceRunning = false;
            mChatService.stop();
            if (mChatService.getState() == BluetoothState.STATE_NONE) {
                isServiceRunning = true;
                mChatService.start(BluetoothUtil.this.isAndroid);
            }
        }
    }

    public void setOnDataReceivedListener(OnDataReceivedListener listener) {
        mDataReceivedListener = listener;
    }

    public void setBluetoothConnectionListener(BluetoothConnectionListener listener) {
        mBluetoothConnectionListener = listener;
    }
    //添加头发送数据
    public void send(byte[] data, String str) {
        int length = data.length;
        byte[] length_b = null;
        try {
            length_b = intToByteArray(length);
        } catch (Exception e) {
            e.printStackTrace();
        }
        if (length_b == null) return;

        byte[] headerInfo = new byte[headInfoLength];
        for (int i = 0; i < headInfoLength - 8; i++) {
            headerInfo[i] = (byte) i;
        }

        for (int i = 0; i < 4; i++) {
            headerInfo[6 + i] = length_b[i];
        }

        if (str.equals("text")) {
            for (int i = 0; i < 4; i++) {
                headerInfo[10 + i] = (byte) 0;
            }
        } else if (str.equals("photo")) {
            for (int i = 0; i < 4; i++) {
                headerInfo[10 + i] = (byte) 1;
            }
        } else if (str.equals("video")) {
            for (int i = 0; i < 4; i++) {
                headerInfo[10 + i] = (byte) 2;
            }
        }

        byte[] sendMsg = new byte[length + headInfoLength];
        for (int i = 0; i < sendMsg.length; i++) {
            if (i < headInfoLength) {
                sendMsg[i] = headerInfo[i];
            } else {
                sendMsg[i] = data[i - headInfoLength];
            }

        }
        mChatService.write(sendMsg);
    }

    public String getConnectedDeviceName() {
        return mDeviceName;
    }

    public String getConnectedDeviceAddress() {
        return mDeviceAddress;
    }

    public String[] getPairedDeviceName() {
        int c = 0;
        Set<BluetoothDevice> devices = mBluetoothAdapter.getBondedDevices();
        String[] name_list = new String[devices.size()];
        for (BluetoothDevice device : devices) {
            name_list[c] = device.getName();
            c++;
        }
        return name_list;
    }

    public String[] getPairedDeviceAddress() {
        int c = 0;
        Set<BluetoothDevice> devices = mBluetoothAdapter.getBondedDevices();
        String[] address_list = new String[devices.size()];
        for (BluetoothDevice device : devices) {
            address_list[c] = device.getAddress();
            c++;
        }
        return address_list;
    }


    public void autoConnect(String keywordName) {
        if (!isAutoConnectionEnabled) {
            keyword = keywordName;
            isAutoConnectionEnabled = true;
            isAutoConnecting = true;
            if (mAutoConnectionListener != null)
                mAutoConnectionListener.onAutoConnectionStarted();
            final ArrayList<String> arr_filter_address = new ArrayList<String>();
            final ArrayList<String> arr_filter_name = new ArrayList<String>();
            String[] arr_name = getPairedDeviceName();
            String[] arr_address = getPairedDeviceAddress();
            for (int i = 0; i < arr_name.length; i++) {
                if (arr_name[i].contains(keywordName)) {
                    arr_filter_address.add(arr_address[i]);
                    arr_filter_name.add(arr_name[i]);
                }
            }

            bcl = new BluetoothConnectionListener() {
                @Override
                public void onDeviceConnected(String name, String address) {
                    bcl = null;
                    isAutoConnecting = false;
                }

                @Override
                public void onDeviceDisconnected() {
                }

                @Override
                public void onDeviceConnectionFailed() {
                    Log.e("CHeck", "Failed");
                    if (isServiceRunning) {
                        if (isAutoConnectionEnabled) {
                            c++;
                            if (c >= arr_filter_address.size())
                                c = 0;
                            connect(arr_filter_address.get(c));
                            Log.e("CHeck", "Connect");
                            if (mAutoConnectionListener != null)
                                mAutoConnectionListener.onNewConnection(arr_filter_name.get(c)
                                        , arr_filter_address.get(c));
                        } else {
                            bcl = null;
                            isAutoConnecting = false;
                        }
                    }
                }
            };

            setBluetoothConnectionListener(bcl);
            c = 0;
            if (mAutoConnectionListener != null)
                mAutoConnectionListener.onNewConnection(arr_name[c], arr_address[c]);
            if (arr_filter_address.size() > 0)
                connect(arr_filter_address.get(c));
            else
                Toast.makeText(mContext, "Device name mismatch", Toast.LENGTH_SHORT).show();
        }
    }
}
