package com.harsh.profit.fitness;


import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.work.Data;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.fitness.Fitness;
import com.google.android.gms.fitness.FitnessOptions;
import com.google.android.gms.fitness.data.Bucket;
import com.google.android.gms.fitness.data.DataPoint;
import com.google.android.gms.fitness.data.DataSet;
import com.google.android.gms.fitness.data.DataType;
import com.google.android.gms.fitness.data.Field;
import com.google.android.gms.fitness.request.DataReadRequest;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class DataEntryReceiver extends Worker {

    private static final String TAG = "Data Entry";
    FirebaseFirestore db = FirebaseFirestore.getInstance();
    long day;

    public static GoogleSignInAccount googleSignInAccount;

    long noOfSteps, moveMinutes;
    double noOfCalories, distanceWalked;
    ZonedDateTime startTime,endTime;
    Context context;

    public DataEntryReceiver(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
        this.context = context;
    }

    @Override
    @NonNull
    public Result doWork() {


        endTime = null;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            endTime = LocalDateTime.now().atZone(ZoneId.systemDefault());
        }
        startTime = null;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            startTime = LocalDateTime.of(LocalDate.from(LocalDateTime.now()), LocalTime.MIDNIGHT).atZone(ZoneId.systemDefault());
        }
        Log.i(TAG, "Range Start: " + startTime.toString());
        Log.i(TAG, "Range End: " + endTime.toString());

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            DataReadRequest readRequest = new DataReadRequest.Builder()
                    .aggregate(DataType.TYPE_STEP_COUNT_DELTA)
                    .aggregate(DataType.AGGREGATE_CALORIES_EXPENDED)
                    .aggregate(DataType.TYPE_MOVE_MINUTES)
                    .bucketByTime(1, TimeUnit.DAYS)
                    .setTimeRange(startTime.toEpochSecond(), endTime.toEpochSecond(), TimeUnit.SECONDS)
                    .build();
            Fitness.getHistoryClient(context, GoogleSignIn.getLastSignedInAccount(context))
                    .readData(readRequest)
                    .addOnSuccessListener(response -> {
                        for (Bucket bucket : response.getBuckets()) {
                            for (DataSet dataSet : bucket.getDataSets()) {
                                dumpDataSet(dataSet);
                            }
                        }
                    })
                    .addOnFailureListener(e ->
                            Log.w(TAG, "There was an error reading data from Google Fit", e));
        }
        try {
            Thread.sleep(5000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        FirebaseAuth mAuth = FirebaseAuth.getInstance();
        FirebaseUser user = mAuth.getCurrentUser();
        enterData(user.getUid());
        try {
            Thread.sleep(5000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        return Result.success();
    }

    private void dumpDataSet(DataSet dataSet) {
        Log.i(TAG, "Data returned for Data type: " + dataSet.getDataType().getName());

        if (dataSet.isEmpty()) {
            if (dataSet.getDataType().getName().equals("com.google.calories.expended"))
                noOfCalories = 0;

            if (dataSet.getDataType().getName().equals("com.google.active_minutes"))
                moveMinutes = 0;

            if (dataSet.getDataType().getName().equals("com.google.step_count.delta"))
                noOfSteps = 0;
        }

        for (DataPoint dp : dataSet.getDataPoints()) {
            Log.i(TAG, "Data point:");
            Log.i(TAG, "\tType: " + dp.getDataType().getName());
            Log.i(TAG, "\tStart: " + startTime.toString());
            Log.i(TAG, "\tEnd: " + endTime.toString());
            Log.i(TAG, "\tEnd: " + Field.FIELD_STEPS.getName());
            for (Field field : dp.getDataType().getFields()) {

                Log.i(TAG, "\tfName: " + field.getName() + " value = " + dp.getValue(field));

                if (field.equals(Field.FIELD_STEPS))
                    noOfSteps = dp.getValue(field).asInt();
                if (field.equals(Field.FIELD_CALORIES))
                    noOfCalories = dp.getValue(field).asFloat();
                if (field.equals(Field.FIELD_DURATION))
                    moveMinutes = dp.getValue(field).asInt();
            }
        }

        distanceWalked = 0.000639 * noOfSteps;

    }


    private void enterData(String uid){


        LocalDateTime date = null;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            date = LocalDateTime.now();
        }
        HashMap<String, Object> map = new HashMap<>();
        map.put("Date",date.toString());
        map.put("Steps",noOfSteps);
        map.put("Calories",noOfCalories);
        map.put("Move Minutes",moveMinutes);
        map.put("Distance",distanceWalked);
        Log.i("Map is", map.toString());

        db.collection("users").document(uid).
                collection("Days").document("Total").get().addOnSuccessListener(new OnSuccessListener<DocumentSnapshot>() {
            @Override
            public void onSuccess(DocumentSnapshot documentSnapshot) {
                Log.i("Total",documentSnapshot.get("Total") + "");
                day = ((Long) documentSnapshot.get("Total")) + 1;

                db.collection("users").document(uid).
                        collection("Days").document(day + "").set(map).addOnSuccessListener(new OnSuccessListener<Void>() {
                    @Override
                    public void onSuccess(Void unused) {
                        HashMap<String, Integer> map = new HashMap<>();
                        map.put("Total", (int) (day));
                        db.collection("users").document(uid).
                                collection("Days").document("Total").set(map).addOnSuccessListener(new OnSuccessListener<Void>() {
                            @Override
                            public void onSuccess(Void unused) {
                            }
                        }).addOnFailureListener(new OnFailureListener() {
                            @Override
                            public void onFailure(@NonNull Exception e) {
                            }
                        });

                    }
                }).addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {
                    }
                });
            }
        });
    }

}

