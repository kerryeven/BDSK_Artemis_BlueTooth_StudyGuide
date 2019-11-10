package e.kerry.bdsk.ui;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.bluetooth.BluetoothGattService;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;

import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

import e.kerry.bdsk.Constants;
import e.kerry.bdsk.R;
import e.kerry.bdsk.bluetooth.BleAdapterService;

/***********************************************************************************************
 * Service Connection
 * Our new Activity needs to be able to use the BleAdapterService as a kind of Bluetooth API. To
 * do so, it needs to form a “service connection” and when connected to the Android service, obtain
 * an an instance of the BleAdapterService for subsequent use. Don’t confuse “Android service” with
 * “Bluetooth GATT service” by the way!
 *
 * Create a service connection and to handle service connection and disconnection:
 */

public class PeripheralControlActivity extends Activity {
    public static final String EXTRA_NAME = "name";
    public static final String EXTRA_ID = "id";
    private String device_name;
    private String device_address;
    private Timer mTimer;
    private boolean sound_alarm_on_disconnect = false;
    private int alert_level;
    private boolean back_requested = false;
    private boolean share_with_server = false;
    private Switch share_switch;
    private BleAdapterService bluetooth_le_adapter;
    private Button b_Connect;
    private Button b_Send;
    byte [] al = new byte[1];


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_peripheral_control);
// read intent data
        final Intent intent = getIntent();
        device_name = intent.getStringExtra(EXTRA_NAME);
        device_address = intent.getStringExtra(EXTRA_ID);
// show the device name
        b_Connect = this.findViewById(R.id.connectButton);
        b_Send = this.findViewById(R.id.stopButton);
        ((TextView) this.findViewById(R.id.nameTextView)).setText("Device : "+device_name
                +" ["+device_address+"]");
        // hide the coloured rectangle used to show green/amber/red rssi
// distance
        ((LinearLayout) this.findViewById(R.id.rectangle))
                .setVisibility(View.INVISIBLE);
// disable the noise button
        ((Button) PeripheralControlActivity.this.findViewById(R.id.stopButton))
                .setEnabled(false);

// disable the LOW/MID/HIGH alert level selection buttons
        ((Button) this.findViewById(R.id.leftButton)).setEnabled(false);
        ((Button) this.findViewById(R.id.fwrdButton)).setEnabled(false);
        ((Button) this.findViewById(R.id.rightButton)).setEnabled(false);
        ((Button) this.findViewById(R.id.rvrseButton)).setEnabled(false);
        ((Button) this.findViewById(R.id.stopButton)).setEnabled(false);
        share_switch = (Switch) this.findViewById(R.id.switch1);
        share_switch.setEnabled(false);
        share_switch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton buttonView,
                                         boolean isChecked) {
// we'll complete this later
            }
        });
// connect to the Bluetooth adapter service
        Intent gattServiceIntent = new Intent(this, BleAdapterService.class);
        bindService(gattServiceIntent, service_connection, BIND_AUTO_CREATE);
        showMsg("Working it....CONNECTING...");
        /*******************************************************************************************
         * can't connect to adapter from onCreate as adapter is not started untill after onCreate
         * is exited.  So, create Runnapble to run 5 seconds after onCreate finishes to allow
         * adapter to be created.
         ******************************************************************************************/
        b_Connect.setVisibility(View.INVISIBLE);
        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                showMsg("onConnect");
                if (bluetooth_le_adapter != null) {
                    if (bluetooth_le_adapter.connect(device_address)) {
                        ((Button) PeripheralControlActivity.this
                                .findViewById(R.id.connectButton)).setEnabled(false);
                    } else {
                        showMsg("onConnect: failed to connect");
                    }
                } else {
                    showMsg("onConnect: bluetooth_le_adapter=null");
                }
                //b_Connect.performClick();
            }
        }, 5000);
        //b_Connect.performClick();

    }
    private void showMsg(final String msg) {
        Log.d(Constants.TAG, msg);
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                ((TextView) findViewById(R.id.msgTextView)).setText(msg);
            }
        });
    }

    private final ServiceConnection service_connection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName componentName, IBinder service) {
            bluetooth_le_adapter = ((BleAdapterService.LocalBinder) service).getService();
            bluetooth_le_adapter.setActivityHandler(message_handler);
        }
        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            bluetooth_le_adapter = null;
        }
    };

    /***********************************************************************************************
     * Create a Handler for Service to Activity Communication:
     **********************************************************************************************/
    @SuppressLint("HandlerLeak")
    private Handler message_handler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            Bundle bundle;
            String service_uuid = "";
            String characteristic_uuid = "";
            byte[] b = null;
            // message handling logic
            switch (msg.what) {
                case BleAdapterService.MESSAGE:
                    bundle = msg.getData();
                    String text = bundle.getString(BleAdapterService.PARCEL_TEXT);
                    showMsg(text);
                    break;
                case BleAdapterService.GATT_CONNECTED:
                    ((Button) PeripheralControlActivity.this
                            .findViewById(R.id.connectButton)).setEnabled(false);
// we're connected
                    showMsg("CONNECTED");
                    // enable the LOW/MID/HIGH alert level selection buttons
                    ((Button) PeripheralControlActivity.this.findViewById(R.id.leftButton)).setEnabled(true);
                    ((Button) PeripheralControlActivity.this.findViewById(R.id.fwrdButton)).setEnabled(true);
                    ((Button) PeripheralControlActivity.this.findViewById(R.id.rightButton)).setEnabled(true);
                    ((Button) PeripheralControlActivity.this.findViewById(R.id.rvrseButton)).setEnabled(true);
                    ((Button) PeripheralControlActivity.this.findViewById(R.id.stopButton)).setEnabled(true);
                    bluetooth_le_adapter.discoverServices();
                    break;
                case BleAdapterService.GATT_DISCONNECT:
                    ((Button) PeripheralControlActivity.this
                            .findViewById(R.id.connectButton)).setEnabled(true);
// we're disconnected
                    showMsg("DISCONNECTED");
                    // hide the rssi distance colored rectangle
                    ((LinearLayout) PeripheralControlActivity.this
                            .findViewById(R.id.rectangle))
                            .setVisibility(View.INVISIBLE);
                    // disable the LOW/MID/HIGH alert level selection buttons
                    ((Button) PeripheralControlActivity.this.findViewById(R.id.leftButton)).setEnabled(false);
                    ((Button) PeripheralControlActivity.this.findViewById(R.id.fwrdButton)).setEnabled(false);
                    ((Button) PeripheralControlActivity.this.findViewById(R.id.rightButton)).setEnabled(false);
                    ((Button) PeripheralControlActivity.this.findViewById(R.id.rvrseButton)).setEnabled(false);
                    ((Button) PeripheralControlActivity.this.findViewById(R.id.stopButton)).setEnabled(false);
                    // stop the rssi reading timer
                    stopTimer();
                    if (back_requested) {
                        PeripheralControlActivity.this.finish();
                    }
                    break;
                case BleAdapterService.GATT_SERVICES_DISCOVERED:
// validate services and if ok....
                    List<BluetoothGattService> slist = bluetooth_le_adapter.getSupportedGattServices();
                    boolean link_loss_present=false;
                    boolean immediate_alert_present=false;
                    boolean tx_power_present=false;
                    //boolean proximity_monitoring_present=false;
                    //boolean health_thermometer_present = false;
                    for (BluetoothGattService svc : slist) {
                        Log.d(Constants.TAG, "UUID=" + svc.getUuid().toString().toUpperCase() + " INSTANCE=" + svc.getInstanceId());
                        if (svc.getUuid().toString().equalsIgnoreCase(BleAdapterService.LINK_LOSS_SERVICE_UUID)) {
                            link_loss_present = true;
                            continue;
                        }
                        if (svc.getUuid().toString().equalsIgnoreCase(BleAdapterService.IMMEDIATE_ALERT_SERVICE_UUID)) {
                            immediate_alert_present = true;
                            continue;
                        }
                        if (svc.getUuid().toString().equalsIgnoreCase(BleAdapterService.TX_POWER_SERVICE_UUID)) {
                            tx_power_present = true;
                            continue;
                        }
                        //if (svc.getUuid().toString().equalsIgnoreCase(BleAdapterService.PROXIMITY_MONITORING_SERVICE_UUID)) {
                        //    proximity_monitoring_present = true;
                        //    continue;
                        //}
                        //if (svc.getUuid().toString().equalsIgnoreCase(BleAdapterService.HEALTH_THERMOMETER_SERVICE_UUID)) {
                        //    health_thermometer_present = true;
                        //    continue;
                        //}
                    }
                    //KHE proximity_monitoring && health_thermometer ARE NOT PRESENT
                    //if (link_loss_present && immediate_alert_present && tx_power_present && proximity_monitoring_present && health_thermometer_present) {
                    if (link_loss_present && immediate_alert_present && tx_power_present) {
                        showMsg("Device has expected services");
// show the rssi distance colored rectangle
                        ((LinearLayout) PeripheralControlActivity.this
                                .findViewById(R.id.rectangle))
                                .setVisibility(View.VISIBLE);
// enable the LOW/MID/HIGH alert level selection buttons
                        ((Button) PeripheralControlActivity.this.findViewById(R.id.leftButton)).setEnabled(true);
                        ((Button) PeripheralControlActivity.this.findViewById(R.id.fwrdButton)).setEnabled(true);
                        ((Button) PeripheralControlActivity.this.findViewById(R.id.rightButton)).setEnabled(true);
                        ((Button) PeripheralControlActivity.this.findViewById(R.id.rvrseButton)).setEnabled(true);
                        ((Button) PeripheralControlActivity.this.findViewById(R.id.stopButton)).setEnabled(true);
                        //After service discovery has completed and we’ve validated the services on
                        // the device, we’ll read the alert level characteristic.
                        bluetooth_le_adapter.readCharacteristic(
                                BleAdapterService.LINK_LOSS_SERVICE_UUID,
                                BleAdapterService.ALERT_LEVEL_CHARACTERISTIC);
                    } else {
                        showMsg("Device does not have expected GATT services");
                    }
                    break;
                //handles characteristic read and write messages from BleAdapterService
                case BleAdapterService.GATT_CHARACTERISTIC_READ:
                    bundle = msg.getData();
                    Log.d(Constants.TAG, "Service=" + bundle.get(BleAdapterService.PARCEL_SERVICE_UUID).toString().toUpperCase() + " Characteristic=" + bundle.get(BleAdapterService.PARCEL_CHARACTERISTIC_UUID).toString().toUpperCase());
                    if (bundle.get(BleAdapterService.PARCEL_CHARACTERISTIC_UUID).toString()
                            .toUpperCase().equals(BleAdapterService.ALERT_LEVEL_CHARACTERISTIC)
                            && bundle.get(BleAdapterService.PARCEL_SERVICE_UUID).toString()
                            .toUpperCase().equals(BleAdapterService.LINK_LOSS_SERVICE_UUID)) {
                        b = bundle.getByteArray(BleAdapterService.PARCEL_VALUE);
                        if (b.length > 0) {
                            //PeripheralControlActivity.this.setAlertLevel((int) b[0]);
                            // show the rssi distance colored rectangle
                            ((LinearLayout) PeripheralControlActivity.this
                                    .findViewById(R.id.rectangle))
                                    .setVisibility(View.VISIBLE);
// start off the rssi reading timer
                            startReadRssiTimer();
                        }
                    }
                    break;
                case BleAdapterService.GATT_CHARACTERISTIC_WRITTEN:
                    //changes appropriate button color to red after writing GATT_CHAR...
                    bundle = msg.getData();
                    if (bundle.get(BleAdapterService.PARCEL_CHARACTERISTIC_UUID).toString()
                            .toUpperCase().equals(BleAdapterService.ALERT_LEVEL_CHARACTERISTIC)
                            && bundle.get(BleAdapterService.PARCEL_SERVICE_UUID).toString()
                            .toUpperCase().equals(BleAdapterService.IMMEDIATE_ALERT_SERVICE_UUID)) {
                        //KHE changed LINK_LOSS_SERVICE_UUID to IMMEDIATE...(previous line)
                        b = bundle.getByteArray(BleAdapterService.PARCEL_VALUE);
                        if (b.length > 0) {
                            PeripheralControlActivity.this.setAlertLevel((int) b[0]);
                        }
                    }
                    break;
                case BleAdapterService.GATT_REMOTE_RSSI:
                    bundle = msg.getData();
                    int rssi = bundle.getInt(BleAdapterService.PARCEL_RSSI);
                    PeripheralControlActivity.this.updateRssi(rssi);
                    break;

            }
        }
    };

    /***********************************************************************************************
     *Disconnect when back button is pressed
     * If we’re connected to the peripheral device when the user presses the back button, we need
     * to respond by first disconnecting and then allowing the default response to pressing the
     * back button to be taken. Add this function to PeripheralControlActivity. It will be
     * automatically called.
     *
     *This method actually prevents the user from exiting the current screen as things stand. It
     * requests that we disconnect from Bluetooth but until we receive a message from
     * BleAdapterService to say disconnection has been accomplished, we can’t yet exit. Update the
     * message handler (above) case for disconnection so that it takes into account the possibility
     * that the user has pressed the back button and completes the process of exiting the current
     * screen:
     *      if (back_requested) {
     *          PeripheralControlActivity.this.finish();
     *          }
     **********************************************************************************************/

    public void onBackPressed() {
        Log.d(Constants.TAG, "onBackPressed");
        back_requested = true;
        if (bluetooth_le_adapter.isConnected()) {
            try {
                bluetooth_le_adapter.disconnect();
            } catch (Exception e) {
            }
        } else {
            finish();
        }
    }

    //On-Click event handlers: uses our new BleAdapterService methods to initiate writing to the
    // alert level characteristic of the link loss service:
    public void onLow(View view) {
        bluetooth_le_adapter.writeCharacteristic(
                //BleAdapterService.LINK_LOSS_SERVICE_UUID,
                BleAdapterService.IMMEDIATE_ALERT_SERVICE_UUID,
                BleAdapterService.ALERT_LEVEL_CHARACTERISTIC, Constants.ALERT_LEVEL_LOW
        );
        //setAlertLevel(0);
        //new Handler().postDelayed(new Runnable() {
        //    @Override
        //    public void run() {
        //        ((Button) findViewById(R.id.noiseButton)).performClick();
        //        //b_Send.performClick();
        //    }
        //}, 1000);
    }
    public void onMid(View view) {
        bluetooth_le_adapter.writeCharacteristic(
                BleAdapterService.IMMEDIATE_ALERT_SERVICE_UUID,
                BleAdapterService.ALERT_LEVEL_CHARACTERISTIC, Constants.ALERT_LEVEL_MID
        );
        //setAlertLevel(1);
        //new Handler().postDelayed(new Runnable() {
        //    @Override
        //public void run() {
        //        ((Button) findViewById(R.id.noiseButton)).performClick();
        //    }
        //}, 1000);
    }
    public void onHigh(View view) {
        bluetooth_le_adapter.writeCharacteristic(
                //BleAdapterService.LINK_LOSS_SERVICE_UUID,
                //BleAdapterService.ALERT_LEVEL_CHARACTERISTIC, Constants.ALERT_LEVEL_HIGH
                BleAdapterService.IMMEDIATE_ALERT_SERVICE_UUID,
                BleAdapterService.ALERT_LEVEL_CHARACTERISTIC, Constants.ALERT_LEVEL_HIGH
        );
    }
    public void onOther(View view) {
        bluetooth_le_adapter.writeCharacteristic(
                //BleAdapterService.LINK_LOSS_SERVICE_UUID,
                //BleAdapterService.ALERT_LEVEL_CHARACTERISTIC, Constants.ALERT_LEVEL_HIGH
                BleAdapterService.IMMEDIATE_ALERT_SERVICE_UUID,
                BleAdapterService.ALERT_LEVEL_CHARACTERISTIC, Constants.ALERT_LEVEL_OTHER
        );
    }
    public void onStop(View view) {
        //al[0] = (byte) 4;
        //byte [] al = new byte[1];
        //al[0] = (byte) alert_level;
        bluetooth_le_adapter.writeCharacteristic(
                BleAdapterService.IMMEDIATE_ALERT_SERVICE_UUID,
                BleAdapterService.ALERT_LEVEL_CHARACTERISTIC, Constants.ALERT_LEVEL_STOP
        );
    }

    /***********************************************************************************************
     *On Connect method:
     * clicking the CONNECT button will ask the BleAdapterService to initiate connecting to the
     * selected peripheral device. Remember, this is an asychronous operation though. This Activity
     * will be informed when the connection has been established via a message arriving in our
     * handler object.
     **********************************************************************************************/
    public void onConnect(View view) {
        showMsg("onConnect");
        if (bluetooth_le_adapter != null) {
            if (bluetooth_le_adapter.connect(device_address)) {
                ((Button) PeripheralControlActivity.this
                        .findViewById(R.id.connectButton)).setEnabled(false);
            } else {
                showMsg("onConnect: failed to connect");
            }
        } else {
            showMsg("onConnect: bluetooth_le_adapter=null");
        }
    }

    /***********************************************************************************************
     *Highlight Selected Alert Level
     * When the user clicks one of the LOW, MID or HIGH buttons and the associated characteristic
     * has been successfully updated over Bluetooth, we’ll set the colour of the text of selected
     * button and set an internal variable to the selected alert level.
     **********************************************************************************************/
    private void setAlertLevel(int alert_level) {
        this.alert_level = alert_level;
        ((Button) this.findViewById(R.id.leftButton)).setTextColor(Color.parseColor("#000000")); ;
        ((Button) this.findViewById(R.id.fwrdButton)).setTextColor(Color.parseColor("#000000")); ;
        ((Button) this.findViewById(R.id.rightButton)).setTextColor(Color.parseColor("#000000")); ;
        ((Button) this.findViewById(R.id.rvrseButton)).setTextColor(Color.parseColor("#000000")); ;
        ((Button) this.findViewById(R.id.stopButton)).setTextColor(Color.parseColor("#000000")); ;
        switch (alert_level) {
            case 0:
                ((Button) this.findViewById(R.id.leftButton)).setTextColor(Color.parseColor("#FF0000")); ;
                break;
            case 1:
                ((Button) this.findViewById(R.id.fwrdButton)).setTextColor(Color.parseColor("#FF0000")); ;
                break;
            case 2:
                ((Button) this.findViewById(R.id.rightButton)).setTextColor(Color.parseColor("#FF0000")); ;
                break;
            case 3:
                ((Button) this.findViewById(R.id.stopButton)).setTextColor(Color.parseColor("#FF0000")); ;
                break;
            case 4:
                ((Button) this.findViewById(R.id.rvrseButton)).setTextColor(Color.parseColor("#FF0000")); ;
                break;
        }
    }
    private void startReadRssiTimer() {
        mTimer = new Timer();
        mTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                bluetooth_le_adapter.readRemoteRssi();
            }
        }, 0, 2000);
    }
    private void stopTimer() {
        if (mTimer != null) {
            mTimer.cancel();
            mTimer = null;
        }
    }
    private void updateRssi(int rssi) {
        ((TextView) findViewById(R.id.rssiTextView)).setText("RSSI = "
                + Integer.toString(rssi));
        LinearLayout layout = ((LinearLayout) PeripheralControlActivity.this
                .findViewById(R.id.rectangle));
        byte proximity_band = 3;
        if (rssi < -80) {
            layout.setBackgroundColor(0xFFFF0000);
        } else if (rssi < -50) {
            layout.setBackgroundColor(0xFFFF8A01);
            proximity_band = 2;
        } else {
            layout.setBackgroundColor(0xFF00FF00);
            proximity_band = 1;
        }
        layout.invalidate();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopTimer();
        unbindService(service_connection);
        bluetooth_le_adapter = null;
    }
}
