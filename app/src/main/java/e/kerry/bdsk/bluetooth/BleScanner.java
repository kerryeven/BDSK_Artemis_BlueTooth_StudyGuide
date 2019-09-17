package e.kerry.bdsk.bluetooth;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.util.Log;
import e.kerry.bdsk.Constants;
import java.util.ArrayList;
import java.util.List;
/***************************************************************************
  This creates the class, declares various member variables which we’
  need and creates the class constructor. The constructor takes a Context
  object as an argument so that we can use it to start an activity, which
  we need to do in the event that we find that Bluetooth is currently
  switched off.
        The Android BluetoothManager class provides us with an instance of
  BluetoothAdapter. We use it to check whether or not Bluetooth is currently
  switched on and if it is not, prompt the user to enable it.
******************************************************************************/
public class BleScanner {
    private BluetoothLeScanner scanner = null;
    private BluetoothAdapter bluetooth_adapter = null;
    private Handler handler = new Handler();
    private ScanResultsConsumer scan_results_consumer;
    private Context context;
    private boolean scanning=false;
    private String device_name_start="";
    public BleScanner(Context context) {
        this.context = context;
        final BluetoothManager bluetoothManager = (BluetoothManager) context.getSystemService(Context.BLUETOOTH_SERVICE);
        bluetooth_adapter = bluetoothManager.getAdapter();
// check bluetooth is available and on
        if (bluetooth_adapter == null || !bluetooth_adapter.isEnabled()) {
            Log.d(Constants.TAG, "Bluetooth is NOT switched on");
            Intent enableBtIntent = new Intent( BluetoothAdapter.ACTION_REQUEST_ENABLE);
            enableBtIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(enableBtIntent);
        }
        Log.d(Constants.TAG, "Bluetooth is switched on");
    }

/************************************************************************************************
 * Next, we add methods which allow another object to initiate Bluetooth scanning. A public
 * startScanning method which requires a time limit for scanning to be specified as an argument
 * as well as an instance of our ScanResultsConsumer interface so that callbacks can be made to
 * its methods during scanning.
 *************************************************************************************************/
    public void startScanning(final ScanResultsConsumer scan_results_consumer, long stop_after_ms) {
        if (scanning) {
            Log.d(Constants.TAG, "Already scanning so ignoring startScanning request");
            return;
        }
        if (scanner == null) {
            scanner = bluetooth_adapter.getBluetoothLeScanner();
            Log.d(Constants.TAG, "Created BluetoothScanner object");
        }
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (scanning) {
                    Log.d(Constants.TAG, "Stopping scanning");
                    scanner.stopScan(scan_callback);
                    setScanning(false);
                } }
        }, stop_after_ms);
        this.scan_results_consumer = scan_results_consumer;
        Log.d(Constants.TAG,"Scanning");
        List<ScanFilter> filters;
        filters = new ArrayList<ScanFilter>();
        // Can't make it see Bdsk with filter..tried "BDSK" and "Bdsk" here
        // Next try BDSK for name in arduino...= NO
        // Next Tried Mac Address = NO..YES if use setDeviceAddress
        // KHE - Next two lines added to filter for Devices named BDSK only...
        //ScanFilter filter = new ScanFilter.Builder().setDeviceName("BDSK").build();
        //KNOWN BUG THAT SCANFILTER NOT WORKING.. SO MANUALLY FILTER WITH IF OR CASE IN
        //  MAINACTIVITY CANDIDATEBLEDEVICE SEE COMMENT THERE.
        //Could not get to recognize name but did recognize mac. confirmed by filtering wrong mac
        // Above will not work...next two lines will..so looking at results from no filters
        //ScanFilter filter = new ScanFilter.Builder().setDeviceAddress("56:77:88:23:AB:EF").build();
        //filters.add(filter);
        ScanSettings settings = new ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build();
        setScanning(true);
        scanner.startScan(filters, settings, scan_callback);
    }

    public void stopScanning() {
        setScanning(false);
        Log.d(Constants.TAG, "Stopping scanning");
        scanner.stopScan(scan_callback);
    }

    /**********************************************************************************************
     * Now let’s create the ScanCallback object that our BluetoothLeScanner object needs. We’ll
     * create it as an inner class for convenience and implement the required onScanResult method
     * right here. This method is going to be called every time the scanner collects a Bluetooth
     * advertising packet which complies with our filtering criteria i.e. it includes
     * DEVICE_NAME=”BDSK”. As you can see, we check our ‘scanning’ boolean in case scanning has
     * just been terminated and then make a callback to the ScanResultsConsumer object which was
     * provided to this class’ constructor, passing the BluetoothDevice object that represents
     * the remote device which emitted the advertising packet. We also pass the content of the
     * advertising packet as a raw byte array and the received signal strength indicator. We don’t
     * use either of these items in the lab at present, but they might be useful to you, should
     * you decide to take the lab further.
     **********************************************************************************************/
    private ScanCallback scan_callback = new ScanCallback() {
        public void onScanResult(int callbackType, final ScanResult result) {
            if (!scanning) {
                return;
            }
            scan_results_consumer.candidateBleDevice(result.getDevice(), result.getScanRecord().getBytes(), result.getRssi());
        }
    };

    public boolean isScanning() {
        return scanning;
    }
    void setScanning(boolean scanning) {
        this.scanning = scanning;
        if (!scanning) {
            scan_results_consumer.scanningStopped();
        } else {
            scan_results_consumer.scanningStarted(); }
    }

    /***********************************************************************************************
     * Note that setScanning informs the ScanResultsConsumer object of changes in the scanning
     * state. This is useful so that the UI can be updated accordingly.
     * We’ve now got a class which can perform scanning for us when we ask it to and which will
     * provide details of any Bluetooth devices it finds to another object.
     * That other object will be our MainActivity object which underpins the scanning / scan
     * results screen of our application.
     **********************************************************************************************/
// end of class
}

