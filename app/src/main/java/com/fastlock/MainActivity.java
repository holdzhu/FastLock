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
import android.support.v7.app.ActionBarActivity;
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
import java.util.Arrays;
import java.util.LinkedList;
import java.util.Queue;
import java.util.UUID;

import at.markushi.ui.RevealColorView;

public class MainActivity extends ActionBarActivity {
	private final static String BUTTON_TEXT_UNSET = "长按配对设置";
	private final static String BUTTON_TEXT_PAIR = "配对";
	private final static String BUTTON_TEXT_ON = "开锁";
	private final static String BUTTON_TEXT_OFF = "上锁";
	private final static String PIN = "1234";
	private final static int LABEL_ANIMATION_DURATION = 300;
	private final static int LOADING_ANIMATION_DURATION = 300;
	private final SetStatusHandler setStatusHandler = new SetStatusHandler(this);
	private ButtonStatus buttonStatus = ButtonStatus.PAIR;
	private float labelTextSize;
	private String MAC = "";
	private BluetoothAdapter bluetoothAdapter;
	private OutputStream outputStream;
	private InputStream inputStream;
	private TextView[] label = new TextView[2];
	private int currentLabel = 0;
	private BluetoothSocket socket;
	private Button mainButton;
	private RevealColorView revealColorView;
	private ImageView loading;
	private Animation rotation;
	private Animation fadeIn;
	private Animation fadeOut;
	private Animation labelFadeIn;
	private Animation labelFadeOut;
	private Animation labelTranslateAnimation;
	private Animation newLabelTranslateAnimation;
	private AnimationSet loadingAnimation;
	private AnimationSet labelAnimation;
	private AnimationSet newLabelAnimation;
	private Queue<String> labelQueue = new LinkedList<>();
	private Point p;
	private boolean isInitialized = false;
	private String password = "";
	private SharedPreferences sp;

	private void setLabelText(final String s) {
		labelQueue.add(s);
		if (labelQueue.size() == 1) {
			label[1 - currentLabel].setText(s);
			label[currentLabel].setVisibility(View.INVISIBLE);
			label[currentLabel].startAnimation(labelAnimation);
			label[1 - currentLabel].startAnimation(newLabelAnimation);
			newLabelAnimation.setAnimationListener(new CallbackAnimationListener() {
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
			});
		}
	}

	private void fadeInRotation() {
		loadingAnimation = new AnimationSet(false);
		loadingAnimation.addAnimation(rotation);
		loadingAnimation.addAnimation(fadeIn);
		loading.setVisibility(View.VISIBLE);
		loading.startAnimation(loadingAnimation);
	}

	private void fadeOutRotation() {
		loadingAnimation.addAnimation(fadeOut);
		fadeOut.setAnimationListener(new CallbackAnimationListener() {
			@Override
			public void onAnimationEnd(Animation animation) {
				loading.setVisibility(View.INVISIBLE);
				loadingAnimation.cancel();
				loading.clearAnimation();
			}
		});
	}

	private void setButtonStatus(ButtonStatus status) {
		buttonStatus = status;
		switch (status) {
			case PAIR:
				mainButton.setText(BUTTON_TEXT_PAIR);
				mainButton.setEnabled(true);
				closeSocket();
				mainButton.setBackgroundResource(R.drawable.bg_pair_button);
				revealColorView.hide(p.x, p.y, Color.parseColor("#212121"), 0, 300, null);
				fadeOutRotation();
				break;
			case ON:
				mainButton.setText(BUTTON_TEXT_ON);
				mainButton.setEnabled(true);
				mainButton.setBackgroundResource(R.drawable.bg_on_button);
				revealColorView.reveal(p.x, p.y, Color.parseColor("#8bc34a"), mainButton.getHeight() / 2, 340, null);
				fadeOutRotation();
				break;
			case OFF:
				mainButton.setText(BUTTON_TEXT_OFF);
				mainButton.setEnabled(true);
				mainButton.setBackgroundResource(R.drawable.bg_off_button);
				revealColorView.reveal(p.x, p.y, Color.parseColor("#e91e63"), mainButton.getHeight() / 2, 340, null);
				fadeOutRotation();
				break;
			case PAIRING:
				mainButton.setEnabled(false);
				mainButton.setBackgroundResource(R.drawable.bg_pair_button);
				revealColorView.reveal(p.x, p.y, Color.parseColor("#3f51b5"), mainButton.getHeight() / 2, 340, null);
				fadeInRotation();
				break;
		}
	}

	private byte[] receive() {
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

	private void setPassword(String newPassword) {
		password = newPassword;
	}

	private void closeSocket() {
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
			setStatusHandler.sendEmptyMessage(Status.TURN_ON_BLUETOOTH.ordinal());
			bluetoothAdapter.enable();
			Thread.sleep(500);
		}
		if (!BluetoothAdapter.checkBluetoothAddress(strAddress)) {
			return false;
		}
		BluetoothDevice device = bluetoothAdapter.getRemoteDevice(strAddress);
		if (device.getBondState() != BluetoothDevice.BOND_BONDED) {
			setStatusHandler.sendEmptyMessage(Status.PAIRING.ordinal());
			try {
				ClsUtils.setPin(device.getClass(), device, strPsw);
				ClsUtils.createBond(device.getClass(), device);
				result = true;
			} catch (Exception e) {
				e.printStackTrace();
			}
		} else {
			try {
				ClsUtils.createBond(device.getClass(), device);
				ClsUtils.setPin(device.getClass(), device, strPsw);
				ClsUtils.createBond(device.getClass(), device);
				result = true;
			} catch (Exception e) {
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
			label[0].setText(BUTTON_TEXT_UNSET);
		}
		mainButton.setText(BUTTON_TEXT_PAIR);
		mainButton.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				switch (buttonStatus) {
					case PAIR:
						pairButtonClick();
						break;
					case ON:
						turn("\0unlo\0", "UNLO");
						break;
					case OFF:
						turn("\0lock\0", "LOCK");
						break;
				}
			}
		});
		mainButton.setOnLongClickListener(new View.OnLongClickListener() {
			@Override
			public boolean onLongClick(View v) {
				if (buttonStatus == ButtonStatus.OFF || buttonStatus == ButtonStatus.ON) {
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

	public void setStatus(Status status) {
		switch (status) {
			case PAIRING:
				setLabelText("配对中");
				break;
			case CONNECTING:
				setLabelText("连接中");
				break;
			case TURN_ON_BLUETOOTH:
				setLabelText("开启蓝牙中");
				break;
			case CHANGE_FAILED:
				setLabelText("更改失败");
				break;
			case CHANGED:
				setLabelText("已更改");
				break;
			case TURNED:
				if (buttonStatus == ButtonStatus.ON) {
					setLabelText("已开锁");
					setButtonStatus(ButtonStatus.OFF);
				} else {
					setLabelText("已上锁");
					setButtonStatus(ButtonStatus.ON);
				}
				break;
			case TURN_FAILED:
				if (buttonStatus == ButtonStatus.ON) {
					setLabelText("开锁失败");
				} else {
					setLabelText("上锁失败");
				}
				break;
			case PAIRED:
				onInputPassword();
				break;
			case CONNECT_FAILED:
				setLabelText("连接失败");
				setButtonStatus(ButtonStatus.PAIR);
				break;
			case WRONG_PASSWORD:
				setLabelText("密码错误");
				setButtonStatus(ButtonStatus.PAIR);
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
							setStatus(Status.CHANGED);
							setPassword(password);
						} else {
							setStatus(Status.CHANGE_FAILED);
						}
					} catch (Exception e) {
						setStatus(Status.CHANGE_FAILED);
					}
				} else {
					setStatus(Status.CHANGE_FAILED);
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
		AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
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
								setButtonStatus(ButtonStatus.ON);
							} else if (Arrays.equals(bytes, "UNLO".getBytes())) {
								setLabelText("连接成功");
								setButtonStatus(ButtonStatus.OFF);
							} else {
								setStatus(Status.WRONG_PASSWORD);
							}
						} else {
							setStatus(Status.CONNECT_FAILED);
						}
					} catch (Exception e) {
						setStatus(Status.CONNECT_FAILED);
					}
				} else {
					setStatus(Status.CONNECT_FAILED);
				}
			}
		});
		builder.setNegativeButton("取消", new DialogInterface.OnClickListener() {
			@Override
			public void onClick(DialogInterface dialog, int which) {
				setStatus(Status.CONNECT_FAILED);
			}
		});
		builder.show();
	}

	private void pairButtonClick() {
		setButtonStatus(ButtonStatus.PAIRING);
		final Thread thread = new Thread(new Runnable() {
			@Override
			public void run() {
				try {
					if (pair(MAC, PIN)) {
						setStatusHandler.sendEmptyMessage(Status.CONNECTING.ordinal());
						socket = bluetoothAdapter.getRemoteDevice(MAC).createRfcommSocketToServiceRecord(UUID.fromString("00001101-0000-1000-8000-00805F9B34FB"));
						Thread.sleep(1000);
						socket.connect();
						outputStream = socket.getOutputStream();
						inputStream = socket.getInputStream();
						setStatusHandler.sendEmptyMessage(Status.PAIRED.ordinal());
					} else {
						setStatusHandler.sendEmptyMessage(Status.CONNECT_FAILED.ordinal());
					}
				} catch (Exception e) {
					setStatusHandler.sendEmptyMessage(Status.CONNECT_FAILED.ordinal());
				}
			}
		});
		thread.start();
	}

	private void turn(final String sendString, final String receiveString) {
		final Thread thread = new Thread(new Runnable() {
			@Override
			public void run() {
				try {
					outputStream.write(sendString.getBytes());
					if (Arrays.equals(receive(), receiveString.getBytes())) {
						setStatusHandler.sendEmptyMessage(Status.TURNED.ordinal());
					} else {
						setStatusHandler.sendEmptyMessage(Status.TURN_FAILED.ordinal());
					}
				} catch (Exception e) {
					setStatusHandler.sendEmptyMessage(Status.CONNECT_FAILED.ordinal());
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
		if (buttonStatus != ButtonStatus.PAIR && buttonStatus != ButtonStatus.PAIRING) {
			closeSocket();
			setButtonStatus(ButtonStatus.PAIR);
			setLabelText("已断开连接");
		}
		super.onPause();
	}
}
