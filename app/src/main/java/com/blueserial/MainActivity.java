/*
 * Released under MIT License http://opensource.org/licenses/MIT
 * Copyright (c) 2013 Plasty Grove
 * Refer to file LICENSE or URL above for full text 
 */

package com.blueserial;

import java.io.IOException;
import java.io.InputStream;
import java.util.UUID;

import com.blueserial.R;

import android.app.Activity;
import android.app.ProgressDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.Toast;
import android.content.pm.ActivityInfo;

public class MainActivity extends Activity {

	private static final String TAG = "OLEDCtrl-MainActivity";
	private UUID mDeviceUUID;
	private BluetoothSocket mBTSocket;
	private ReadInput mReadThread = null;

	private boolean mIsUserInitiatedDisconnect = false;

	// All controls here
	private Button mBtnDisconnect;
	private Button mBtnPent;
	private Button mBtnDice;
	private LedView[] mBtnToggle=new LedView[6];
    private String[] onoff={"A","D","B","E","C","F"};

	private boolean mIsBluetoothConnected = false;

	private BluetoothDevice mDevice;

	private ProgressDialog progressDialog;

    private void sendStr(String data) {
        try {
            mBTSocket.getOutputStream().write(data.getBytes());
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
        //ActivityHelper.initialize(this); //This is to ensure that the rotation persists across activities and not just this one
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);

		Intent intent = getIntent();
		Bundle b = intent.getExtras();
		mDevice = b.getParcelable(Homescreen.DEVICE_EXTRA);
		mDeviceUUID = UUID.fromString(b.getString(Homescreen.DEVICE_UUID));

		Log.d(TAG, "Ready");

		mBtnDisconnect = (Button) findViewById(R.id.btnDisconnect);
		mBtnPent = (Button) findViewById(R.id.btnPentLayout);
		mBtnDice = (Button) findViewById(R.id.btnDiceLayout);

        int[] ids={R.id.button1,
                R.id.button2,
                R.id.button3,
                R.id.button4,
                R.id.button5,
                R.id.button6};

        for (int i=0;i<ids.length;i++) {
            mBtnToggle[i]=(LedView) findViewById(ids[i]);
            mBtnToggle[i].setCode(onoff[i]);
            mBtnToggle[i].setOnClickListener(new OnClickListener() {

                @Override
                public void onClick(View arg0) {
                    if (!(arg0 instanceof LedView)) return;
                    LedView lv=(LedView) arg0;
                    lv.toggle();
                    if (lv.getState()==0) {
                        sendStr(lv.getCode().toLowerCase());
                    } else {
                        sendStr(lv.getCode().toUpperCase());
                    }
                }
            });
        }

		mBtnDisconnect.setOnClickListener(new OnClickListener() {

			@Override
			public void onClick(View v) {
				mIsUserInitiatedDisconnect = true;
				new DisConnectBT().execute();
			}
		});

		mBtnPent.setOnClickListener(new OnClickListener() {

			@Override
			public void onClick(View arg0) {
				// TODO: Implement layout change
			}
		});

		mBtnDice.setOnClickListener(new OnClickListener() {

			@Override
			public void onClick(View arg0) {

			}
		});

	}

	private class ReadInput implements Runnable {

		private boolean bStop = false;
		private Thread t;

		public ReadInput() {
			t = new Thread(this, "Input Thread");
			t.start();
		}

		public boolean isRunning() {
			return t.isAlive();
		}

		@Override
		public void run() {
			InputStream inputStream;

			try {
				inputStream = mBTSocket.getInputStream();
				while (!bStop) {
					byte[] buffer = new byte[256];
					if (inputStream.available() > 0) {
						inputStream.read(buffer);
						int i = 0;
						/*
						 * This is needed because new String(buffer) is taking the entire buffer i.e. 256 chars on Android 2.3.4 http://stackoverflow.com/a/8843462/1287554
						 */
						for (i = 0; i < buffer.length && buffer[i] != 0; i++) {
						}
						// INPUT STRING goes here
						final String strInput = new String(buffer, 0, i);

					}
					Thread.sleep(500);
				}
			} catch (IOException e) {
			} catch (InterruptedException e) {
			}

		}

		public void stop() {
			bStop = true;
		}

	}

	private class DisConnectBT extends AsyncTask<Void, Void, Void> {

		@Override
		protected void onPreExecute() {
		}

		@Override
		protected Void doInBackground(Void... params) {

			if (mReadThread != null) {
				mReadThread.stop();
				while (mReadThread.isRunning()) {
					try {
						Thread.sleep(30);
					} catch (Exception e) {
					} // Wait until it stops
				}
				mReadThread = null;

			}

			try {
				mBTSocket.close();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}

			return null;
		}

		@Override
		protected void onPostExecute(Void result) {
			super.onPostExecute(result);
			mIsBluetoothConnected = false;
			if (mIsUserInitiatedDisconnect) {
				finish();
			}
		}

	}

	private void msg(String s) {
		Toast.makeText(getApplicationContext(), s, Toast.LENGTH_SHORT).show();
	}

	@Override
	protected void onPause() {
		if (mBTSocket != null && mIsBluetoothConnected) {
			new DisConnectBT().execute();
		}
		Log.d(TAG, "Paused");
		super.onPause();
	}

	@Override
	protected void onResume() {
		if (mBTSocket == null || !mIsBluetoothConnected) {
			new ConnectBT().execute();
		}
		Log.d(TAG, "Resumed");
		super.onResume();
	}

	@Override
	protected void onStop() {
		Log.d(TAG, "Stopped");
		super.onStop();
	}

	@Override
	protected void onSaveInstanceState(Bundle outState) {
		// TODO Auto-generated method stub
		super.onSaveInstanceState(outState);

	}

	private class ConnectBT extends AsyncTask<Void, Void, Void> {
		private boolean mConnectSuccessful = true;

		@Override
		protected void onPreExecute() {
            // http://stackoverflow.com/a/11130220/1287554
			progressDialog = ProgressDialog.show(MainActivity.this, getString(R.string.condlg_title), getString(R.string.condlg_con));
		}

		@Override
		protected Void doInBackground(Void... devices) {

			try {
				if (mBTSocket == null || !mIsBluetoothConnected) {
					mBTSocket = mDevice.createInsecureRfcommSocketToServiceRecord(mDeviceUUID);
					BluetoothAdapter.getDefaultAdapter().cancelDiscovery();
					mBTSocket.connect();
				}
			} catch (IOException e) {
				// Unable to connect to device
				e.printStackTrace();
				mConnectSuccessful = false;
			}
			return null;
		}

		@Override
		protected void onPostExecute(Void result) {
			super.onPostExecute(result);

			if (!mConnectSuccessful) {
				Toast.makeText(getApplicationContext(), getString(R.string.home_confail), Toast.LENGTH_LONG).show();
				finish();
			} else {
				msg(getString(R.string.home_connected));
				mIsBluetoothConnected = true;
				mReadThread = new ReadInput(); // Kick off input reader
			}

			progressDialog.dismiss();
		}

	}

}
