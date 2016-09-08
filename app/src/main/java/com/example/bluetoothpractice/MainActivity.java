package com.example.bluetoothpractice;

import android.app.Activity;
import android.content.Intent;
import android.os.Handler;
import android.os.Message;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends AppCompatActivity implements View.OnClickListener {
    private static final String TAG = "Main";

    private static final int REQUEST_CONNECT_DEVICE=1;
    private static final int REQUEST_ENABLE_BT=2;

    private static final int STATE_NONE = 0; // we're doing nothing
    private static final int STATE_LISTEN = 1; // now listening for incoming connections
    private static final int STATE_CONNECTING = 2; // now initiating an outgoing connection
    private static final int STATE_CONNECTED = 3; // now connected to a remote device

    private static final int SEND_MESSAGE = 1;
    private static final int CHANGE_STATE = 2;

    private static final String TXT_NONE = "Waiting";
    private static final String TXT_CONNECTING = "Connecting to ";
    private static final String TXT_CONNECTED = "Connected to ";

    private BluetoothService btService = null;

    private Button btnConnect;
    private Button btnGo;
    private Button btnGoRight;
    private Button btnGoLeft;
    private Button btnBack;
    private Button btnPause;

    private TextView txtResult;

    private String deviceName;

    private final Handler mHandler = new Handler(){
        @Override
        public void handleMessage(Message msg) {
            switch(msg.what) {
                case SEND_MESSAGE:
                    byte[] writeBuf = (byte[]) msg.obj;
                    int begin = (int)msg.arg1;
                    int end = (int)msg.arg2;
                    String writeMessage = new String(writeBuf);
                    writeMessage = writeMessage.substring(begin, end);
                    break;
                case CHANGE_STATE:
                    int state = (int)msg.arg1;
                    setTxtResult(state);
                    break;
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.e(TAG, "onCreate");
        setContentView(R.layout.activity_main);

        btnConnect = (Button) findViewById(R.id.btn_connect);
        txtResult = (TextView) findViewById(R.id.txt_result);

        btnGo = (Button) findViewById(R.id.move_GoBtn);
        btnGoRight = (Button) findViewById(R.id.move_RightGoBtn);
        btnGoLeft = (Button) findViewById(R.id.move_LeftGoBtn);
        btnBack = (Button) findViewById(R.id.move_BackBtn);
        btnPause = (Button) findViewById(R.id.move_StopBtn);

        btnConnect.setOnClickListener(this);
        btnGo.setOnClickListener(this);
        btnGoRight.setOnClickListener(this);
        btnGoLeft.setOnClickListener(this);
        btnBack.setOnClickListener(this);
        btnPause.setOnClickListener(this);

        if(btService == null){
            btService = new BluetoothService(this, mHandler);
        }
    }

    public void onActivityResult(int requestCode, int resultCode, Intent data){
        Log.d(TAG, "onActivityResult " + resultCode);
        switch(requestCode){
            case REQUEST_CONNECT_DEVICE:
                if(resultCode == Activity.RESULT_OK){
                    deviceName = btService.getDeviceInfo(data);
                }
                break;
            case REQUEST_ENABLE_BT:
                if(resultCode == Activity.RESULT_OK){
                    // Ok Button
                    btService.scanDevice();
                }
                else {
                    // Cancel Button
                    Log.d(TAG, "Bluetooth is not enabled");
                }
                break;
        }
    }

    public void onClick(View v){
        switch(v.getId()) {
            case R.id.btn_connect:
                if (btService.getDeviceState()) {
                    btService.enableBluetooth();
                } else {
                    finish();
                }
                break;

            case R.id.move_GoBtn:
                Log.i(TAG, "@Go# : @2#");
                sendToArduino("@2#");
                break;

            case R.id.move_RightGoBtn:
                Log.i(TAG, "@GoRight# : @3#");
                sendToArduino("@3#");
                break;

            case R.id.move_LeftGoBtn:
                Log.i(TAG, "@GoLeft# : @1#");
                sendToArduino("@1#");
                break;

            case R.id.move_BackBtn:
                Log.i(TAG, "@BACK# : @8#");
                sendToArduino("@8#");
                break;

            case R.id.move_StopBtn:
                Log.i(TAG, "@STOP# : @5#");
                sendToArduino("@5#");
                break;
        }
    }

    public void setTxtResult(int state){
        switch(state){
            case STATE_NONE:
            case STATE_LISTEN:
                txtResult.setText(TXT_NONE);
                break;
            case STATE_CONNECTING:
                txtResult.setText(TXT_CONNECTING + deviceName);
                break;
            case STATE_CONNECTED:
                txtResult.setText(TXT_CONNECTED + deviceName);
                break;
        }
    }

    private void sendToArduino(String message){
        Log.i(TAG, "sendToArduino : " + message);
        if(btService.getState() != STATE_CONNECTED){
            Toast.makeText(this, R.string.not_connected, Toast.LENGTH_SHORT).show();
        }
        else if(message.length() > 0){
            btService.write(message.getBytes());
        }
    }
}
