package com.mss.augmentedrealityface;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;

public class MainActivity extends Activity implements OnClickListener {
	private static final String TAG = "MainActivity";
	private CameraView mCameraView;
	private Button mBtnShoot;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		Log.d(TAG, "onCreate");
		requestWindowFeature(Window.FEATURE_NO_TITLE);
		getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
		setContentView(R.layout.activity_main);
		mCameraView = (CameraView) findViewById(R.id.sv_camera);
		mBtnShoot = (Button) findViewById(R.id.btn_shoot);
		mBtnShoot.setOnClickListener(this);
	}

	@Override
	public void onClick(View v) {
		switch (v.getId()) {
		case R.id.btn_shoot:
			mCameraView.startBulletMovement(mBtnShoot);
			break;
		default:
			break;
		}
	}
}
