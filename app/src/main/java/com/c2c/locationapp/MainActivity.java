package com.c2c.locationapp;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Location;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.util.Log;
import android.widget.Button;
import android.widget.Toast;

import com.google.android.material.snackbar.Snackbar;

public class MainActivity extends AppCompatActivity implements SharedPreferences.OnSharedPreferenceChangeListener {

    public static final String TAG = MainActivity.class.getSimpleName();

    // for checking runtime permission
    private static final int REQUEST_PERMISSION_REQUEST_CODE = 34;

    //broadcast receiver used to listen from broadcasts from service
    private LocationUpdateReceiver mLocationUpdateReceiver;

    //reference to service used to get location updates
    private LocationUpdatesService mService = null;

    //tracks bound state of service
    private boolean mBound = false;

    //UI elements
    private Button mRequestLocationUpdatesButton;
    private Button mRemoveLocationUpdatesButton;

    //monitor state of the connection to the service
    private final ServiceConnection mServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
            LocationUpdatesService.LocalBinder binder = (LocationUpdatesService.LocalBinder) iBinder;
            mService = binder.getService();
            mBound = true;
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            mService = null;
            mBound = false;
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mLocationUpdateReceiver = new LocationUpdateReceiver();
        setContentView(R.layout.activity_main);

        if(Utils.requestingLocationUpdates(this)) {
            if (!checkPermissions()) {
                requestPermissions();
            }
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        PreferenceManager.getDefaultSharedPreferences(this)
                .registerOnSharedPreferenceChangeListener(this);

        mRequestLocationUpdatesButton = findViewById(R.id.request_location_update_button);
        mRemoveLocationUpdatesButton = findViewById(R.id.remove_location_updates_button);

        mRequestLocationUpdatesButton.setOnClickListener(view -> {
            if(!checkPermissions()) {
                requestPermissions();
            } else {
                //requestLocationService
                mService.requestLocationRequest();
            }
        });

        mRemoveLocationUpdatesButton.setOnClickListener(view -> {
            //removeLocationService
            mService.removeLocationUpdates();
        });

        // restore state of buttons when activity (re)launches
        setButtonState(Utils.requestingLocationUpdates(this));

        //bind to service
        //if service in foreground mode, this signals to service that since activity is in foreground,
        //service can exit foreground mode
        bindService(new Intent(this, LocationUpdatesService.class), mServiceConnection,
                Context.BIND_AUTO_CREATE);
    }

    @Override
    protected void onResume() {
        super.onResume();
        LocalBroadcastManager.getInstance(this).registerReceiver(mLocationUpdateReceiver,
                new IntentFilter(LocationUpdatesService.ACTION_BROADCAST));
    }

    @Override
    protected void onPause() {
        super.onPause();
        LocalBroadcastManager.getInstance(this).unregisterReceiver(mLocationUpdateReceiver);
    }

    @Override
    protected void onStop() {
        if (mBound) {
            //unbind from this service
            //if service in foreground mode, this signals to service that since activity is not in foreground,
            //service can promote itself to a foreground mode
            unbindService(mServiceConnection);
            mBound = false;
        }
        PreferenceManager.getDefaultSharedPreferences(this)
                .unregisterOnSharedPreferenceChangeListener(this);
        super.onStop();
    }

    //returns current state of permission needed
    private boolean checkPermissions() {
        return PackageManager.PERMISSION_GRANTED == ActivityCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_FINE_LOCATION);
    }

    private void requestPermissions() {
        boolean shouldProvideRationale =
                ActivityCompat.shouldShowRequestPermissionRationale(this,
                        Manifest.permission.ACCESS_FINE_LOCATION);

        // additional rationale if user previously denied but didn't check don't ask again checkbox
        if(shouldProvideRationale) {
            Log.i(TAG, "Displaying permission rationale to provide additional context.");
            Snackbar.make(
                    findViewById(R.id.activity_main),
                    R.string.permission_rationale,
                    Snackbar.LENGTH_INDEFINITE)
                    //requesting permission
                    .setAction(R.string.ok, view -> ActivityCompat.requestPermissions(MainActivity.this,
                            new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                            REQUEST_PERMISSION_REQUEST_CODE))
                    .show();
        } else {
            Log.i(TAG, "Requesting Permission");
            //if user user denied permision previously and checked never ask again
            ActivityCompat.requestPermissions(MainActivity.this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                    REQUEST_PERMISSION_REQUEST_CODE);
        }
    }

    //Callback received when a permission request has received
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        Log.i(TAG, "onRequestPermissionsResult");
        if(requestCode == REQUEST_PERMISSION_REQUEST_CODE) {
            if (grantResults.length <= 0) {
                // if user interaction interrupted, permission request is cancelled and empty array is received
                Log.i(TAG, "User interaction was cancelled");
            } else if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // permission granted
                //requestLocationUpdate
                mService.requestLocationRequest();
            } else {
                //permission denied
                setButtonState(false);
                Snackbar.make(findViewById(
                        R.id.activity_main),
                        R.string.permission_denied_explanation,
                        Snackbar.LENGTH_INDEFINITE)
                        .setAction(R.string.settings, view -> {
                            //intent to app settings screen
                            Intent intent = new Intent();
                            intent.setAction(
                                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                            Uri uri = Uri.fromParts("package",
                                    BuildConfig.APPLICATION_ID, null);
                            intent.setData(uri);
                            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                            startActivity(intent);
                        })
                        .show();
            }
        }
    }

    private class LocationUpdateReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            Location location = intent.getParcelableExtra(LocationUpdatesService.EXTRA_LOCATION);
            if (location != null) {
                Toast.makeText(MainActivity.this,  Utils.getLocationText(location), Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String s) {
        // update buttons state depending on location updates
        if (s.equals(Utils.KEY_REQUESTING_LOCATION_UPDATES)) {
            setButtonState(sharedPreferences.getBoolean(Utils.KEY_REQUESTING_LOCATION_UPDATES,
                    false));
        }
    }

    private void setButtonState(boolean requestingLocationUpdates) {
        if(requestingLocationUpdates) {
            mRequestLocationUpdatesButton.setEnabled(false);
            mRemoveLocationUpdatesButton.setEnabled(true);
        } else {
            mRequestLocationUpdatesButton.setEnabled(true);
            mRemoveLocationUpdatesButton.setEnabled(false);
        }
    }
}