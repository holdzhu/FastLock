package com.fastlock;

import android.os.Handler;
import android.os.Message;

import java.lang.ref.WeakReference;

public class SetStatusHandler extends Handler {
	private final WeakReference<MainActivity> mActivity;

	public SetStatusHandler(MainActivity activity) {
		mActivity = new WeakReference<>(activity);
	}

	@Override
	public void handleMessage(Message msg) {
		mActivity.get().setStatus(Status.values()[msg.what]);
		super.handleMessage(msg);
	}
}
