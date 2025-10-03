package com.zedna.qrscanner;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;

import android.os.Bundle;
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


public class MainActivity extends AppCompatActivity {
    private TextView connectionStatus;
    private TextView resultText2;
    private EditText ipAddress, port;
    private Button connectButton, disconnectButton, sendButton;
    private SwitchCompat Tor_switch;

    private boolean isConnected = false;
    private  TcpConnectionManager connectionManager;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        ActivityMainBinding binding = ActivityMainBinding.inflate(getLayoutInflater());
     setContentView(binding.getRoot());

        initializeViews();
        setupClickListeners();
        connectionManager = new TcpConnectionManager();

    }

    private void initializeViews() {
        connectButton = findViewById(R.id.connectButton);
        disconnectButton = findViewById(R.id.disconnectButton);
        sendButton = findViewById(R.id.sendButton);
        resultText2 = findViewById(R.id.textView2);
        connectionStatus = findViewById(R.id.connectionStatus);
        ipAddress = findViewById(R.id.ipAddress);
        port = findViewById(R.id.port);
        FloatingActionButton fab_scan = findViewById(R.id.fabscan);
        FloatingActionButton fab_send = findViewById(R.id.fabsend);
        Tor_switch = findViewById(R.id.Torchswitch);


        Button scanButton = findViewById(R.id.scanButton);
        scanButton.setOnClickListener(v -> startBarcodeScan());
        sendButton.setOnClickListener(v -> SendData());
        fab_scan.setOnClickListener(v -> startBarcodeScan());
        fab_send.setOnClickListener(v -> SendData());
    }
    private void setupClickListeners() {
        connectButton.setOnClickListener(v -> connectToServer());
        disconnectButton.setOnClickListener(v -> disconnectFromServer());
        sendButton.setOnClickListener(v -> SendData());
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
                        }
                        else {
                            resultText2.setText("");
                        }
                    });

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
                sendButton.setEnabled(true);
                sendDataOverTcp("<ZEDPCQR>CONNECTED</ZEDPCQR>");
            } else {
                connectionStatus.setText(R.string.mainmenu_constat_discon);
                connectionStatus.setTextColor(this.getColor(android.R.color.holo_red_dark));
                connectButton.setEnabled(true);
                disconnectButton.setEnabled(false);
                sendButton.setEnabled(false);
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        disconnectFromServer();
    }


 }


