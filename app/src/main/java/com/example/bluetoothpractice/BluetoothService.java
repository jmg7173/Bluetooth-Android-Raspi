package com.example.bluetoothpractice;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Intent;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.UUID;

/**
 * Created by 임민섭 on 2016-08-11.
 */
public class BluetoothService {
    private static final String TAG = "BluetoothService";
    private static final int REQUEST_CONNECT_DEVICE=1;
    private static final int REQUEST_ENABLE_BT=2;
    private static final UUID MY_UUID =
            UUID.fromString("00001101-0000-1000-8000-00805f9b34fb");

    private static final int STATE_NONE = 0; // we're doing nothing
    private static final int STATE_LISTEN = 1; // now listening for incoming connections
    private static final int STATE_CONNECTING = 2; // now initiating an outgoing connection
    private static final int STATE_CONNECTED = 3; // now connected to a remote device

    private static final int SEND_MESSAGE = 1;
    private static final int CHANGE_STATE = 2;

    private static final String txtNone = "Waiting";
    private static final String txtConnecting = "Connecting to ";
    private static final String txtConnected = "Connected to ";

    private BluetoothAdapter btAdapter;

    private Activity mActivity;
    private Handler mHandler;
    private ConnectThread mConnectThread;
    private ConnectedThread mConnectedThread;

    private int mState;

    public BluetoothService(Activity ac, Handler h){
        mActivity = ac;
        mHandler = h;
        btAdapter = BluetoothAdapter.getDefaultAdapter();
    }

    private class ConnectThread extends Thread{
        private final BluetoothSocket mmSocket;
        private final BluetoothDevice mmDevice;

        public ConnectThread(BluetoothDevice device){
            mmDevice = device;
            BluetoothSocket tmp = null;

            try{
                tmp = device.createRfcommSocketToServiceRecord(MY_UUID);
            } catch(IOException e){
                Log.e(TAG, "create() failed", e);
            }
            mmSocket = tmp;
        }

        public void run(){
            Log.i(TAG, "Begin mConnectThread");
            setName("ConnectThread");


            btAdapter.cancelDiscovery();

            try{
                mmSocket.connect();
                Log.d(TAG, "Connect Success");
            }catch(IOException e){
                connectionFailed();
                Log.d(TAG, "Connect Fail");

                try{
                    mmSocket.close();
                }catch(IOException e2){
                    Log.e(TAG, "unable to close() socket during connection failure", e2);
                }
                BluetoothService.this.start();
                return;
            }

            synchronized (BluetoothService.this){
                mConnectThread = null;
            }
            connected(mmSocket, mmDevice);
        }

        public void cancel(){
            try{
                mmSocket.close();
            }catch(IOException e){
                Log.e(TAG, "close() of connect socket failed", e);
            }
        }
    }
    private class ConnectedThread extends Thread {
        private final BluetoothSocket mmSocket;
        private final InputStream mmInStream;
        private final OutputStream mmOutStream;

        public ConnectedThread(BluetoothSocket socket) {
            Log.d(TAG, "create ConnectedThread");
            mmSocket = socket;
            InputStream tmpIn = null;
            OutputStream tmpOut = null;

            // BluetoothSocket의 inputstream 과 outputstream을 얻는다.
            try {
                tmpIn = socket.getInputStream();
                tmpOut = socket.getOutputStream();
            } catch (IOException e) {
                Log.e(TAG, "temp sockets not created", e);
            }

            mmInStream = tmpIn;
            mmOutStream = tmpOut;
        }

        public void run() {
            Log.i(TAG, "BEGIN mConnectedThread");
            byte[] buffer = new byte[1024];
            int begin = 0;
            int bytes = 0;

            // Keep listening to the InputStream while connected
            while (true) {
                try {
                    // InputStream으로부터 값을 받는 읽는 부분(값을 받는다)
                    bytes += mmInStream.read(buffer,bytes,buffer.length - bytes);
                    for(int i = begin; i < bytes; i++){
                        if(buffer[i] == "#".getBytes()[0]){
                            mHandler.obtainMessage(SEND_MESSAGE, begin, i, buffer).sendToTarget();
                            begin = i+1;
                            if(i == bytes - 1){
                                bytes = 0;
                                begin = 0;
                            }
                        }
                    }

                } catch (IOException e) {
                    Log.e(TAG, "disconnected", e);
                    connectionLost();
                    break;
                }
                Log.i(TAG,"Input Stream : "+buffer);
            }
        }

        /**
         * Write to the connected OutStream.
         * @param buffer  The bytes to write
         */
        public void write(byte[] buffer) {
            try {
                // 값을 쓰는 부분(값을 보낸다)
                mmOutStream.write(buffer);

            } catch (IOException e) {
                Log.e(TAG, "Exception during write", e);
            }
        }

        public void cancel() {
            try {
                mmSocket.close();
            } catch (IOException e) {
                Log.e(TAG, "close() of connect socket failed", e);
            }
        }
    }




    public boolean getDeviceState(){
        Log.d(TAG, "Check the Bluetooth support");
        if(btAdapter == null){
            Log.d(TAG, "Bluetooth is not available");
            return false;
        }
        else{
            Log.d(TAG, "Bluetooth is available");
            return true;
        }
    }

    public String getDeviceInfo(Intent data){
        String address = data.getExtras().getString(DeviceListActivity.EXTRA_DEVICE_ADDRESS);
        String name = data.getExtras().getString(DeviceListActivity.EXTRA_DEVICE_NAME);
        BluetoothDevice device = btAdapter.getRemoteDevice(address);
        Log.d(TAG,"Get Device Info \n" + "address : " + address);
        connect(device);
        return name;
    }

    public void enableBluetooth(){
        Log.i(TAG, "Check the enabled Bluetooth");

        if(btAdapter.isEnabled()){
            Log.d(TAG, "Bluetooth Enable Now");
            scanDevice();
        }
        else{
            Log.d(TAG, "Bluetooth Enable Request");
            Intent i = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            mActivity.startActivityForResult(i,REQUEST_ENABLE_BT);
        }
    }

    public void scanDevice(){
        Log.d(TAG, "Scan Device");
        Intent serverIntent = new Intent(mActivity, DeviceListActivity.class);
        mActivity.startActivityForResult(serverIntent, REQUEST_CONNECT_DEVICE);
    }

    public synchronized void setState(int state){
        Log.d(TAG, "setState() " + mState + " -> " + state);
        mState = state;
        mHandler.obtainMessage(CHANGE_STATE, mState, 0).sendToTarget();
    }

    public synchronized int getState() {
        return mState;
    }

    public synchronized void start(){
        Log.d(TAG, "start");

        if(mConnectThread == null){
        }
        else{
            mConnectThread.cancel();
            mConnectThread = null;
        }

        if(mConnectedThread == null){
        }
        else{
            mConnectedThread.cancel();
            mConnectedThread = null;
        }
    }

    public synchronized void connect(BluetoothDevice device){
        Log.d(TAG, "connect to : " + device);

        if(mState == STATE_CONNECTING) {
            if (mConnectThread == null) {
            } else {
                mConnectThread.cancel();
                mConnectThread = null;
            }
        }
        else if(mState == STATE_CONNECTED){
            if (mConnectedThread == null) {
            } else {
                mConnectedThread.cancel();
                mConnectedThread = null;
            }
        }
        mConnectThread = new ConnectThread(device);
        mConnectThread.start();
        setState(STATE_CONNECTING);
    }

    public synchronized void connected(BluetoothSocket socket, BluetoothDevice device){
        Log.d(TAG, "connected");

        if(mConnectThread == null){
        }
        else{
            mConnectThread.cancel();
            mConnectThread = null;
        }

        if(mConnectedThread == null){
        }
        else{
            mConnectedThread.cancel();
            mConnectedThread = null;
        }

        mConnectedThread = new ConnectedThread(socket);
        mConnectedThread.start();
        setState(STATE_CONNECTED);
    }

    public synchronized void stop(){
        Log.d(TAG, "stop");

        if(mConnectThread != null){
            mConnectThread.cancel();
            mConnectThread = null;
        }

        if(mConnectedThread != null){
            mConnectedThread.cancel();
            mConnectedThread = null;
        }
        setState(STATE_NONE);
    }

    public void write(byte[] out){
        ConnectedThread r;
        synchronized(this){
            if(mState != STATE_CONNECTED)
                return;
            r = mConnectedThread;
            r.write(out);
        }
    }

    public void connectionFailed(){
        setState(STATE_LISTEN);
    }

    private void connectionLost(){
        setState(STATE_LISTEN);
    }
}
