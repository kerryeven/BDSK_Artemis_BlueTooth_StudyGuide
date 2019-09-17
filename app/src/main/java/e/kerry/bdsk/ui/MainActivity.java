package e.kerry.bdsk.ui;

import android.Manifest;
import android.bluetooth.BluetoothDevice;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;

import e.kerry.bdsk.Constants;
import e.kerry.bdsk.R;
import e.kerry.bdsk.bluetooth.BleScanner;
import e.kerry.bdsk.bluetooth.ScanResultsConsumer;

public class MainActivity extends AppCompatActivity implements ScanResultsConsumer {
    private boolean ble_scanning = false;
    private Handler handler = new Handler();
    private ListAdapter ble_device_list_adapter;
    private BleScanner ble_scanner;
    private static final long SCAN_TIMEOUT = 5000;
    private static final int REQUEST_LOCATION = 0;
    private static String[] PERMISSIONS_LOCATION = {Manifest.permission.ACCESS_COARSE_LOCATION};
    private boolean permissions_granted=false;
    private int device_count=0;
    private Toast toast;
    static class ViewHolder {
        public TextView text;
        public TextView bdaddr;
    }


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);
        setButtonText();

        ble_device_list_adapter = new ListAdapter();

        ListView listView = (ListView) this.findViewById(R.id.deviceList);
        listView.setAdapter(ble_device_list_adapter);

        ble_scanner = new BleScanner(this.getApplicationContext());

        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {

            @Override
            public void onItemClick(AdapterView<?> parent, View view,
                                    int position, long id) {

                if (ble_scanning) {
                    ble_scanner.stopScanning();
                }

                BluetoothDevice device = ble_device_list_adapter.getDevice(position);
                if (toast != null) {
                    toast.cancel();
                }
                Intent intent = new Intent(MainActivity.this, PeripheralControlActivity.class);
                intent.putExtra(PeripheralControlActivity.EXTRA_NAME, device.getName());
                intent.putExtra(PeripheralControlActivity.EXTRA_ID, device.getAddress());
                startActivity(intent);

            }
        });
    }

    /***********************************************************************************************
     * Update the candidateBleDevice method so that any details passed to it by the BleScanner
     * object are stored in the ListAdapter and are shown on the UI if not already there.
     **********************************************************************************************/
    @Override
    public void candidateBleDevice(final BluetoothDevice device, byte[] scan_record, int rssi) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                //Next line will toast to UI
                //toast.makeText(getApplicationContext(),"Device = "
                //        + device.getName(),Toast.LENGTH_LONG).show();
                //Next line will Logcat with filter bdsk
                Log.d(Constants.TAG,"Device Found = " + device.getName());
                //KHE Therfore could filter by name here and only add to list adapter if = ...
                ble_device_list_adapter.addDevice(device);
                ble_device_list_adapter.notifyDataSetChanged();
                device_count++;
            }
        });
    }

    /***********************************************************************************************
     * The BleScanner object will tell our MainActivity object whenever it starts to perform scanning
     * or stops scanning by calling the corresponding methods of the ScanResultsConsumer interface
     * which MainActivity implements.
     **********************************************************************************************/
    @Override
    public void scanningStarted() {
        setScanState(true);
    }

    @Override
    public void scanningStopped() {
        if(toast != null){
            toast.cancel();
        }
        setScanState(false);
    }

    private void setButtonText() {
        String text = "";
        text = Constants.FIND;
        final String button_text = text;
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                ((TextView) MainActivity.this.findViewById(R.id.scanButton)).setText(button_text);
            }
        });
    }

    private void setScanState(boolean value) {
        ble_scanning = value;
        ((Button) this.findViewById(R.id.scanButton)).setText(value ? Constants.STOP_SCANNING : Constants.FIND);
    }

    /***********************************************************************************************
     * Now we'll add a list adapter that serves to store a list of BluetoothDevices that are found
     * during scanning, and provides the data for the list view. The adapter will use the
     * list_row.xml layout we added earlier and return that view with the name of the found
     * device for the list to display in the row.
     */
    private class ListAdapter extends BaseAdapter {
        private ArrayList<BluetoothDevice> ble_devices;
        public ListAdapter() {
            super();
            ble_devices = new ArrayList<BluetoothDevice>();
        }
        public void addDevice(BluetoothDevice device) {
            if (!ble_devices.contains(device)) {
                ble_devices.add(device);
            }
        }
        public boolean contains(BluetoothDevice device) {
            return ble_devices.contains(device);
        }
        public BluetoothDevice getDevice(int position) {
            return ble_devices.get(position);
        }
        public void clear() {
            ble_devices.clear();
        }
        @Override
        public int getCount() {
            return ble_devices.size();
        }
        @Override
        public Object getItem(int i) {
            return ble_devices.get(i);
        }
        @Override
        public long getItemId(int i) {
            return i;
        }
        @Override
        public View getView(int i, View view, ViewGroup viewGroup) {
            ViewHolder viewHolder;
            if (view == null) {
                view = MainActivity.this.getLayoutInflater().inflate(R.layout.list_row, null);
                viewHolder = new ViewHolder();
                viewHolder.text = (TextView) view.findViewById(R.id.textView);
                viewHolder.bdaddr = (TextView) view.findViewById(R.id.bdaddr);
                view.setTag(viewHolder);
            } else {
                viewHolder = (ViewHolder) view.getTag();
            }
            BluetoothDevice device = ble_devices.get(i);
            String deviceName = device.getName();
            if (deviceName != null && deviceName.length() > 0) {
                viewHolder.text.setText(deviceName);
            } else {
                viewHolder.text.setText("unknown device");
            }
            viewHolder.bdaddr.setText(device.getAddress());
            return view;
        }
    }
    /***********************************************************************************************
     *Next we need to add some code to the MainActivity class which will respond to the Find button
     * being pressed. It will need to trigger Bluetooth scanning (unless we’re already scanning) but
     * before it can do so, it *may* need to request permissions from the user. This is only an
     * issue if running on a device with Android 6 or a later version of the OS installed on it.
     * When Android 6 came out, the permissions model changed so that some types of permissions
     * are requested when they are first needed as opposed to when the application is initially
     * installed. Once permissions have been granted, they stay granted so the user is only asked
     * to grant permissions once.
     */
    public void onScan(View view) {
        if (!ble_scanner.isScanning()) {
            Log.d(Constants.TAG, "Not currently scanning");
            device_count=0;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)
                        != PackageManager.PERMISSION_GRANTED) {
                    permissions_granted = false;
                    requestLocationPermission();
                } else {
                    Log.i(Constants.TAG, "Location permission has already been granted. Starting scanning.");
                    permissions_granted = true;
                }
            } else {
                // the ACCESS_COARSE_LOCATION permission did not exist before M so....
                permissions_granted = true;
            }
            startScanning();
        } else {
            Log.d(Constants.TAG, "Already scanning");
            ble_scanner.stopScanning();
        }
    }

    /***********************************************************************************************
     *This method checks that permissions have been granted, clears the UI device list and then
     * tells the BleScanner object to start scanning.
     **********************************************************************************************/
    private void startScanning() {
        if (permissions_granted) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    ble_device_list_adapter.clear();
                    ble_device_list_adapter.notifyDataSetChanged();
                }
            });
            simpleToast(Constants.SCANNING,2000);
            ble_scanner.startScanning(this, SCAN_TIMEOUT);
        } else {
            Log.i(Constants.TAG, "Permission to perform Bluetooth scanning was not yet granted");
        }
    }


    /***********************************************************************************************
     * Handle requesting permisssion to perform scanning from the user...
     **********************************************************************************************/
    private void requestLocationPermission() {
        Log.i(Constants.TAG, "Location permission has NOT yet been granted. Requesting permission.");
        if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.ACCESS_COARSE_LOCATION)){
            Log.i(Constants.TAG, "Displaying location permission rationale to provide additional context.");
            final AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle("Permission Required");
            builder.setMessage("Please grant Location access so this application can perform Bluetooth scanning");
            builder.setPositiveButton(android.R.string.ok, null);
            builder.setOnDismissListener(new DialogInterface.OnDismissListener() {
                public void onDismiss(DialogInterface dialog) {
                    Log.d(Constants.TAG, "Requesting permissions after explanation");
                    ActivityCompat.requestPermissions(MainActivity.this, new String[]{Manifest.permission.ACCESS_COARSE_LOCATION}, REQUEST_LOCATION);
                }
            });
            builder.show();
        } else {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_COARSE_LOCATION}, REQUEST_LOCATION);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == REQUEST_LOCATION) {
            Log.i(Constants.TAG, "Received response for location permission request.");
            // Check if the only required permission has been granted
            if (grantResults.length == 1 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Location permission has been granted
                Log.i(Constants.TAG, "Location permission has now been granted. Scanning.....");
                permissions_granted = true;
                if (ble_scanner.isScanning()) {
                    startScanning();
                }
            }else{
                Log.i(Constants.TAG, "Location permission was NOT granted.");
            }
        } else {
            super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        }
    }

    private void simpleToast(String message, int duration) {
        toast = Toast.makeText(this, message, duration);
        toast.setGravity(Gravity.CENTER, 0, 0);
        toast.show();
    }

}
