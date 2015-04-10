package com.fastlock;

import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Point;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.v7.app.ActionBarActivity;
import android.util.Log;
import android.view.View;
import android.view.ViewTreeObserver;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.view.animation.AnimationSet;
import android.view.animation.LinearInterpolator;
import android.view.animation.RotateAnimation;
import android.view.animation.TranslateAnimation;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;

import java.io.InputStream;
import java.io.OutputStream;
import java.lang.ref.WeakReference;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.Queue;
import java.util.UUID;

import at.markushi.ui.RevealColorView;


public class MainActivity extends ActionBarActivity {

	private final static int BUTTON_OFF = 0;
	private final static int BUTTON_ON = 1;
	private final static int BUTTON_PAIR = 2;
	private static int buttonStatus = BUTTON_PAIR;
	private final static int BUTTON_PAIRING = 3;
	private final static int STATUS_PAIRED = 0;
	private final static int STATUS_WRONG_PASSWORD = 1;
	private final static int STATUS_CONNECT_FAILED = 2;
	private final static int STATUS_TURN_FAILED = 3;
	private final static int STATUS_TURNED = 4;
	private final static int STATUS_CHANGED = 5;
	private final static int STATUS_CHANGE_FAILED = 6;
	private final static int STATUS_TURN_ON_BLUETOOTH = 7;
	private final static int STATUS_CONNECTING = 8;
	private final static int STATUS_PAIRING = 9;
	private final static String BUTTON_TEXT_PAIR = "配对";
	private final static String BUTTON_TEXT_ON = "开锁";
	private final static String BUTTON_TEXT_OFF = "上锁";
	private final static String PIN = "1234";
	private final static int LABEL_ANIMATION_DURATION = 300;
	private final static int LOADING_ANIMATION_DURATION = 300;
	private static float labelTextSize;
	private static String MAC = "";
	private static BluetoothAdapter bluetoothAdapter;
	private static OutputStream outputStream;
	private static InputStream inputStream;
	private static TextView[] label = new TextView[2];
	private static int currentLabel = 0;
	private static BluetoothSocket socket;
	private static Button mainButton;
	private static RevealColorView revealColorView;
	private static ImageView loading;
	private static Animation rotation;
	private static Animation fadeIn;
	private static Animation fadeOut;
	private static Animation labelFadeIn;
	private static Animation labelFadeOut;
	private static Animation labelTranslateAnimation;
	private static Animation newLabelTranslateAnimation;
	private static AnimationSet loadingAnimation;
	private static AnimationSet labelAnimation;
	private static AnimationSet newLabelAnimation;
	private static Queue<String> labelQueue = new LinkedList<>();
	private static AlertDialog.Builder builder;
	private static Point p;
	private static boolean isInitialized = false;
	private static String password = "";
	private static SharedPreferences sp;
	private final SetStatusHandler setStatusHandler = new SetStatusHandler(this);

	private static void setLabelText(final String s) {
		labelQueue.add(s);
		if (labelQueue.size() == 1) {
			label[1 - currentLabel].setText(s);
			label[currentLabel].setVisibility(View.INVISIBLE);
			label[currentLabel].startAnimation(labelAnimation);
			label[1 - currentLabel].startAnimation(newLabelAnimation);
			newLabelAnimation.setAnimationListener(new Animation.AnimationListener() {
				@Override
				public void onAnimationStart(Animation animation) {

				}

				@Override
				public void onAnimationEnd(Animation animation) {
					if (!animation.hasEnded()) {
						label[currentLabel].setText(labelQueue.poll());
						currentLabel = 1 - currentLabel;
						if (!labelQueue.isEmpty()) {
							label[1 - currentLabel].setText(labelQueue.peek());
							label[currentLabel].startAnimation(labelAnimation);
							label[1 - currentLabel].startAnimation(newLabelAnimation);
						} else {
							label[currentLabel].setVisibility(View.VISIBLE);
						}
					}
				}

				@Override
				public void onAnimationRepeat(Animation animation) {

				}
			});
		}
	}

	private static void fadeInRotation() {
		loadingAnimation = new AnimationSet(false);
		loadingAnimation.addAnimation(rotation);
		loadingAnimation.addAnimation(fadeIn);
		loading.setVisibility(View.VISIBLE);
		loading.startAnimation(loadingAnimation);
	}

	private static void fadeOutRotation() {
		loadingAnimation.addAnimation(fadeOut);
		fadeOut.setAnimationListener(new Animation.AnimationListener() {
			@Override
			public void onAnimationStart(Animation animation) {

			}

			@Override
			public void onAnimationEnd(Animation animation) {
				loading.setVisibility(View.INVISIBLE);
				loadingAnimation.cancel();
				loading.clearAnimation();
			}

			@Override
			public void onAnimationRepeat(Animation animation) {

			}
		});
	}

	private static void setButtonStatus(int status) {
		buttonStatus = status;
		switch (status) {
			case BUTTON_PAIR:
				mainButton.setText(BUTTON_TEXT_PAIR);
				mainButton.setEnabled(true);
				closeSocket();
				mainButton.setBackgroundResource(R.drawable.bg_pair_button);
				revealColorView.hide(p.x, p.y, Color.parseColor("#212121"), 0, 300, null);
				fadeOutRotation();
				break;
			case BUTTON_ON:
				mainButton.setText(BUTTON_TEXT_ON);
				mainButton.setEnabled(true);
				mainButton.setBackgroundResource(R.drawable.bg_on_button);
				revealColorView.reveal(p.x, p.y, Color.parseColor("#8bc34a"), mainButton.getHeight() / 2, 340, null);
				fadeOutRotation();
				break;
			case BUTTON_OFF:
				mainButton.setText(BUTTON_TEXT_OFF);
				mainButton.setEnabled(true);
				mainButton.setBackgroundResource(R.drawable.bg_off_button);
				revealColorView.reveal(p.x, p.y, Color.parseColor("#e91e63"), mainButton.getHeight() / 2, 340, null);
				fadeOutRotation();
				break;
			case BUTTON_PAIRING:
				mainButton.setEnabled(false);
				mainButton.setBackgroundResource(R.drawable.bg_pair_button);
				revealColorView.reveal(p.x, p.y, Color.parseColor("#3f51b5"), mainButton.getHeight() / 2, 340, null);
				fadeInRotation();
				break;
		}
	}

	private static byte[] receive() {
		try {
			Thread.sleep(200);
			int count = inputStream.available();
			if (count != 0) {
				System.out.println(count);
				byte[] bytes = new byte[count];
				int readCount = 0;
				while (readCount < count) {
					readCount += inputStream.read(bytes, readCount, count - readCount);
				}
				for (byte b : bytes) {
					System.out.println((char) b);
				}
				return bytes;
			} else {
				return new byte[]{};
			}
		} catch (Exception e) {
			return new byte[]{};
		}
	}

	private static void setPassword(String newPassword) {
		password = newPassword;
	}

	private static void closeSocket() {
		try {
			socket.close();
		} catch (Exception e) {
			System.out.println(e.toString());
		}
	}

	public boolean pair(String strAddress, String strPsw) throws Exception {
		boolean result = false;
		bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
		bluetoothAdapter.cancelDiscovery();
		if (!bluetoothAdapter.isEnabled()) {
			setStatusHandler.sendEmptyMessage(STATUS_TURN_ON_BLUETOOTH);
			bluetoothAdapter.enable();
			Thread.sleep(500);
		}
		if (!BluetoothAdapter.checkBluetoothAddress(strAddress)) {
			Log.d("what", "aaa");
		}
		BluetoothDevice device = bluetoothAdapter.getRemoteDevice(strAddress);
		if (device.getBondState() != BluetoothDevice.BOND_BONDED) {
			setStatusHandler.sendEmptyMessage(STATUS_PAIRING);
			try {
				Log.d("no", "no BOND");
				ClsUtils.setPin(device.getClass(), device, strPsw);
				ClsUtils.createBond(device.getClass(), device);
				result = true;
			} catch (Exception e) {
				Log.d("np", "setPIINNN FFFFF");
				e.printStackTrace();
			}
		} else {
			Log.d("yes", "BOND");
			try {
				ClsUtils.createBond(device.getClass(), device);
				ClsUtils.setPin(device.getClass(), device, strPsw);
				ClsUtils.createBond(device.getClass(), device);
				result = true;
			} catch (Exception e) {
				Log.d("no", "ppppiiinn");
				e.printStackTrace();
			}
		}
		return result;
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		mainButton = (Button) findViewById(R.id.mainButton);
		revealColorView = (RevealColorView) findViewById(R.id.reveal);
		loading = (ImageView) findViewById(R.id.loading);
		label[0] = (TextView) findViewById(R.id.label);
		label[1] = (TextView) findViewById(R.id.newLabel);
		sp = getSharedPreferences("su", Context.MODE_PRIVATE);
		MAC = sp.getString("mac", "");
		if ("".equals(MAC)) {
			label[0].setText("长按按钮设置");
		}
		mainButton.setText(BUTTON_TEXT_PAIR);
		mainButton.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				switch (buttonStatus) {
					case BUTTON_PAIR:
						pairButtonClick();
						break;
					case BUTTON_ON:
						onButtonClick();
						break;
					case BUTTON_OFF:
						offButtonClick();
						break;
				}
			}
		});
		mainButton.setOnLongClickListener(new View.OnLongClickListener() {
			@Override
			public boolean onLongClick(View v) {
				if (buttonStatus == BUTTON_OFF || buttonStatus == BUTTON_ON) {
					onChangePassword();
				} else {
					onChangeMAC();
				}
				return true;
			}
		});
		View contentView = findViewById(R.id.contentView);
		contentView.getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
			@Override
			public boolean onPreDraw() {
				if (!isInitialized) {
					isInitialized = true;
					p = getLocationInView(revealColorView, mainButton);
					rotation = new RotateAnimation(0.0f, 360.0f, loading.getPivotX() + loading.getWidth() / 2, loading.getPivotY() + loading.getHeight() / 2);
					rotation.setDuration(1000);
					rotation.setRepeatCount(-1);
					rotation.setInterpolator(new LinearInterpolator());
					rotation.setRepeatMode(Animation.INFINITE);
					rotation.setFillEnabled(true);
					rotation.setFillAfter(true);
					labelAnimation = new AnimationSet(true);
					newLabelAnimation = new AnimationSet(true);
					labelTextSize = label[0].getTextSize();
					labelFadeIn = new AlphaAnimation(0.0f, 1.0f);
					labelFadeIn.setDuration(LABEL_ANIMATION_DURATION);
					labelFadeOut = new AlphaAnimation(1.0f, 0.0f);
					labelFadeOut.setDuration(LABEL_ANIMATION_DURATION);
					labelTranslateAnimation = new TranslateAnimation(0, 0, 0, -labelTextSize);
					labelTranslateAnimation.setDuration(LABEL_ANIMATION_DURATION);
					newLabelTranslateAnimation = new TranslateAnimation(0, 0, labelTextSize, 0);
					newLabelTranslateAnimation.setDuration(LABEL_ANIMATION_DURATION);
					labelAnimation.addAnimation(labelTranslateAnimation);
					labelAnimation.addAnimation(labelFadeOut);
					newLabelAnimation.addAnimation(newLabelTranslateAnimation);
					newLabelAnimation.addAnimation(labelFadeIn);
					fadeIn = new AlphaAnimation(0.0f, 1.0f);
					fadeIn.setDuration(LOADING_ANIMATION_DURATION);
					fadeIn.setFillEnabled(true);
					fadeIn.setFillAfter(true);
					fadeOut = new AlphaAnimation(1.0f, 0.0f);
					fadeOut.setDuration(LOADING_ANIMATION_DURATION);
					fadeOut.setFillEnabled(true);
					fadeOut.setFillAfter(true);
				}
				return true;
			}
		});
	}

	private void onChangeMAC() {
		AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
		final EditText et = new EditText(MainActivity.this);
		et.setText(MAC);
		builder.setTitle("设置MAC").setView(et).setPositiveButton("确定", new DialogInterface.OnClickListener() {
			@Override
			public void onClick(DialogInterface dialog, int which) {
				MAC = et.getText().toString();
				if (!"".equals(MAC)) {
					setLabelText("欢迎使用");
					SharedPreferences.Editor editor = sp.edit();
					editor.putString("mac", MAC);
					editor.apply();
				}
			}
		}).setNegativeButton("取消", null).show();
	}

	private Point getLocationInView(View src, View target) {
		final int[] l0 = new int[2];
		src.getLocationOnScreen(l0);
		final int[] l1 = new int[2];
		target.getLocationOnScreen(l1);
		l1[0] = l1[0] - l0[0] + target.getWidth() / 2;
		l1[1] = l1[1] - l0[1] + target.getHeight() / 2;
		return new Point(l1[0], l1[1]);
	}

	private void setStatus(int status) {
		switch (status) {
			case STATUS_PAIRING:
				setLabelText("配对中");
				break;
			case STATUS_CONNECTING:
				setLabelText("连接中");
				break;
			case STATUS_TURN_ON_BLUETOOTH:
				setLabelText("开启蓝牙中");
				break;
			case STATUS_CHANGE_FAILED:
				setLabelText("更改失败");
				break;
			case STATUS_CHANGED:
				setLabelText("已更改");
				break;
			case STATUS_TURNED:
				if (buttonStatus == BUTTON_ON) {
					setLabelText("已开锁");
					setButtonStatus(BUTTON_OFF);
				} else {
					setLabelText("已上锁");
					setButtonStatus(BUTTON_ON);
				}
				break;
			case STATUS_TURN_FAILED:
				if (buttonStatus == BUTTON_ON) {
					setLabelText("开锁失败");
				} else {
					setLabelText("上锁失败");
				}
				break;
			case STATUS_PAIRED:
				onInputPassword();
				break;
			case STATUS_CONNECT_FAILED:
				setLabelText("连接失败");
				setButtonStatus(BUTTON_PAIR);
				break;
			case STATUS_WRONG_PASSWORD:
				setLabelText("密码错误");
				setButtonStatus(BUTTON_PAIR);
				break;
		}
	}

	private void onChangePassword() {
		AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
		final View textEntryView = View.inflate(MainActivity.this, R.layout.change_password_dialog, null);
		builder.setTitle("更改密码");
		builder.setView(textEntryView);
		builder.setCancelable(false);
		builder.setPositiveButton("确定", new DialogInterface.OnClickListener() {
			@Override
			public void onClick(DialogInterface dialog, int which) {
				EditText originPasswordEditText = (EditText) textEntryView.findViewById(R.id.originPasswordEditText);
				EditText passwordEditText = (EditText) textEntryView.findViewById(R.id.passwordEditText);
				EditText repeatPasswordEditText = (EditText) textEntryView.findViewById(R.id.repeatPasswordEditText);
				if (originPasswordEditText.getText().toString().equals(password) &&
						passwordEditText.getText().length() > 0 &&
						passwordEditText.getText().toString().equals(repeatPasswordEditText.getText().toString())) {
					setPassword(passwordEditText.getText().toString());
					try {
						outputStream.write("\0sset\0".getBytes());
						outputStream.write(password.getBytes());
						if (Arrays.equals(receive(), "righ".getBytes())) {
							setStatus(STATUS_CHANGED);
							setPassword(password);
						} else {
							setStatus(STATUS_CHANGE_FAILED);
						}
					} catch (Exception e) {
						setStatus(STATUS_CHANGE_FAILED);
					}
				} else {
					setStatus(STATUS_CHANGE_FAILED);
				}
			}
		});
		builder.setNegativeButton("取消", new DialogInterface.OnClickListener() {
			@Override
			public void onClick(DialogInterface dialog, int which) {

			}
		});
		builder.show();
	}

	private void onInputPassword() {
		builder = new AlertDialog.Builder(MainActivity.this);
		final View textEntryView = View.inflate(MainActivity.this, R.layout.password_dialog, null);
		builder.setTitle("输入密码");
		builder.setView(textEntryView);
		builder.setCancelable(false);
		builder.setPositiveButton("确定", new DialogInterface.OnClickListener() {
			@Override
			public void onClick(DialogInterface dialog, int which) {
				EditText passwordEditText = (EditText) textEntryView.findViewById(R.id.passwordEditText);
				if (passwordEditText.getText().length() > 0) {
					setPassword(passwordEditText.getText().toString());
					try {
						outputStream.write("\0yanz\0".getBytes());
						byte[] bytes = receive();
						if (Arrays.equals(bytes, "watt".getBytes())) {
							outputStream.write(password.getBytes());
							outputStream.write(0);
							bytes = receive();
							if (Arrays.equals(bytes, "LOCK".getBytes())) {
								setLabelText("连接成功");
								setButtonStatus(BUTTON_ON);
							} else if (Arrays.equals(bytes, "UNLO".getBytes())) {
								setLabelText("连接成功");
								setButtonStatus(BUTTON_OFF);
							} else {
								setStatus(STATUS_WRONG_PASSWORD);
							}
						} else {
							setStatus(STATUS_CONNECT_FAILED);
						}
					} catch (Exception e) {
						setStatus(STATUS_CONNECT_FAILED);
					}
				} else {
					setStatus(STATUS_CONNECT_FAILED);
				}
			}
		});
		builder.setNegativeButton("取消", new DialogInterface.OnClickListener() {
			@Override
			public void onClick(DialogInterface dialog, int which) {
				setStatus(STATUS_CONNECT_FAILED);
			}
		});
		builder.show();
	}

	private void pairButtonClick() {
		setButtonStatus(BUTTON_PAIRING);
		final Thread thread = new Thread(new Runnable() {
			@Override
			public void run() {
				try {
					if (pair(MAC, PIN)) {
						setStatusHandler.sendEmptyMessage(STATUS_CONNECTING);
						socket = bluetoothAdapter.getRemoteDevice(MAC).createRfcommSocketToServiceRecord(UUID.fromString("00001101-0000-1000-8000-00805F9B34FB"));
						Thread.sleep(1000);
						socket.connect();
						outputStream = socket.getOutputStream();
						inputStream = socket.getInputStream();
						setStatusHandler.sendEmptyMessage(STATUS_PAIRED);
					} else {
						setStatusHandler.sendEmptyMessage(STATUS_CONNECT_FAILED);
					}
				} catch (Exception e) {
					setStatusHandler.sendEmptyMessage(STATUS_CONNECT_FAILED);
				}
			}
		});
		thread.start();
	}

	private void onButtonClick() {
		final Thread thread = new Thread(new Runnable() {
			@Override
			public void run() {
				try {
					outputStream.write("\0unlo\0".getBytes());
					if (Arrays.equals(receive(), "UNLO".getBytes())) {
						setStatusHandler.sendEmptyMessage(STATUS_TURNED);
					} else {
						setStatusHandler.sendEmptyMessage(STATUS_TURN_FAILED);
					}
				} catch (Exception e) {
					setStatusHandler.sendEmptyMessage(STATUS_CONNECT_FAILED);
				}
			}
		});
		thread.start();
	}

	private void offButtonClick() {
		final Thread thread = new Thread(new Runnable() {
			@Override
			public void run() {
				try {
					outputStream.write("\0lock\0".getBytes());
					if (Arrays.equals(receive(), "LOCK".getBytes())) {
						setStatusHandler.sendEmptyMessage(STATUS_TURNED);
					} else {
						setStatusHandler.sendEmptyMessage(STATUS_TURN_FAILED);
					}
				} catch (Exception e) {
					setStatusHandler.sendEmptyMessage(STATUS_CONNECT_FAILED);
				}
			}
		});
		thread.start();
	}

	@Override
	protected void onDestroy() {
		closeSocket();
		label[0].clearAnimation();
		label[1].clearAnimation();
		labelAnimation.cancel();
		newLabelAnimation.cancel();
		super.onDestroy();
	}

	@Override
	protected void onPause() {
		if (buttonStatus != BUTTON_PAIR && buttonStatus != BUTTON_PAIRING) {
			closeSocket();
			setButtonStatus(BUTTON_PAIR);
			setLabelText("已断开连接");
		}
		super.onPause();
	}

	@Override
	protected void onResume() {
		System.out.println("resume");
		super.onResume();
	}

	private static class SetStatusHandler extends Handler {
		private final WeakReference<MainActivity> mActivity;

		public SetStatusHandler(MainActivity activity) {
			mActivity = new WeakReference<>(activity);
		}

		@Override
		public void handleMessage(Message msg) {
			mActivity.get().setStatus(msg.what);
			super.handleMessage(msg);
		}
	}
}
