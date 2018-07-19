package org.tensorflow.demo.bluetooth;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.UUID;

@SuppressLint("NewApi")
public class BluetoothService {

	public static final int CONNECTION_FAIL = 0x12;
	public static final int CONNECTION_SUCCESS = 0x13;
	public static final int MESSAGE_READ = 0x01;

	private static final UUID MY_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

	private final BluetoothAdapter mAdapter;
	//private BluetoothAdapter mAdapter;
	private ConnectThread mConnectThread;
    private ConnectedThread mConnectedThread;
	//public static ConnectThread mConnectThread;
	//public static ConnectedThread mConnectedThread;
    public static boolean mStateConnection = true;
    //private final Handler mReadHandler;
    public Handler mReadHandler;
    public Handler mCHandler;

    //public static BluetoothSocket mConnectedSocket;
    //public static BluetoothDevice mConnectDevice;

	public BluetoothService(Context context, BluetoothAdapter adapter, Handler mHandler) {
		mAdapter = adapter;
        mReadHandler = mHandler;
    }

	public BluetoothService(Context context, BluetoothAdapter adapter) {
        mAdapter = adapter;

    }

	private class ConnectThread extends Thread {
	    private final BluetoothSocket mmSocket;
	    private final BluetoothDevice mmDevice;

	    public ConnectThread(BluetoothDevice device) {
	        // Use a temporary object that is later assigned to mmSocket,
	        // because mmSocket is final
	        BluetoothSocket tmp = null;
	        mmDevice = device;

	        // Get a BluetoothSocket to connect with the given BluetoothDevice
	        try {
	            // MY_UUID is the app's UUID string, also used by the server code
	            tmp = device.createRfcommSocketToServiceRecord(MY_UUID);
	        } catch (IOException e) { }
	        mmSocket = tmp;
	    }

	    public void run() {
	        // Cancel discovery because it will slow down the connection
	        mAdapter.cancelDiscovery();

	        try {
	            // Connect the device through the socket. This will block
	            // until it succeeds or throws an exception
	            mmSocket.connect();
	        } catch (IOException connectException) {
	            // Unable to connect; close the socket and get out
	        	mStateConnection = false;
	        	Message m1=mCHandler.obtainMessage();
				m1.what=CONNECTION_FAIL;
				mCHandler.sendMessage(m1);
	        	//Log.d("connect", "not connect");
	            try {
	                mmSocket.close();
	            } catch (IOException closeException) { }
	            //ControlActivity.this.onBackPressed();
	            return;
	        }
	        Message m2=mCHandler.obtainMessage();
	        m2.what=CONNECTION_SUCCESS;
	        mCHandler.sendMessage(m2);
	        Log.d("connect", "ok?");
	        synchronized (BluetoothService.this) {
                mConnectThread = null;
            }

            // Start the connected thread

	        connected(mmSocket, mmDevice);
	        //mConnectedSocket = mmSocket;
	        //mConnectDevice = mmDevice;
	    }

	    /** Will cancel an in-progress connection, and close the socket */
	    public void cancel() {
	        try {
	            mmSocket.close();
	        } catch (IOException e) { }
	    }
	}

	private class ConnectedThread extends Thread {
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
	        byte[] buffer = new byte[1024];  // buffer store for the stream
	        //byte[] buffer = new byte[7];
	        //int byteNum; // bytes returned from read()
	        int byteFirst;
	        int i = 0;

	        // Keep listening to the InputStream until an exception occurs
	        while (true) {
	            try {
	            	/***********************************
	                // Read from the InputStream
	            	byteNum = mmInStream.read(buffer);
	            	//Message readMsg=mReadHandler.obtainMessage();
            		//readMsg.obj = buffer;
            		//mReadHandler.sendMessage(readMsg);
            		mReadHandler.obtainMessage(MESSAGE_READ,byteNum,-1, buffer).sendToTarget();
            		**********************************************/

	            	if(mmInStream.available()>0){
	            		byteFirst = mmInStream.read();
		            	//buffer[i++]=(byte)byteFirst;
		            	if(i>6){
		            		i=0;
		            	}
		            	buffer[i++]=(byte)byteFirst;
		            	if(byteFirst == 0xef){
		            		i=1;
		            		buffer[0]=(byte)byteFirst;
		            	}
		            	if((buffer[0]==(byte)0xef)&&(buffer[6]==(byte)0xfe)&&(i==7)){
		            		Message readMsg=mReadHandler.obtainMessage();
		            		readMsg.obj = buffer;
		            		mReadHandler.sendMessage(readMsg);
		            	}
	            	}

	            	Log.d("RECEIVE", String.valueOf(i));

	            } catch (IOException e) {
	                break;
	            }
	        }
	    }

	    /* Call this from the main activity to send data to the remote device */
	    public void write(byte bytes) {
	        try {
	            mmOutStream.write(bytes);
	        } catch (IOException e) { }
	    }

	    public void write(byte[] bytes) {
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

    private void broadcastUpdate(final String action) {
        Intent intent = new Intent(action);
        //sendBroadcast(intent);
    }


	public synchronized void connect(BluetoothDevice device) {

        // Cancel any thread currently running a connection
        if (mConnectedThread != null) {
        	mConnectedThread.cancel();
        	mConnectedThread = null;
        	}

        // Start the thread to connect with the given device
        mConnectThread = new ConnectThread(device);
        mConnectThread.start();
    }

	public synchronized void connected(BluetoothSocket socket, BluetoothDevice device) {

        // Cancel the thread that completed the connection
        if (mConnectThread != null) {mConnectThread.cancel(); mConnectThread = null;}

        // Cancel any thread currently running a connection
        if (mConnectedThread != null) {mConnectedThread.cancel(); mConnectedThread = null;}

        // Start the thread to manage the connection and perform transmissions
        mConnectedThread = new ConnectedThread(socket);
        mConnectedThread.start();

    }

	public void write(byte out) {
        // Create temporary object
        ConnectedThread r;
        r = mConnectedThread;

        // Perform the write unsynchronized
        r.write(out);
    }

	public void write(byte[] out) {
        // Create temporary object
        ConnectedThread r;
        r = mConnectedThread;
        Log.d("Write", out.toString());
        // Perform the write unsynchronized
        r.write(out);
    }

	public synchronized void stop() {
        if(mConnectThread != null){
        	mConnectThread.cancel();
        	mConnectThread = null;
        	}
        if(mConnectedThread != null){
        	mConnectedThread.cancel();
        	mConnectedThread = null;
        	}
    }

}
