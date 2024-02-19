package com.harsh.profit.fitness;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Build;
import androidx.annotation.NonNull;

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
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.material.navigation.NavigationView;

import androidx.core.app.ActivityCompat;
import androidx.core.app.ActivityOptionsCompat;
import androidx.core.content.ContextCompat;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.app.AppCompatActivity;
import android.os.Bundle;
import androidx.appcompat.widget.Toolbar;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import android.os.Handler;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.firebase.ui.auth.AuthUI;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.GetTokenResult;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.hookedonplay.decoviewlib.DecoView;
import com.hookedonplay.decoviewlib.charts.DecoDrawEffect;
import com.hookedonplay.decoviewlib.charts.SeriesItem;
import com.hookedonplay.decoviewlib.events.DecoEvent;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class MainActivity extends AppCompatActivity implements NavigationView.OnNavigationItemSelectedListener {

    private static final String TAG = "Fitness App";
    public static float evsteps;
    public static int cont = 0;

    private final int mInterval = 60000 * 5; // 5 seconds by default, can be changed later
    private Handler mHandler;

    public static float mSeriesMax = 0f;
    boolean activityRunning;

    DecoView mDecoView;

    long noOfSteps, moveMinutes;
    double noOfCalories, distanceWalked;

    private NavigationView navigationView;
    private DrawerLayout drawerLayout;

    TextView steps,distance,calories,duration;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        navigationView = findViewById(R.id.navigation_view);
        navigationView.setNavigationItemSelectedListener(this);

        steps = findViewById(R.id.steps);
        distance = findViewById(R.id.distance);
        calories = findViewById(R.id.calories);
        duration = findViewById(R.id.duration);

        View mHeaderView = navigationView.getHeaderView(0);

        TextView nameId = mHeaderView.findViewById(R.id.txt1);
        nameId.setText(LoginActivity.USER_NAME);
        TextView emailId = mHeaderView.findViewById(R.id.txt2);
        emailId.setText(LoginActivity.USER_EMAIL);
        drawerLayout = findViewById(R.id.drawer);
        ActionBarDrawerToggle actionBarDrawerToggle =
                new ActionBarDrawerToggle(this, drawerLayout, toolbar, R.string.open_drawer, R.string.close_drawer) {
                    @Override
                    public void onDrawerClosed(View drawerView) {
                        super.onDrawerClosed(drawerView);
                    }

                    @Override
                    public void onDrawerOpened(View drawerView) {
                        super.onDrawerOpened(drawerView);
                    }
                };

        checkDB();

        drawerLayout.addDrawerListener(actionBarDrawerToggle);
        GoogleSignInAccount account = GoogleSignIn.getLastSignedInAccount(this);

        Log.d("Account" , account.getEmail());

        actionBarDrawerToggle.syncState();

        Log.d("SetGoal mseries", String.valueOf(SetGoalActivity.mSeries));
        if (mSeriesMax == 0) {
            mSeriesMax = LoginActivity.mSeries1;
        }




        mDecoView = findViewById(R.id.dynamicArcView);
        String[] permissions = {Manifest.permission.ACTIVITY_RECOGNITION, Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.READ_EXTERNAL_STORAGE};

        if (!checkPermission(permissions)) {
            ActivityCompat.requestPermissions(this,
                    permissions,
                    1);
        }


        FitnessOptions fitnessOptions = FitnessOptions.builder()
                .addDataType(DataType.TYPE_STEP_COUNT_DELTA, FitnessOptions.ACCESS_READ)
                .addDataType(DataType.TYPE_STEP_COUNT_CUMULATIVE, FitnessOptions.ACCESS_READ)
                .build();

        if (!GoogleSignIn.hasPermissions(GoogleSignIn.getLastSignedInAccount(this), fitnessOptions)) {
            GoogleSignIn.requestPermissions(
                    this,
                    101,
                    GoogleSignIn.getLastSignedInAccount(this),
                    fitnessOptions);
        }


        mHandler = new Handler();
        connectSensor.run();

        DataEntryReceiver.googleSignInAccount = GoogleSignIn.getLastSignedInAccount(this);
        PeriodicWorkRequest workRequest = new PeriodicWorkRequest
                .Builder(DataEntryReceiver.class, 15, TimeUnit.MINUTES)
                .addTag("Entry")
                .build();
        WorkManager.getInstance(getApplicationContext()).enqueueUniquePeriodicWork("Entry", ExistingPeriodicWorkPolicy.REPLACE, workRequest);


    }

    Runnable connectSensor = new Runnable() {
        @Override
        public void run() {
            FitnessOptions fitnessOptions = FitnessOptions.builder()
                    .addDataType(DataType.TYPE_STEP_COUNT_DELTA, FitnessOptions.ACCESS_READ)
                    .addDataType(DataType.TYPE_STEP_COUNT_CUMULATIVE, FitnessOptions.ACCESS_READ)
                    .build();

            GoogleSignInAccount googleSignInAccount = GoogleSignIn.getAccountForExtension(getApplicationContext(), fitnessOptions);

            Fitness.getRecordingClient(getApplicationContext(), googleSignInAccount)
                    .subscribe(DataType.TYPE_STEP_COUNT_CUMULATIVE)
                    .addOnSuccessListener(new OnSuccessListener<Void>() {
                        @Override
                        public void onSuccess(Void unused) {
                            Log.i(TAG, "Data source for STEP_COUNT_DELTA found!");
                        }
                    })
                    .addOnFailureListener(e ->
                            Log.e(TAG, "Find data sources request failed", e));
            Fitness.getRecordingClient(getApplicationContext(), googleSignInAccount)
                    .subscribe(DataType.TYPE_CALORIES_EXPENDED);


            ZonedDateTime endTime = null;
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                endTime = LocalDateTime.now().atZone(ZoneId.systemDefault());
            }
            ZonedDateTime startTime = null;
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
                ZonedDateTime finalStartTime = startTime;
                ZonedDateTime finalEndTime = endTime;
                Fitness.getHistoryClient(getApplicationContext(), GoogleSignIn.getAccountForExtension(getApplicationContext(), fitnessOptions))
                        .readData(readRequest)
                        .addOnSuccessListener(response -> {
                            for (Bucket bucket : response.getBuckets()) {
                                for (DataSet dataSet : bucket.getDataSets()) {
                                    dumpDataSet(dataSet, finalStartTime, finalEndTime);
                                }
                            }
                        })
                        .addOnFailureListener(e ->
                                Log.w(TAG, "There was an error reading data from Google Fit", e));

            }

            mHandler.postDelayed(connectSensor,mInterval);
        }
    };

    private void dumpDataSet(DataSet dataSet, ZonedDateTime startTime, ZonedDateTime endTime) {
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

        evsteps = noOfSteps;
        distanceWalked = 0.000639 * noOfSteps;


        steps.setText(noOfSteps + "");
        distance.setText(distanceWalked+"");
        calories.setText(noOfCalories+"");
        duration.setText(moveMinutes+"");

    }


    public boolean checkPermission(String[] permissions) {
        for (String permission : permissions) {
            if (ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_DENIED) {
                return false;
            }
        }
        return true;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == 1) {

            if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            } else {
                Toast.makeText(this, "The app requires activity access", Toast.LENGTH_SHORT).show();
            }

            if (grantResults[1] == PackageManager.PERMISSION_GRANTED) {
            } else {
                Toast.makeText(this, "The app requires location access", Toast.LENGTH_SHORT).show();
            }

            if (grantResults[2] == PackageManager.PERMISSION_GRANTED) {
            } else {
                Toast.makeText(this, "The app requires storage access", Toast.LENGTH_SHORT).show();
            }

            if (grantResults[3] == PackageManager.PERMISSION_GRANTED) {
            } else {
                Toast.makeText(this, "The app requires storage access", Toast.LENGTH_SHORT).show();
            }
        }
        connectSensor.run();
    }

    private void checkDB(){
        FirebaseAuth mAuth = FirebaseAuth.getInstance();
        FirebaseUser user = mAuth.getCurrentUser();
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        Map<String,Long> map = new HashMap<>();
        map.put("Total",0L);
        CollectionReference ref = db.collection("users").document(user.getUid())
                .collection("Days");
        ref.document("Total").get().addOnCompleteListener(new OnCompleteListener<DocumentSnapshot>() {
            @Override
            public void onComplete(@NonNull Task<DocumentSnapshot> task) {
                if(task.getResult().getData() == null){
                    ref.document("Total").set(map);
                    Log.i("Database" , "Total");
                }
            }
        });
    }

    @Override
    public boolean onNavigationItemSelected(MenuItem item) {

        int id = item.getItemId();

        switch (id) {
            case R.id.item1:
                Intent intent = new Intent(this, MainActivity.class);
                startActivity(intent);
                break;
            case R.id.item2:
                intent = new Intent(this, OverviewActivity.class);
                startActivity(intent);
                break;
            case R.id.item3:
                ReportGenerator generator = new ReportGenerator();
                generator.generatePdf(MainActivity.this);
                break;
            case R.id.item4:
                intent = new Intent(this, AccountActivity.class);
                startActivity(intent);
                break;
            case R.id.item5:
                AuthUI.getInstance().signOut(this).addOnCompleteListener(new OnCompleteListener<Void>() {
                    public void onComplete(@NonNull Task<Void> task) {
                        Toast.makeText(MainActivity.this, "Signed out successfully", Toast.LENGTH_SHORT).show();
                    }
                });
                Intent myIntent = new Intent(this, LoginActivity.class);
                myIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);// clear back stack
                startActivity(myIntent);
                finish();
            default:
                break;
        }
        drawerLayout.closeDrawer(GravityCompat.START);
        return true;
    }

}
