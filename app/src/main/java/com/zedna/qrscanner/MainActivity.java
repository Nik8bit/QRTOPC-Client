package com.zedna.qrscanner;
import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.journeyapps.barcodescanner.ScanContract;
import com.journeyapps.barcodescanner.ScanOptions;
import com.zedna.qrscanner.databinding.ActivityMainBinding;

import java.io.PrintWriter;
import java.net.Socket;

//TODO: SEND LENGTH OF STRING IN 4BYTE TO THE SERVER TO LOOP READ THE DATA CORRECTLY
public class MainActivity extends AppCompatActivity {
    private TextView connectionStatus;
    private TextView resultText2;
    private EditText ipAddress, port;
    private Button connectButton, disconnectButton;
    private SwitchCompat Tor_switch;
    private SwitchCompat Scanandsend_switch;
    private SwitchCompat Loop_Switch;

    private boolean isConnected = false;
    private  TcpConnectionManager connectionManager;
    SharedPreferences sharedPreferences;
    private static final String PREF_SERVER = "SERVER_DETAILS";
    private static final String KEY_IP = "LAST_IP";
    private static final String KEY_PORT = "LAST_PORT";
    private boolean isLoopRunning = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        ActivityMainBinding binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // Set up the back press callback
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                // Stop the loop completely so the camera doesn't open again
                stopScanningLoop();

                // Close the screen/Activity safely
                setEnabled(false);
                getOnBackPressedDispatcher().onBackPressed();
            }
        });


        sharedPreferences = getSharedPreferences(PREF_SERVER, MODE_PRIVATE);

        initializeViews();
        setupClickListeners();
        connectionManager = new TcpConnectionManager();

    }




    private void initializeViews() {
        connectButton = findViewById(R.id.connectButton);
        disconnectButton = findViewById(R.id.disconnectButton);
        resultText2 = findViewById(R.id.textView2);
        connectionStatus = findViewById(R.id.connectionStatus);
        ipAddress = findViewById(R.id.ipAddress);
        port = findViewById(R.id.port);
        FloatingActionButton fab_scan = findViewById(R.id.fabscan);
        FloatingActionButton fab_send = findViewById(R.id.fabsend);
        Tor_switch = findViewById(R.id.Torchswitch);
        Scanandsend_switch = findViewById(R.id.Scansendswitch);
        Loop_Switch = findViewById(R.id.Loopswitch);

        String LastIP = sharedPreferences.getString(KEY_IP,"192.168.0.10");
        String LastPORT = sharedPreferences.getString(KEY_PORT,"9898");
        ipAddress.setText(LastIP);
        port.setText(LastPORT);

        Button scanButton = findViewById(R.id.scanButton);
        scanButton.setOnClickListener(v -> startBarcodeScan());
        fab_scan.setOnClickListener(v -> ScanFabButton());
        fab_send.setOnClickListener(v -> SendData());
    }
    @Override
    protected void onPause() {
        super.onPause();

        // Save text when app goes into background
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString(KEY_IP, ipAddress.getText().toString());
        editor.putString(KEY_PORT, port.getText().toString());
        editor.apply();
    }
    private void setupClickListeners() {
        connectButton.setOnClickListener(v -> connectToServer());
        disconnectButton.setOnClickListener(v -> disconnectFromServer());
    }

    private final Handler scanHandler = new Handler(Looper.getMainLooper());
    private Runnable scanRunnable;
    private void ScanFabButton(){
        isLoopRunning = Loop_Switch.isChecked(); // Turn the loop flag ON
        startBarcodeScan();   // Fire the first scan
    }

    private void SendData(){

        if (!resultText2.getText().toString().isEmpty()){
            if (isConnected ) {
                String SendDataXml = "<CODE>" + resultText2.getText().toString() + "</CODE>";
                sendDataOverTcp(SendDataXml);
            }
        }
    }
    private void startBarcodeScan() {
        ScanOptions options = new ScanOptions();
        options.setPrompt("Scan a barcode");
        options.setBeepEnabled(true);
        options.setOrientationLocked(true);
        options.setCaptureActivity(CaptureActivityPortrait.class);
        if (Tor_switch.isChecked()){
            options.setTorchEnabled(true);
        }
        barcodeLauncher.launch(options);
    }

    private void connectToServer() {
        String ip = ipAddress.getText().toString();
        String portStr = port.getText().toString();

        if (ip.isEmpty() || portStr.isEmpty()) {
            Toast.makeText(this, "Please enter IP and Port", Toast.LENGTH_SHORT).show();
            return;
        }

        connectionManager.connect(ip, Integer.parseInt(portStr), new TcpConnectionManager.ConnectionCallback() {
            @Override
            public void onConnectionSuccess(Socket socket, PrintWriter writer) {
                runOnUiThread(() -> {

                    isConnected = true;
                    updateConnectionStatus();
                    Toast.makeText(MainActivity.this, "Connected to server", Toast.LENGTH_SHORT).show();
                });
            }

            @Override
            public void onConnectionError(String error) {
                runOnUiThread(() -> {
                    isConnected = false;
                    updateConnectionStatus();
                    Toast.makeText(MainActivity.this, "Failed to connect: " + error, Toast.LENGTH_SHORT).show();
                });
            }

            @Override
            public void onConnectionLost() {
                runOnUiThread(() -> {
                    isConnected = false;
                    updateConnectionStatus();
                    Toast.makeText(MainActivity.this, "Connection lost", Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    private void disconnectFromServer() {
        connectionManager.disconnect();
        isConnected = false;
        updateConnectionStatus();
        Toast.makeText(this, "Disconnected from server", Toast.LENGTH_SHORT).show();
    }

    private final androidx.activity.result.ActivityResultLauncher<ScanOptions> barcodeLauncher =
            registerForActivityResult(new ScanContract(),
                    result -> {
                        if (result.getContents() != null) {
                            String scanResult = result.getContents();
                            resultText2.setText(scanResult);
                            // SCAN AND SEND
                            if (Scanandsend_switch.isChecked()) {
                                if (!scanResult.isEmpty()){
                                    if (isConnected ) {
                                        String SendDataXml = "<CODE>" + scanResult + "</CODE>";
                                        sendDataOverTcp(SendDataXml);
                                    }
                                }
                                }
                            // 2. CRITICAL CHANGE: Continuous uninterrupted loop
                            if (isLoopRunning) {
                                // A brief 300ms delay gives the UI a moment to settle
                                // and prevents the camera activity from crashing due to rapid firing
                                scanRunnable = this::startBarcodeScan;
                                scanHandler.postDelayed(scanRunnable, 300);
                            }
                        }
                        else {
                            resultText2.setText("");
                            stopScanningLoop();
                        }
                    });
    private void stopScanningLoop() {
        // 1. Drop the flag so the barcode launcher callback knows to stop looping
        isLoopRunning = false;

        // 2. Remove any pending camera launches scheduled in the background handler
        if (scanRunnable != null) {
            scanHandler.removeCallbacks(scanRunnable);
        }
    }
    private void sendDataOverTcp(String data) {
        if (data == null || data.isEmpty()) {
            Toast.makeText(this, "Please scan a barcode first", Toast.LENGTH_SHORT).show();
            return;
        }

        if (!isConnected) {
            Toast.makeText(this, "Not connected to server", Toast.LENGTH_SHORT).show();
            return;
        }

        connectionManager.sendData(data, new TcpConnectionManager.SendCallback() {
            @Override
            public void onSendSuccess() {
                runOnUiThread(() ->
                        Toast.makeText(MainActivity.this, "Data sent successfully", Toast.LENGTH_SHORT).show()
                );
            }

            @Override
            public void onSendError(String error) {
                runOnUiThread(() -> {
                    Toast.makeText(MainActivity.this, "Failed to send data: " + error, Toast.LENGTH_SHORT).show();
                    isConnected = false;
                    updateConnectionStatus();
                });
            }
        });
    }

    private void updateConnectionStatus() {
        runOnUiThread(() -> {
            if (isConnected) {
                connectionStatus.setText(R.string.mainmenu_constat_con);
                connectionStatus.setTextColor(this.getColor(android.R.color.holo_green_dark));
                connectButton.setEnabled(false);
                disconnectButton.setEnabled(true);
                sendDataOverTcp("<ZEDPCQR>CONNECTED</ZEDPCQR>");
            } else {
                connectionStatus.setText(R.string.mainmenu_constat_discon);
                connectionStatus.setTextColor(this.getColor(android.R.color.holo_red_dark));
                connectButton.setEnabled(true);
                disconnectButton.setEnabled(false);
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        disconnectFromServer();
    }


 }


