package com.tailortoys.app.PowerUp;


import android.app.Activity;
import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.util.Log;
import android.view.animation.TranslateAnimation;
import android.widget.ImageView;
import android.widget.Switch;
import android.widget.TextView;

import com.tailortoys.app.PowerUp.util.Const;
import com.tailortoys.app.PowerUp.util.LowPassFilter;

import lib.smartlink.driver.BLESmartplaneService;

/**
 * @author Samit Vaidya
 * @date 04 March 2014
 * Refactored by: Radu Hambasan
 */

/**
 * Class in charge of the accelerometer and magnetometer callbacks
 */

public class SensorHandler implements SensorEventListener {
    private final String TAG = "SensorHandler";
    private BluetoothDelegate bluetoothDelegate;

    private SensorManager sensorManager;
    private Sensor accelerometer;
    private Sensor magnetometer;

    TextView hdgVal;
    ImageView compass;
    ImageView horizonImage;
    ImageView centralRudder;
    Switch rudderSwitch;

    private float[] gravity;
    private float[] geomagnetic;
    private float[] prevOrientation = new float[3];
    private float[] rotationMatrix = new float[9];
    private float[] inclinationMatrix = new float[9];
    private float[] newOrientation = new float[3];


    public SensorHandler(Activity activity, BluetoothDelegate bluetoothDelegate) {
        this.bluetoothDelegate = bluetoothDelegate;

        gravity = new float[3];
        geomagnetic = new float[3];
        prevOrientation = new float[3];
        newOrientation = new float[3];
        rotationMatrix = new float[9];
        inclinationMatrix = new float[9];

        /* The data set changes rapidly, so we need to set the views here,
         * and keep the references alive for the lifetime of the app
         */
        hdgVal = (TextView) activity.findViewById(R.id.hdgValue);
        compass = (ImageView) activity.findViewById(R.id.compass);
        horizonImage = (ImageView) activity.findViewById(R.id.imageHorizon);
        centralRudder = (ImageView) activity.findViewById(R.id.rulerMiddle);
        rudderSwitch = (Switch) activity.findViewById(R.id.rudderSwitch);

        sensorManager = (SensorManager) activity.getSystemService(Context.SENSOR_SERVICE);
        if (sensorManager == null) {
            Log.e(TAG, "Couldn't get the sensor service.");
            return;
        }
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        magnetometer = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
    }

    public void unregisterListener() {
        sensorManager.unregisterListener(this);
    }

    public void registerListener() {
        if (accelerometer != null && magnetometer != null) {
            sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_UI);
            sensorManager.registerListener(this, magnetometer, SensorManager.SENSOR_DELAY_UI);
        } else {
            Log.e(TAG, "no Accelerometer/Magnetometer!");
        }
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        final String[] compassDir = {"N", "NE", "E", "SE", "S", "SW", "W", "NW", "N"};
        /* TODO: explain the following two members better */
        // degrees between segments in between north, south, east and west
        final int DEGREES_PER_SEGMENT = Const.FULL_DEGREES / 8;
        // degrees between N, NE, E, SE, S, SW, W , NW, N
        final float DEGREES_PER_DIRECTION = (DEGREES_PER_SEGMENT / 2);

        switch (event.sensor.getType()) {
            case Sensor.TYPE_ACCELEROMETER:
                gravity = event.values;
                break;
            case Sensor.TYPE_MAGNETIC_FIELD:
                geomagnetic = event.values;
                break;
            default:
                break;
        }
        // if we miss either of these, we can't do much
        if (gravity == null || geomagnetic == null) {
            return;
        }

        // if the device is in free fall, getRotationMatrix returns false
        if (SensorManager.getRotationMatrix(rotationMatrix, inclinationMatrix, gravity, geomagnetic)) {
            SensorManager.getOrientation(rotationMatrix, newOrientation);

            // smooth the orientation
            newOrientation = LowPassFilter.filter(newOrientation, prevOrientation);
            // cache it
            prevOrientation = newOrientation;

            final int rollAngle = (int) Math.toDegrees(newOrientation[2]); //radian to degrees
            final int pitchAngle = (int) Math.toDegrees(newOrientation[1]);
            int azimuthAngle = (int) Math.toDegrees(newOrientation[0]);
            float compassAngle;

            double horizonVerticalMovement = 0.0;

            //limiting the values of pitch angle for the vertical movement of the horizon
            if (Const.PITCH_ANGLE_MIN < pitchAngle && pitchAngle < Const.PITCH_ANGLE_MAX) {
                horizonVerticalMovement = Const.SCALE_FOR_VERT_MOVEMENT_HORIZON * pitchAngle;
            } else if (pitchAngle <= Const.PITCH_ANGLE_MIN) {
                horizonVerticalMovement = Const.SCALE_FOR_VERT_MOVEMENT_HORIZON * Const.PITCH_ANGLE_MIN;
            } else if (pitchAngle >= Const.PITCH_ANGLE_MAX) {
                horizonVerticalMovement = Const.SCALE_FOR_VERT_MOVEMENT_HORIZON * Const.PITCH_ANGLE_MAX;
            }
            short newRudder = (short) (rollAngle * -Const.MAX_RUDDER_INPUT / Const.MAX_ROLL_ANGLE);

            if (azimuthAngle < 0) {
                // scaling angle from 0 to 360
                azimuthAngle += Const.FULL_DEGREES;
            }

            compassAngle = (azimuthAngle + DEGREES_PER_DIRECTION) / DEGREES_PER_SEGMENT;


            hdgVal.setText(compassDir[(int) compassAngle]);

            compass.setRotation(azimuthAngle);

            // translation animation, translating the image in the vertical direction
            TranslateAnimation translateHorizon = new TranslateAnimation(0, 0,
                    -(float) horizonVerticalMovement, (float) horizonVerticalMovement);
            // TODO: ^ why from -horizon?
            translateHorizon.setDuration(Const.ANIMATION_DURATION_MILLISEC);

            horizonImage.startAnimation(translateHorizon);

            // ruler movement, a bit faster than horizon movement for 3D effect
            centralRudder.setY((float) (-Const.RULER_MOVEMENT_SPEED * (horizonVerticalMovement + Const.RULER_MOVEMENT_HEIGHT)));

            horizonImage.setRotation(-rollAngle);


            BLESmartplaneService smartplaneService = bluetoothDelegate.getSmartplaneService();
            if (smartplaneService != null) {
                smartplaneService.setRudder(
                        (short) (rudderSwitch.isChecked() ? -newRudder : newRudder)
                );
            }
        }

    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
    }


}
