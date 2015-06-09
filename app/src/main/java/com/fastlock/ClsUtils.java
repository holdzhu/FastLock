package com.fastlock;

import android.bluetooth.BluetoothDevice;

import java.lang.reflect.Method;

public class ClsUtils {
	static public boolean createBond(Class btClass, BluetoothDevice btDevice) throws Exception {
		Method createBondMethod = btClass.getMethod("createBond");
		Boolean returnValue = (Boolean) createBondMethod.invoke(btDevice);
		return returnValue.booleanValue();
	}

	static public boolean setPin(Class btClass, BluetoothDevice btDevice, String str) throws Exception {
		try {
			Method removeBondMethod = btClass.getDeclaredMethod("setPin", new Class[]{byte[].class});
			removeBondMethod.invoke(btDevice, new Object[]{str.getBytes()});
		} catch (SecurityException e) {
			e.printStackTrace();
		}
		return true;
	}

	static public boolean cancelPairingUserInput(Class btClass, BluetoothDevice device) throws Exception {
		Method createBondMethod = btClass.getMethod("cancelPairingUserInput");
		Boolean returnValue = (Boolean) createBondMethod.invoke(device);
		return returnValue.booleanValue();
	}
}