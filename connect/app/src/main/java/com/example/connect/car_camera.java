package com.example.connect;


import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;

import android.content.ContentValues;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.RemoteException;
import android.provider.MediaStore;
import android.util.Log;
import android.util.Size;
import java.util.Arrays;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.InputStream;
import java.net.Socket;
import java.util.Collection;
import java.util.List;
import android.view.View;
import android.webkit.MimeTypeMap;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

//import com.estimote.coresdk.observation.region.beacon.BeaconRegion;
//import com.estimote.coresdk.recognition.packets.Beacon;
//import com.estimote.coresdk.service.BeaconManager;

import com.estimote.coresdk.observation.region.beacon.BeaconRegion;
import com.google.common.util.concurrent.ListenableFuture;

import org.altbeacon.beacon.BeaconConsumer;
import org.checkerframework.checker.units.qual.C;

import java.io.File;
import java.io.IOException;
import java.util.Date;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.FormBody;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;



import org.altbeacon.beacon.BeaconManager;
import org.altbeacon.beacon.BeaconParser;
import org.altbeacon.beacon.Beacon;
import org.altbeacon.beacon.Region;
import org.altbeacon.beacon.RangeNotifier;
import org.altbeacon.beacon.startup.BootstrapNotifier;



public class car_camera extends AppCompatActivity implements View.OnClickListener,BeaconConsumer{

    private ListenableFuture<ProcessCameraProvider> cameraProviderFuture;
    private PreviewView previewView;
    private Button testing;
    private Button collect_data;
    private TextView message_car_camera;

    private ImageView imageView;
    private ImageCapture imageCapture;

    private CountDownTimer cTimer = null;

    private int timer_status = 0;

    private BeaconManager beaconManager;
    private Region region;




    private String car_url = "http://" + "140.116.72.77" + ":" + 5000 + "/";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_car_camera);

        previewView = findViewById(R.id.previewView);

        testing = findViewById(R.id.testing);
        testing.setOnClickListener(this);

        collect_data = findViewById(R.id.collect_data);
        collect_data.setOnClickListener(this);

        message_car_camera = findViewById(R.id.message_car_camera);

        imageView = findViewById(R.id.imageView);

        cameraProviderFuture = ProcessCameraProvider.getInstance(this);
        cameraProviderFuture.addListener(() -> {
            try {
                ProcessCameraProvider cameraProvider = cameraProviderFuture.get();
                startCameraX(cameraProvider);
            } catch (ExecutionException e) {
                throw new RuntimeException(e);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        },getExecutor());

        timer_status = 0;

        BeaconManager beaconManager = BeaconManager.getInstanceForApplication(this);
        beaconManager.getBeaconParsers().add(new BeaconParser().setBeaconLayout("your_beacon_layout"));
        beaconManager.bind(this);

        onBeaconServiceConnect();



    }

    private Executor getExecutor() {
        return ContextCompat.getMainExecutor(this);
    }

    @Override
    public void onClick(View view) {
        int id = view.getId();
        if (id == R.id.testing) {
            message_car_camera.setText("This is testing calling");





        }
        else if (id == R.id.collect_data) {
            message_car_camera.setText("This is collect data calling");
            if(timer_status == 0)
                startTimer();
            else {
                cancelTimer();
                message_car_camera.setText("cancel timer");
                timer_status = 0;
            }

        }

    }

    private void startCameraX(ProcessCameraProvider cameraProvider) {

        cameraProvider.unbindAll();

        CameraSelector cameraSelector = new CameraSelector.Builder()
                .requireLensFacing(CameraSelector.LENS_FACING_BACK)
                .build();



        Preview preview = new Preview.Builder()
                .build();
        preview.setSurfaceProvider(previewView.getSurfaceProvider());

        imageCapture = new ImageCapture.Builder()
                .setTargetResolution(new Size(500,500))
                .build();

        cameraProvider.bindToLifecycle(this, cameraSelector, preview ,imageCapture);

    }
    //take picture and post to server
    private void capturephoto(){
        File pictureDir = new File(getExternalFilesDir("/").getAbsolutePath(), "picture");
        if (!pictureDir.exists()) {
            pictureDir.mkdirs();
        }

        Date date = new Date();
        String timestamp = String.valueOf(date.getTime());
        String photofilepath = pictureDir.getAbsolutePath() + "/" + timestamp + ".jpeg";
        String photoname = timestamp + ".jpeg";

        File photofile = new File(photofilepath);

        imageCapture.takePicture(
                new ImageCapture.OutputFileOptions.Builder(photofile).build(),
                getExecutor(),
                new ImageCapture.OnImageSavedCallback() {
                    @Override
                    public void onImageSaved(@NonNull ImageCapture.OutputFileResults outputFileResults) {
                        message_car_camera.setText("picture saved");
                        postRequest(car_url, photofilepath, photoname);
                    }
                    @Override
                    public void onError(@NonNull ImageCaptureException exception) {
                        message_car_camera.setText("ERROR");
                    }
                }
        );

    }
    private void postRequest(String car_url, String image_file_path, String image_filename) {

        OkHttpClient okHttpClient = new OkHttpClient();

        File f = new File(image_file_path);

        String mediaType = getMimeType(image_file_path);

        message_car_camera.setText(mediaType + "____" + image_file_path + "____" + car_url);

        Bitmap myBitmap = BitmapFactory.decodeFile(f.getAbsolutePath());
        imageView.setImageBitmap(myBitmap);


        RequestBody requestBody = new MultipartBody
                .Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("image", image_filename, RequestBody.create(MediaType.parse(mediaType),f))
                .build();

        String image_url = car_url + "image_store";

        Request request = new Request
                .Builder()
                .post(requestBody)
                .url(image_url)
                .build();

        okHttpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                message_car_camera.setText(e.toString());
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                message_car_camera.setText(response.toString());
            }
        });


    }
    private void postRequest_RSSI(String car_url,String rssi1,String rssi2,String rssi3,String rssi4,String rssi5,String rssi6){
        OkHttpClient okHttpClient = new OkHttpClient();

        String room_URL = car_url + "receive_RSSI";

        RequestBody formBody = new FormBody.Builder()
                .add("beacon1", rssi1)
                .add("beacon2", rssi2)
                .add("beacon3", rssi3)
                .add("beacon4", rssi4)
                .add("beacon5", rssi5)
                .add("beacon6", rssi6)
                .build();

        Request request = new Request.Builder()
                .url(room_URL)
                .post(formBody)
                .build();

        okHttpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                message_car_camera.setText(e.toString() + "error sending RSSI");
                call.cancel();
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                message_car_camera.setText(response.toString());
            }
        });

    }

    public void onBeaconServiceConnect() {
        // Configure the BeaconManager and set up a ranging listener
        beaconManager.addRangeNotifier(new RangeNotifier() {
            @Override
            public void didRangeBeaconsInRegion(Collection<Beacon> beacons, Region region) {
                for (Beacon beacon : beacons) {
                    String uuid = beacon.getId1().toString();
                    int major = beacon.getId2().toInt();
                    int minor = beacon.getId3().toInt();
                    int rssi = beacon.getRssi();

                    System.out.println(major);
                    System.out.println(minor);
                    System.out.println(rssi);

                    // Process beacon data here, including RSSI


                }
            }
        });
    }

        private String getMimeType(String path){
        String extension = MimeTypeMap.getFileExtensionFromUrl(path);
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension);
    }
    //start timer function
    void startTimer() {
        cTimer = new CountDownTimer(3000, 200) {
            public void onTick(long millisUntilFinished) {
                timer_status = 1;
                capturephoto();
            }
            public void onFinish() {
//                timer_status = 0;
                startTimer();
                message_car_camera.setText("on finish coutdowmer timer");
            }
        };
        cTimer.start();
    }
    //cancel timer
    void cancelTimer() {
        if(cTimer!=null)
            cTimer.cancel();
    }




}