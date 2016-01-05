/*
 * Copyright (c) 2015 T4Connex.
 */

package com.tracker.journeys;

import android.app.AlertDialog;
import android.app.AlertDialog.Builder;
import android.app.Notification;
import android.app.NotificationManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Color;
import android.location.Location;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.provider.Settings;
import android.support.v4.app.NotificationCompat;
import android.support.v7.app.AppCompatActivity;
import android.transition.Explode;
import android.transition.Slide;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Chronometer;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;


import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesUtil;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.GoogleApiClient.ConnectionCallbacks;
import com.google.android.gms.common.api.GoogleApiClient.OnConnectionFailedListener;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.PolylineOptions;


import com.tracker.app.R;
import com.tracker.data.DCJourney;
import com.tracker.data.DCJourneyPoint;
import com.tracker.database.JourneyDataSource;
import com.tracker.singleton.TrackJourneySingleton;
import com.tracker.utilities.Utilities;

import java.util.ArrayList;




/**
 * Activity for tracking a journey.
 *
 *  @author Muhammad Azeem Anwar
 */

public class TrackJourneyActivity extends AppCompatActivity {
    private final static String PLAYSTATUS = "button.play";
    private final static String PAUSESTATUS = "button.pause";

    private TextView disVal, recordUnitTextView, switchTitleView, durationTextView, distanceTextView;
    private Chronometer durVal;
    private Switch trackingSwitch;

    private ImageButton delete, play, stop;
    private GoogleMap map;
    private GoogleApiClient locationClient = null;

    private boolean isTrackingStarted = false;
    private boolean isSharing = false;

    private Polyline route;
    LinearLayout sharingLayout = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {


        // Check if the device can use the new transition styles and apply them if it can.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {

            getWindow().setEnterTransition(new Explode());

            getWindow().setExitTransition(new Explode());

            getWindow().setReturnTransition(new Slide());

        }

        super.onCreate(savedInstanceState);

            setContentView(R.layout.activity_journey_track);

        dataInit();
        locationInit();
        handleIntent(getIntent());

        // Check if tracker is tracking
        if (TrackJourneySingleton.isTracking())
            startEvent(play);
    }

    @Override
    protected void onResume() {
        super.onResume();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (locationClient != null)
            locationClient.disconnect();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        // When user goes to JourneyDetailsActivity and presses delete it will
        // take him back to this screen
        // which it shouldn't. It should take him back to home activity. Setting
        // flags didn't do it, so
        // the only way I found is to start activity for results and get the
        // result in form of boolean "finish"
        if (requestCode == 100) {
            // Make sure the request was successful
            if (resultCode == RESULT_OK) {
                if (data.getExtras() != null) {
                    if (data.getExtras().getBoolean("finish"))
                        finish();
                }
            }
        }
    }

    /**
     * Initialises data
     */
    private void dataInit() {
        disVal = (TextView) findViewById(R.id.distanceValue);
        durVal = (Chronometer) findViewById(R.id.durationValue);
        switchTitleView = (TextView) findViewById(R.id.switchTitle);
        durationTextView = (TextView) findViewById(R.id.durationText);
        distanceTextView = (TextView) findViewById(R.id.distanceText);
        recordUnitTextView = (TextView) findViewById(R.id.recordUnitText);
        delete = (ImageButton) findViewById(R.id.deleteBtn);
        play = (ImageButton) findViewById(R.id.playBtn);
        stop = (ImageButton) findViewById(R.id.stopBtn);
        trackingSwitch = (Switch) findViewById(R.id.switch1);
        sharingLayout = (LinearLayout) findViewById(R.id.sharingLayout);
//        disVal.setTypeface(DCAppSingleton.getFont("font/Roboto-Medium.ttf", this));
//
//        durationTextView.setTypeface(DCAppSingleton.getFont("font/Roboto-Light.ttf", this));
//        distanceTextView.setTypeface(DCAppSingleton.getFont("font/Roboto-Light.ttf", this));
//		if (AppConfig.isSharingDisable() || !AppConfig.iSPARSESERVER()) {
//			sharingLayout.setVisibility(View.INVISIBLE);
//		}
        delete.setOnClickListener(onClickListener);
        play.setOnClickListener(onClickListener);
        stop.setOnClickListener(onClickListener);
//        if ( !AppConfig.isSharingDisable()) {

            trackingSwitch = (Switch) findViewById(R.id.switch1);
//            switchTitleView.setTypeface(DCAppSingleton.getFont("font/Roboto-Medium.ttf", this));
            trackingSwitch
                    .setOnCheckedChangeListener(new OnCheckedChangeListener() {
                        @Override
                        public void onCheckedChanged(CompoundButton buttonView,
                                                     boolean isChecked) {
                            isSharing = isChecked;
                            TrackJourneySingleton.isSwitchOn = isChecked;



                        }
                    });

            // if (TrackJourneySingleton.isSwitchOn) {
            trackingSwitch.setChecked(TrackJourneySingleton.isSwitchOn);
//        }
        // }else{
        //
        // }
        play.setTag(PAUSESTATUS);
        stop.setEnabled(false);

        // Get a handle to the Map Fragment
        map = ((MapFragment) getFragmentManager()
                .findFragmentById(R.id.mapView)).getMap();
        map.setMyLocationEnabled(true);

        // If user is tracking, we need to get the current status of the tracker
        durVal.setBase(TrackJourneySingleton.getTrackingDuration());
        disVal.setText(TrackJourneySingleton.getDistance());

        // Check if tracker has enough points to enable saving option
        if (TrackJourneySingleton.getJourneyPoints() >= 3 && !stop.isEnabled()) {
            stop.setEnabled(true);
            stop.setImageResource(R.drawable.stop_white);
        }


        // Create the polyline to display the route on the map.
        // The empty polyline needs to be created here regardless of whether any route points currently exist.
        // As this is usually called before any locations have been received.

        route = map.addPolyline(new PolylineOptions().width(10.0f)
                .color(Color.BLUE).geodesic(true));

        Log.d("TrackerView", "Created route: " + route.toString());

        // Check to see if any route points exist (for example, if the user is returning to this view while already tracking a journey
        // Add them to the polyline is they exist.

        if (TrackJourneySingleton.getRoutePoints() != null) {

            Log.d("TrackerView", "routePoints is not null, adding polyling from routePoints");
            route.setPoints(TrackJourneySingleton.getRoutePoints());

        } else {
            Log.d("TrackerView", "routePoints is null, not adding to polyline");
        }


    }

    private void restAlert() {

        new Builder(getApplicationContext())
                .setTitle("Error")
                .setMessage(
                        getResources().getString(R.string.journey_error_info))
                .setPositiveButton(android.R.string.ok,
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog,
                                                int which) {
                            }

                        }).show();

    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        // Must store the new intent otherwise getIntent() will return the old
        // one
        setIntent(intent);
        handleIntent(intent);
    }

    private void handleIntent(Intent intent) {
        if (intent == null || intent.getAction() == null)
            return;

        if (intent.getAction()
                .equals(getPackageName() + ".TrackActivity.start")) {
            if (play.getTag().equals(PAUSESTATUS))
                startEvent(play);
        } else if (intent.getAction().equals(
                getPackageName() + ".TrackActivity.pause")) {
            if (play.getTag().equals(PLAYSTATUS))
                stopEvent(play);
        }
    }

    /**
     * Initialises location of the user
     */
    private void locationInit() {
        if (!Utilities.checkNetworkEnabled(this)
                && !Utilities.checkGPSEnabled(this)) {
            Builder builder = new Builder(TrackJourneyActivity.this);
            builder.setMessage("Please turn on Location Services in your System Settings.");
            builder.setTitle("Notification");
            builder.setPositiveButton("Settings",
                    new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            Intent intent = new Intent(
                                    Settings.ACTION_LOCATION_SOURCE_SETTINGS);
                            startActivity(intent);
                        }
                    });

            builder.setNegativeButton("Cancel",
                    new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            dialog.dismiss();
                        }
                    });

            builder.setCancelable(false);
            builder.create().show();
        } else {
            // Check if Google Play Services are available
            if (GooglePlayServicesUtil
                    .isGooglePlayServicesAvailable(TrackJourneyActivity.this) == ConnectionResult.SUCCESS) {
                if (locationClient == null)
                    locationClient = new GoogleApiClient.Builder(this)

                            .addConnectionCallbacks(locationConnectionCallbacks)
                            .addOnConnectionFailedListener(connectionFailedListener)
                            .addApi(LocationServices.API)
                            .build();

                locationClient.connect();
            }
        }
    }

    /**
     * Handles what happens when location of the user is changed
     */
    private LocationListener locationListener = new LocationListener() {
        @Override
        public void onLocationChanged(Location location) {
            if (TrackJourneySingleton.getJourneyPoints() >= 3
                    && !stop.isEnabled()) {
                stop.setEnabled(true);
                stop.setImageResource(R.drawable.stop_white);
            }

            map.animateCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(
                            location.getLatitude(), location.getLongitude()), 15),
                    1000, null);

            if (TrackJourneySingleton.getRoutePoints() != null) {
                // Makes sure route exists

                Log.d("TrackerView", "routePoints is not null, trying to update polyline...");

                if (route != null) {

                    Log.d("TrackerView", "Route exists, trying to get route points...");

                    // Makes sure that any route points were recorded
                    if (TrackJourneySingleton.getRoutePoints() != null) {

                        Log.d("TrackerView", "Updating polyline with new route points");
                        route.setPoints(TrackJourneySingleton.getRoutePoints());


                    } else {

                        Log.d("TrackerView", "routePoints is null");
                    }
                } else {

                    Log.d("TrackerView", "Route is null. Cannot update.");
                }

                disVal.setText(TrackJourneySingleton.getDistance());
            }


            long time = 0;
            time = SystemClock.elapsedRealtime() - durVal.getBase();

            int h = (int) (time / 3600000);
            int m = (int) (time - h * 3600000) / 60000;
            if (m > 120 && TrackJourneySingleton.isRestAlertShowed == false) {
                TrackJourneySingleton.isRestAlertShowed = true;
                new Builder(TrackJourneyActivity.this)
                        .setTitle("Waring")
                        .setMessage("Please remember to take a break")
                        .setPositiveButton(android.R.string.ok,
                                new DialogInterface.OnClickListener() {
                                    public void onClick(DialogInterface dialog,
                                                        int which) {
                                    }

                                }).show();

            }
        }
    };

    /**
     * stop tracking.
     *
     * @param v
     */
    private void stopEvent(View v) {
        isTrackingStarted = false;
        // setTrackingInParse(false);
        isSharing = false;

        // Stop tracking
        TrackJourneySingleton.stopTracking();

        if (locationClient != null)
            locationClient.disconnect();

        durVal.stop();

        disVal.setText(TrackJourneySingleton.getDistance());

        // Calculate average speed (m/h)
        // Get distance in miles
        double distance = Double.valueOf(disVal.getText().toString());
        // Get duration in minutes
        long duration = (TrackJourneySingleton.getEndTime() - TrackJourneySingleton
                .getStartTime()) / (60 * 1000);

        double averageSpeed = distance / duration * 60;

        // Get journey
        DCJourney journey = TrackJourneySingleton.getJourney();

        JourneyDataSource dataSource = new JourneyDataSource(TrackJourneyActivity.this);
        dataSource.open();
        ArrayList<DCJourneyPoint> points = dataSource.getJourneyPoints(journey.getId());
        dataSource.close();
        if (points.size() < 1) {
            updateJourneyWhenNoPointSaved(journey);
        }
        if (journey != null) {
            journey.setAvgSpeed((float) averageSpeed);

            // meters/second
            float topSpeed = TrackJourneySingleton.getTopSpeed();

            // Convert it into miles/hour
            topSpeed = ((topSpeed * 0.0006f) * 60) * 60;

            journey.setTopSpeed(topSpeed);
            journey.setDistance(distance);
            journey.setDuration(duration);
            journey.setStartAddr("");
            journey.setEndAddr("");
            journey.setStartTime(TrackJourneySingleton.getStartTime());
            journey.setEndTime(TrackJourneySingleton.getEndTime());
            journey.setBusiness(true);

            // We done, so go to next screen
            Intent intent = new Intent(TrackJourneyActivity.this,
                    JourneyDetailsActivity.class);

            // Put the journey into intent
            intent.putExtra("journey", journey);
            // intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);

            // Now, we can reset tracker
            TrackJourneySingleton.reset();

            // Start a new activity
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);

            startActivityForResult(intent, 100);

//            overridePendingTransition(R.anim.slide_left_sub,
//                    R.anim.slide_left_main);
            finish();


            // Send a notification so the user knows that the app has started tracking, useful for debugging bluetooth tracking.

            NotificationCompat.Builder b = new NotificationCompat.Builder(this.getApplicationContext());

            b.setAutoCancel(true)
                    .setDefaults(Notification.DEFAULT_ALL)
                    .setWhen(System.currentTimeMillis())
                    .setSmallIcon(R.drawable.ic_launcher)
                    .setTicker("Journey tracker stopped")
                    .setContentTitle("Journey tracker stopped")
                    .setContentText("App recorded a " + distance + " mile journey")
                    .setDefaults(Notification.DEFAULT_LIGHTS)
                    .setVibrate(new long[]{500, 500});

            NotificationManager notificationManager = (NotificationManager) this.getApplicationContext().getSystemService(Context.NOTIFICATION_SERVICE);
            notificationManager.notify(1, b.build());

            Log.d("TrackerView", "Sending end journey notification");


        } else {
            Toast.makeText(TrackJourneyActivity.this,
                    "Journey id was missing",
                    Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Pauses tracking.
     *
     * @param v
     */
    private void pauseEvent(View v) {
        ((ImageButton) v).setImageResource(R.drawable.play_white);
        v.setTag(PAUSESTATUS);
        // long d = TrackJourneySingleton.getTrackingDuration();
        durVal.stop();
        TrackJourneySingleton.stopTracking();

        if (locationClient != null)
            locationClient.disconnect();
    }

    /**
     * Starts tracking
     *
     * @param v
     */
    private void startEvent(View v) {
        ((ImageButton) v).setImageResource(R.drawable.pause_white);

        v.setTag(PLAYSTATUS);

        // Start tracking a journey
        // First launch tracker that will work in the background
        TrackJourneySingleton.startTracking(TrackJourneyActivity.this);

        durVal.setBase(TrackJourneySingleton.getTrackingDuration());

        // Then start local chronometer, so that it displays duration of the
        // journey
        durVal.start();
        if (locationClient == null) {
            locationClient = new GoogleApiClient.Builder(this)
                    .addApi(LocationServices.API)
                    .addConnectionCallbacks(locationConnectionCallbacks)
                    .addOnConnectionFailedListener(connectionFailedListener)
                    .build();

            locationClient.connect();
        } else {
            locationClient.connect();
        } // Indicate that tracking has started
        isTrackingStarted = true;

        // Check if user wants to share his location
//        if (isSharing)
//            setTrackingInParse(true);


        // Send a notification to let the user know that the tracker has stopped, useful if they are using automatic tracking with bluetooth.

        NotificationCompat.Builder b = new NotificationCompat.Builder(this.getApplicationContext());

        b.setAutoCancel(true)
                .setDefaults(Notification.DEFAULT_ALL)
                .setWhen(System.currentTimeMillis())
                .setSmallIcon(R.drawable.ic_launcher)
                .setTicker("Journey tracker started")
                .setContentTitle("Journey tracker started")
                .setContentText("App is now tracking your journey")
                .setDefaults(Notification.DEFAULT_LIGHTS)
                .setVibrate(new long[]{500, 500});

        NotificationManager notificationManager = (NotificationManager) this.getApplicationContext().getSystemService(Context.NOTIFICATION_SERVICE);
        notificationManager.notify(1, b.build());

        Log.d("TrackerView", "Sending start journey notification");


    }

    private boolean isLocationServicesActive() {
        if (!Utilities.checkNetworkEnabled(this)
                && !Utilities.checkGPSEnabled(this)) {
            Builder builder = new Builder(TrackJourneyActivity.this);
            builder.setMessage("Please turn on Location Services in your System Settings.");
            builder.setTitle("Notification");
            builder.setPositiveButton("Settings",
                    new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            Intent intent = new Intent(
                                    Settings.ACTION_LOCATION_SOURCE_SETTINGS);
                            startActivity(intent);
                        }
                    });

            builder.setNegativeButton("Cancel",
                    new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            dialog.dismiss();
                        }
                    });

            builder.setCancelable(false);
            builder.create().show();
            return false;
        }
        return true;
    }

    private View.OnClickListener onClickListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            if (v == play) {
                if (isLocationServicesActive()) {

                    if (v.getTag().equals(PLAYSTATUS))
                        pauseEvent(v);
                    else
                        startEvent(v);

                }


            }
            // Cancel tracking
            else if (v == delete) {
                // Warn user before discarding journey
                Builder builder = new Builder(
                        TrackJourneyActivity.this);
                builder.setTitle("Discard Journey");
                builder.setMessage("Do you want to discard this journey?");

                // Set positive button
                builder.setPositiveButton(android.R.string.ok,
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog,
                                                int which) {
                                // User wants to discard this journey, so...
                                if (locationClient != null)
                                    locationClient.disconnect();

                                durVal.stop();

                                // Delete journey from the database in the
                                // background
                                if (TrackJourneySingleton.getJourney() != null)
                                    new deleteTask(TrackJourneySingleton
                                            .getJourney().getId()).execute();

                                // Reset tracker
                                TrackJourneySingleton.reset();

                                // Finish this activity
                                finish();
//                                overridePendingTransition(R.anim.null_anim,
//                                        R.anim.slide_out);
                            }
                        });

                // Set cancel button
                builder.setNegativeButton(android.R.string.cancel,
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int id) {
                                // Close dialog
                                dialog.dismiss();
                            }
                        });

                // Display dialog
                AlertDialog dialog = builder.create();
                dialog.show();
            } else if (v == stop) {
                isTrackingStarted = false;
//                if ( !AppConfig.isSharingDisable()) {
//
//                    setTrackingInParse(false);
//                }
                isSharing = false;

                // Stop tracking
                TrackJourneySingleton.stopTracking();

                if (locationClient != null)
                    locationClient.disconnect();

                durVal.stop();

                disVal.setText(TrackJourneySingleton.getDistance());

                // Calculate average speed (m/h)
                // Get distance in miles
                double distance = Double.valueOf(disVal.getText().toString());
                // Get duration in minutes
                long duration = (TrackJourneySingleton.getEndTime() - TrackJourneySingleton
                        .getStartTime()) / (60 * 1000);

                double averageSpeed = distance / duration * 60;

                // Get journey
                DCJourney journey = TrackJourneySingleton.getJourney();
                JourneyDataSource dataSource = new JourneyDataSource(TrackJourneyActivity.this);
                dataSource.open();
                ArrayList<DCJourneyPoint> points = dataSource.getJourneyPoints(journey.getId());
                dataSource.close();


                if (journey != null) {
                    journey.setAvgSpeed((float) averageSpeed);

                    // meters/second
                    float topSpeed = TrackJourneySingleton.getTopSpeed();

                    // Convert it into miles/hour
                    topSpeed = ((topSpeed * 0.0006f) * 60) * 60;

                    journey.setTopSpeed(topSpeed);
                    journey.setDistance(distance);
                    journey.setDuration(duration);
                    journey.setStartAddr("");
                    journey.setEndAddr("");
                    journey.setStartTime(TrackJourneySingleton.getStartTime());
                    journey.setEndTime(TrackJourneySingleton.getEndTime());
                    journey.setBusiness(true);

                    // We done, so go to next screen
                    Intent intent = new Intent(TrackJourneyActivity.this,
                            JourneyDetailsActivity.class);

                    // Put the journey into intent
                    intent.putExtra("journey", journey);
                    // intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);

                    // Now, we can reset tracker
                    TrackJourneySingleton.reset();

                    // Start a new activity
                    startActivityForResult(intent, 100);
//                    overridePendingTransition(R.anim.slide_left_sub,
//                            R.anim.slide_left_main);
                    finish();
                }
            }
        }
    };

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
//            overridePendingTransition(R.anim.null_anim, R.anim.slide_out);
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        return super.onCreateOptionsMenu(menu);
    }

    private ConnectionCallbacks locationConnectionCallbacks = new ConnectionCallbacks() {



        @Override
        public void onConnectionSuspended(int i) {
            LocationServices.FusedLocationApi.removeLocationUpdates(locationClient, locationListener);
        }

        @Override
        public void onConnected(Bundle arg0) {
            Location
//                    myLocation = locationClient.getLastLocation();
                    myLocation = LocationServices.FusedLocationApi.getLastLocation(
                    locationClient);

            if (myLocation != null) {
                double dLatitude = myLocation.getLatitude();
                double dLongitude = myLocation.getLongitude();
                map.animateCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(
                        dLatitude, dLongitude), 15), 2000, null);
            } else {
                Toast.makeText(TrackJourneyActivity.this,
                        "Unable to fetch your current location",
                        Toast.LENGTH_SHORT).show();
            }

            // For best results, the tracker here needs to match the settings used in the tracker singleton
            // This will ensure that the map updates roughly every time the tracker receives a new location
            // Ideally this should use a delegate to update every time the tracker singleton receives a new location
            // This way we won't need to init two locationRequests.

            LocationRequest locationRequest = LocationRequest.create();
            locationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
            locationRequest.setInterval(1000);


            LocationServices.FusedLocationApi.requestLocationUpdates(locationClient, locationRequest, locationListener);

        }
    };

    private OnConnectionFailedListener connectionFailedListener = new OnConnectionFailedListener() {
        @Override
        public void onConnectionFailed(ConnectionResult arg0) {
            Log.e("Connection", "ConnectionFailed");
        }
    };



    private class deleteTask extends AsyncTask<String, Void, Boolean> {
        private long journeyId;

        public deleteTask(long journeyId) {
            this.journeyId = journeyId;
        }

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
        }

        @Override
        protected Boolean doInBackground(String... keywords) {
            JourneyDataSource dataSource = new JourneyDataSource(
                    TrackJourneyActivity.this);

            dataSource.open();
            dataSource.deleteJourney(journeyId);
            dataSource.deleteBehaviourPoints(journeyId);
            dataSource.close();

            return true;
        }

        @Override
        protected void onPostExecute(Boolean result) {
            super.onPostExecute(result);
        }
    }

    // test method not used yest
    private void checkPlayServices() {
        int status = GooglePlayServicesUtil.isGooglePlayServicesAvailable(this);
        if (status != ConnectionResult.SUCCESS) {
            if (GooglePlayServicesUtil.isUserRecoverableError(status)) {
                GooglePlayServicesUtil.getErrorDialog(status, this, 0).show();
            } else {
//                Toast.makeText("Until you update your Google Play Services, this app cannot run on this phone");

                Toast.makeText(TrackJourneyActivity.this,
                        "Until you update your Google Play Services, this app cannot run on this phone",
                        Toast.LENGTH_SHORT).show();
                finish();
            }
        }
    }

    private void updateJourneyWhenNoPointSaved(DCJourney journey) {

    }
}