package com.example.audibraille;

import static java.security.AccessController.getContext;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.SystemClock;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothSocket;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.util.Set;
import java.util.UUID;


public class MainActivity extends AppCompatActivity {

    Button btnCapture;
    TextView mBluetoothStatus;
    ImageView ivImage;
    BluetoothAdapter BA;
    Set<BluetoothDevice> mPairedDevices;
    BluetoothSocket BTsocket;
    final String name = "ESP32test!";
    Bitmap bmp;

    private ConnectedThread mConnectedThread;

    private Handler mHandler;

    private static final UUID BTMODULEUUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

    private final static int REQUEST_ENABLE_BT = 1; // used to identify adding bluetooth names
    private final static int MESSAGE_READ = 2; // used in bluetooth handler to identify message update
    private final static int CONNECTING_STATUS = 3;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        btnCapture = (Button) findViewById(R.id.btnCapture);
        mBluetoothStatus = (TextView)findViewById(R.id.mBluetoothStatus);
        ivImage = (ImageView)findViewById(R.id.ivImage);

        BA = BluetoothAdapter.getDefaultAdapter();

        mHandler = new Handler(){
            public void handleMessage(android.os.Message msg){
                if(msg.what == MESSAGE_READ){
                    String readMessage = null;
                    byte[] readBuf = (byte[]) msg.obj;
                    bmp = BitmapFactory.decodeByteArray(readBuf, 0, msg.arg1);

                    ivImage.setImageBitmap(bmp);
                }

                if(msg.what == CONNECTING_STATUS){
                    if(msg.arg1 == 1)
                        mBluetoothStatus.setText("Connected to Device: " + (String)(msg.obj));
                    else
                        mBluetoothStatus.setText("Connection Failed");
                }
            }
        };

        btnCapture.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                blueToothOn(view);

                new Thread() {
                    @Override
                    public void run() {
                        boolean fail = false;
                        BluetoothDevice m5device = BA.getRemoteDevice("08:3A:F2:6A:56:1A");

                        try {
                            BTsocket = createBluetoothSocket(m5device);

                        } catch (IOException e) {
                            fail = true;
                            Toast.makeText(getBaseContext(), "Socket creation failed", Toast.LENGTH_SHORT).show();
                        }
                        //Establish Bluetooth socket connection.
                        try {
                            BTsocket.connect();
                        } catch (IOException e){
                            try {
                                fail = true;
                                BTsocket.close();
                            } catch (IOException e2) {
                                //Insert code to deal with this
                                Toast.makeText(getBaseContext(), "Socket creation failed", Toast.LENGTH_SHORT).show();
                            }
                        }

                        if(fail == false) {
                            mConnectedThread = new ConnectedThread(BTsocket);
                            mConnectedThread.start();

                            mHandler.obtainMessage(CONNECTING_STATUS, 1, -1,name).sendToTarget();

                        }
                    }
                }.start();
                if(mConnectedThread!= null)
                {
                    mConnectedThread.write("on");
                }
            }
        });


    }

/*    private void listPairedDevices(View view){
        mPairedDevices = mBTAdapter.getBondedDevices();
        if(mBTAdapter.isEnabled()) {
            // put it's one to the adapter
            for (BluetoothDevice device : mPairedDevices)
                mBTArrayAdapter.add(device.getName() + "\n" + device.getAddress());

            Toast.makeText(getApplicationContext(), "Show Paired Devices", Toast.LENGTH_SHORT).show();
        }
        else
            Toast.makeText(getApplicationContext(), "Bluetooth not on", Toast.LENGTH_SHORT).show();
    }*/
private BluetoothSocket createBluetoothSocket(@NonNull BluetoothDevice device) throws IOException {
    return  device.createRfcommSocketToServiceRecord(BTMODULEUUID);
    //creates secure outgoing connection with BT device using UUID
}

    private void blueToothOn(View view) {
        if(!BA.isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent,REQUEST_ENABLE_BT);
            Toast.makeText(getApplicationContext(),"Bluetooth on!", Toast.LENGTH_SHORT).show();
        }
        else{
            Toast.makeText(getApplicationContext(),"Bluetooth is already on", Toast.LENGTH_SHORT).show();
        }
    }
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent Data) {
        // Check which request we're responding to
        super.onActivityResult(requestCode, resultCode, Data);
        //super.onActivityResult(requestCode, resultCode, Data);
        if (requestCode == REQUEST_ENABLE_BT) {
            // Make sure the request was successful
            if (resultCode == RESULT_OK) {
                // The user picked a contact.
                // The Intent's data Uri identifies which contact was selected.
                Toast.makeText(getApplicationContext(), "Bluetooth enabled!", Toast.LENGTH_SHORT).show();
            } else
                Toast.makeText(getApplicationContext(), "Bluetooth disabled!", Toast.LENGTH_SHORT).show();
        }
    }private class ConnectedThread extends Thread {
        private final BluetoothSocket mmSocket;
        private final InputStream mmInStream;
        private final OutputStream mmOutStream;

        public ConnectedThread(BluetoothSocket socket) {
            mmSocket = socket;
            InputStream tmpIn = null;
            OutputStream tmpOut = null;

            // Get the input and output streams, using temp objects because
            // member streams are final
            try {
                tmpIn = socket.getInputStream();
                tmpOut = socket.getOutputStream();
            } catch (IOException e) { }

            mmInStream = tmpIn;
            mmOutStream = tmpOut;
        }

        public void run() {
            byte[] buffer = new byte[307204*2];  // buffer store for the stream
            byte[] imgBuffer = new byte[1024 * 1024];
            int bytes; // bytes returned from read()
            int pos = 0;

            // Keep listening to the InputStream until an exception occurs
            while (true) {
                try {
                    // Read from the InputStream
                    bytes = mmInStream.available();
                    if(bytes != 0) {
                        SystemClock.sleep(100); //pause and wait for rest of data. Adjust this depending on your sending speed.
                        bytes = mmInStream.available(); // how many bytes are ready to be read?
                        bytes = mmInStream.read(buffer); // record how many bytes we actually read
                        Message readMsg = mHandler.obtainMessage(MESSAGE_READ, bytes, -1, buffer);
                        readMsg.sendToTarget(); // Send the obtained bytes to the UI activity
                    }
                } catch (IOException e) {
                    e.printStackTrace();

                    break;
                }
            }
        }

        /* Call this from the main activity to send data to the remote device */
        public void write(String input) {
            byte[] bytes = input.getBytes();           //converts entered String into bytes
            try {
                mmOutStream.write(bytes);
            } catch (IOException e) { }
        }

        /* Call this from the main activity to shutdown the connection */
        public void cancel() {
            try {
                mmSocket.close();
            } catch (IOException e) { }
        }
    }





}