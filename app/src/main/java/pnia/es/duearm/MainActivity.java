package pnia.es.duearm;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.ParcelUuid;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import java.util.Set;

public class MainActivity extends Activity {

    private static final int REQUEST_CONNECT_DEVICE_SECURE = 1;
    private static final int REQUEST_ENABLE_BT = 2;

    private BluetoothAdapter mBluetoothAdapter = null;
    private Bluetooth mBT = null;
    private String mConnectedDeviceName = null;

    Button baseUp, baseDown, brazoUp, brazoDown, antebrazoUp, antebrazoDown;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        antebrazoUp = (Button) findViewById(R.id.antebrazoUp);
        new buttonCommand(antebrazoUp, "7");
        antebrazoDown = (Button) findViewById(R.id.antebrazoDown);
        new buttonCommand(antebrazoDown, "8");

        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (mBluetoothAdapter == null) {
            Toast.makeText(this, "Bluetooth is not available", Toast.LENGTH_LONG).show();
            this.finish();
        }
    }

    @Override
    protected void onStart() {
        super.onStart();

        if (!mBluetoothAdapter.isEnabled()) {
            Toast.makeText(this, "Activa bluetooh y conecta a DueArm", Toast.LENGTH_LONG).show();
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
        } else if (mBT == null) {
            createBT();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (mBT != null) {
            // Only if the state is STATE_NONE, do we know that we haven't started already
            if (mBT.getState() == mBT.STATE_NONE) {
                // Start the Bluetooth chat services
                mBT.start();
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mBT != null) {
            mBT.stop();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
            case REQUEST_CONNECT_DEVICE_SECURE:
                // When DeviceListActivity returns with a device to connect
                if (resultCode == Activity.RESULT_OK) {
                    connectDevice(data, true);
                }
                break;
//            case REQUEST_CONNECT_DEVICE_INSECURE:
//                // When DeviceListActivity returns with a device to connect
//                if (resultCode == Activity.RESULT_OK) {
//                    connectDevice(data, false);
//                }
//                break;
            case REQUEST_ENABLE_BT:
                // When the request to enable Bluetooth returns
                if (resultCode == Activity.RESULT_OK) {
                    // Bluetooth is now enabled, so set up a chat session
                    createBT();
                } else {
                    // User did not enable Bluetooth or an error occurred
                    Toast.makeText(this, R.string.bt_not_enabled_leaving, Toast.LENGTH_SHORT).show();
                    this.finish();
                }
        }
    }

    private void connectDevice(Intent data, boolean secure) {
        // Get the device MAC address
        String address = data.getExtras().getString(DeviceListActivity.EXTRA_DEVICE_ADDRESS);
        // Get the BluetoothDevice object
        BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(address);
        // Attempt to connect to the device
        mBT.connect(device, true);
    }

    private void createBT() {
        // Initialize the Bluetooth to perform bluetooth connections
        mBT = new Bluetooth(new Handler() {
            @Override
            public void handleMessage(Message msg) {
                switch (msg.what) {
                    case Constants.MESSAGE_STATE_CHANGE:
                        switch (msg.arg1) {
                            case Bluetooth.STATE_CONNECTED:
                                setStatus(getString(R.string.title_connected_to, mConnectedDeviceName));
                                break;
                            case Bluetooth.STATE_CONNECTING:
                                setStatus(R.string.title_connecting);
                                break;
                            case Bluetooth.STATE_LISTEN:
                            case Bluetooth.STATE_NONE:
                                setStatus(R.string.title_not_connected);
                                break;
                        }
                        break;
                    case Constants.MESSAGE_WRITE:
                        byte[] writeBuf = (byte[]) msg.obj;
                        // construct a string from the buffer
                        String writeMessage = new String(writeBuf);
                        break;
                    case Constants.MESSAGE_READ:
                        byte[] readBuf = (byte[]) msg.obj;
                        // construct a string from the valid bytes in the buffer
                        String readMessage = new String(readBuf, 0, msg.arg1);
                        break;
                    case Constants.MESSAGE_DEVICE_NAME:
                        // save the connected device's name
                        mConnectedDeviceName = msg.getData().getString(Constants.DEVICE_NAME);
                        Toast.makeText(MyApplication.getAppContext(), "Connected to " + mConnectedDeviceName, Toast.LENGTH_SHORT).show();
                        break;
                    case Constants.MESSAGE_TOAST:
                        Toast.makeText(MyApplication.getAppContext(), msg.getData().getString(Constants.TOAST), Toast.LENGTH_SHORT).show();
                        break;
                }
            }
        }
        );
    }

    /**
     * Updates the status on the action bar.
     *
     * @param resId a string resource ID
     */
    private void setStatus(int resId) {
//        getSupportActionBar().setSubtitle(resId);
        getActionBar().setSubtitle(resId);
    }

    /**
     * Updates the status on the action bar.
     *
     * @param subTitle status
     */
    private void setStatus(CharSequence subTitle) {
        getActionBar().setSubtitle(subTitle);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu, menu);
        return true;
    }

    public void showDevices(MenuItem item) {
        Intent serverIntent = new Intent(this, DeviceListActivity.class);
        startActivityForResult(serverIntent, REQUEST_CONNECT_DEVICE_SECURE);
    }

    private class buttonCommand implements View.OnTouchListener {

        private String pressCommand;

        public buttonCommand(Button button, String pressCommand) {
            button.setOnTouchListener(this);
            this.pressCommand = pressCommand;
        }

        @Override
        public boolean onTouch(View v, MotionEvent event) {
            switch(event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    sendMessage(pressCommand);
                    // Start
                    break;
                case MotionEvent.ACTION_UP:
                    // End
                    break;
            }
            return false;
        }

        private void sendMessage(String message) {
            // Check that we're actually connected before trying anything
            if (mBT.getState() != Bluetooth.STATE_CONNECTED) {
                Toast.makeText(getApplicationContext(), R.string.not_connected, Toast.LENGTH_SHORT).show();
//                return;
            }

            // Check that there's actually something to send
            if (message.length() > 0) {
                // Get the message bytes and tell the BluetoothChatService to write
                byte[] send = message.getBytes();
                mBT.write(send);
            }
        }
    }
}
