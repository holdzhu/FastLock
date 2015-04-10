package com.fastlock;

import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

public class BluetoothConnectActivityReceiver extends BroadcastReceiver {

	String password = "1234";

	@Override
	public void onReceive(Context context, Intent intent) {
		if (intent.getAction().equals("android.bluetooth.device.action.PAIRING_REQUEST")) {
			BluetoothDevice btDevice = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
			Log.i("test", "hahah");
			try {
				ClsUtils.setPin(btDevice.getClass(), btDevice, password);
				ClsUtils.createBond(btDevice.getClass(), btDevice);
				ClsUtils.cancelPairingUserInput(btDevice.getClass(), btDevice);
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}
}
