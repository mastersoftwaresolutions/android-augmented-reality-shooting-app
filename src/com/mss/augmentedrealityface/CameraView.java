package com.mss.augmentedrealityface;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PathMeasure;
import android.graphics.Point;
import android.graphics.PointF;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.YuvImage;
import android.graphics.drawable.AnimationDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.Drawable.Callback;
import android.hardware.Camera;
import android.hardware.Camera.PreviewCallback;
import android.hardware.Camera.Size;
import android.media.FaceDetector;
import android.os.Handler;
import android.util.AttributeSet;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.widget.Button;

@SuppressLint("DrawAllocation")
@SuppressWarnings("deprecation")
public class CameraView extends SurfaceView implements SurfaceHolder.Callback,
		PreviewCallback, Callback {

	private static final String TAGPREVIEW = "Preview";
	private static final int NUM_FACES = 1;
	private AnimationDrawable mAnimationDrawable;
	private SurfaceHolder mHolder;
	private Camera mCamera;
	private Bitmap mWorkBitmap, mBitmapTarImage, mBitmapGunImage, mBitmapBlood;
	private PointF mEyesMidPts[] = new PointF[NUM_FACES];
	private FaceDetector mFaceDetector;
	private FaceDetector.Face[] mFaces = new FaceDetector.Face[NUM_FACES];
	private FaceDetector.Face mFace = null;
	private float mEyesDistance[] = new float[NUM_FACES];
	private float mRatio, mXRatio, mYRatio;
	private Paint mPaintMidCircle = new Paint(Paint.ANTI_ALIAS_FLAG);
	private Paint mPaintBullet = new Paint(Paint.ANTI_ALIAS_FLAG);
	private Paint mPaintBlood = new Paint(Paint.ANTI_ALIAS_FLAG);
	private boolean isFaceFind, isShoot = false, isFaceDetected = false,
			isTargetDraw = true, isAllBloodShow = false;
	private Resources mResources;
	private float mRadius = 7;
	private Handler mHandler = new Handler();
	private Runnable mRunnable;
	private int mPicWidthTarget = 0, mPicHeightTarget = 0, mCount = 0, mLeft,
			mTop, mRight, mBottom, mLeftInc, mRightInc, mTopInc, mBottomInc,
			mLeftAnimBound, mRightAnimBound, mTopAnimBound, mBottomAnimBound,
			mLeftChange = 0, mTopChange = 0, mRightChange = 0,
			mBottomChange = 0, mButtonClick = 0;

	public CameraView(Context context, AttributeSet attrs) {
		super(context, attrs);

		mResources = context.getResources();
		mAnimationDrawable = (AnimationDrawable) mResources
				.getDrawable(R.drawable.blood_animation);
		mHolder = getHolder();
		mHolder.addCallback(this);
		mHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
		mHolder.setFormat(ImageFormat.NV21);
		mPaintMidCircle.setAntiAlias(true);
		mPaintMidCircle.setColor(Color.RED);
		mPaintMidCircle.setStyle(Paint.Style.STROKE);
		mPaintMidCircle.setStrokeWidth(2.0f);
		mPaintBullet.setAntiAlias(true);
		mPaintBullet.setStyle(Paint.Style.STROKE);
		mPaintBullet.setStrokeWidth(100);
		mPaintBullet.setColor(Color.BLACK);
		mPaintBullet.setStyle(Paint.Style.FILL);
		mPaintBlood.setAntiAlias(true);
		mPaintBlood.setStyle(Paint.Style.STROKE);
		mPaintBlood.setStrokeWidth(100);
		mPaintBlood.setColor(Color.RED);
		mPaintBlood.setStyle(Paint.Style.FILL);
		mBitmapGunImage = BitmapFactory.decodeResource(getResources(),
				R.drawable.gun_new);
		mBitmapBlood = BitmapFactory.decodeResource(getResources(),
				R.drawable.blood);
		mBitmapTarImage = BitmapFactory.decodeResource(getResources(),
				R.drawable.gun);
		mPicWidthTarget = mBitmapTarImage.getWidth();
		mPicHeightTarget = mBitmapTarImage.getHeight();
	}

	@Override
	public void surfaceCreated(SurfaceHolder holder) {
		try {
			mCamera = Camera.open();
			mCamera.setPreviewDisplay(holder);
		} catch (IOException exception) {
			mCamera.release();
			mCamera = null;
		}
		setWillNotDraw(false);
	}

	@Override
	public void surfaceDestroyed(SurfaceHolder holder) {
		if (mCamera != null) {
			mCamera.stopPreview();
			mCamera.setPreviewCallbackWithBuffer(null);
			mCamera.release();
			mCamera = null;
		}
		setWillNotDraw(true);
	}

	/**
	 * Method to gets the most appropriate previewSize.
	 * 
	 * @param sizes
	 *            : The supported preview sizes.
	 * @param width
	 *            : Width of surface
	 * @param height
	 *            : Height of surface
	 * @return optimalSize : Optimal previewSize
	 */
	private Size getOptimalPreviewSize(List<Size> sizes, int width, int height) {
		final double ASPECT_TOLERANCE = 0.05;
		double targetRatio = (double) width / height;
		if (sizes == null)
			return null;
		Size optimalSize = null;
		double minDiff = Double.MAX_VALUE;
		int targetHeight = height;
		/* Try to find an size match aspect ratio and size */
		for (Size size : sizes) {
			double ratio = (double) size.width / size.height;
			if (Math.abs(ratio - targetRatio) > ASPECT_TOLERANCE)
				continue;
			if (Math.abs(size.height - targetHeight) < minDiff) {
				optimalSize = size;
				minDiff = Math.abs(size.height - targetHeight);
			}
		}
		/* Cannot find the one match the aspect ratio, ignore the requirement */
		if (optimalSize == null) {
			minDiff = Double.MAX_VALUE;
			for (Size size : sizes) {
				if (Math.abs(size.height - targetHeight) < minDiff) {
					optimalSize = size;
					minDiff = Math.abs(size.height - targetHeight);
				}
			}
		}
		return optimalSize;
	}

	@Override
	public void surfaceChanged(SurfaceHolder holder, int format, int width,
			int height) {
		if (mCamera != null) {
			Camera.Parameters parameters = mCamera.getParameters();
			List<Size> sizes = parameters.getSupportedPreviewSizes();
			Size optimalSize = getOptimalPreviewSize(sizes, width, height);
			parameters.setPreviewSize(optimalSize.width, optimalSize.height);
			mCamera.setParameters(parameters);
			mCamera.startPreview();
			mWorkBitmap = Bitmap.createBitmap(optimalSize.width,
					optimalSize.height, Bitmap.Config.RGB_565);
			mFaceDetector = new FaceDetector(optimalSize.width,
					optimalSize.height, NUM_FACES);
			int bufSize = optimalSize.width
					* optimalSize.height
					* ImageFormat
							.getBitsPerPixel(parameters.getPreviewFormat());
			byte[] cbBuffer = new byte[bufSize];
			mCamera.setPreviewCallbackWithBuffer(this);
			mCamera.addCallbackBuffer(cbBuffer);
		}
	}

	@Override
	protected void onDraw(Canvas canvas) {
		Log.d(TAGPREVIEW, "onDraw");
		super.onDraw(canvas);
		int xCenterOfFace = 0;
		int yCenterOfFace = 0;
		float angle = 0;
		int xCoordinateOfGun = getWidth() / 2 - mBitmapGunImage.getWidth() / 2;
		int yCoordinateOfPath = getHeight() - mBitmapGunImage.getHeight() / 2;
		if (isFaceFind == true) {
			mXRatio = getWidth() * 1.0f / mWorkBitmap.getWidth();
			mYRatio = getHeight() * 1.0f / mWorkBitmap.getHeight();
			for (int i = 0; i < mEyesMidPts.length; i++) {
				if (mEyesMidPts[i] != null) {
					mRatio = mEyesDistance[i] * 4.0f / mPicWidthTarget;
					mLeft = (int) ((mEyesMidPts[0].x - mPicWidthTarget * mRatio
							/ 2.0f) * mXRatio);
					mTop = (int) ((mEyesMidPts[0].y - mPicHeightTarget * mRatio
							/ 2.0f) * mYRatio);
					mRight = (int) ((mEyesMidPts[0].x + mPicWidthTarget
							* mRatio / 2.0f) * mXRatio);
					mBottom = (int) ((mEyesMidPts[0].y + mPicHeightTarget
							* mRatio / 2.0f) * mYRatio);
					xCenterOfFace = (mLeft + mRight) / 2;
					yCenterOfFace = (mTop + mBottom) / 2;
				}
			}
			Point endPoint = new Point(xCenterOfFace, yCenterOfFace);
			Point beginPoint = new Point(getWidth() / 2, getHeight());
			angle = getAngle(endPoint, beginPoint);
			mLeftInc = xCenterOfFace - mBitmapBlood.getWidth();
			mTopInc = yCenterOfFace - mBitmapBlood.getHeight();
			mRightInc = xCenterOfFace + mBitmapBlood.getWidth();
			mBottomInc = yCenterOfFace + mBitmapBlood.getHeight();
			mLeftAnimBound = (int) (xCenterOfFace - mBitmapBlood.getWidth());
			mTopAnimBound = (int) (yCenterOfFace - mBitmapBlood.getHeight());
			mRightAnimBound = (int) (xCenterOfFace + mBitmapBlood.getWidth());
			mBottomAnimBound = (int) (yCenterOfFace + mBitmapBlood.getHeight());
			mAnimationDrawable.setBounds(mLeftAnimBound + mLeftChange,
					mTopAnimBound + mTopChange, mRightAnimBound + mRightChange,
					mBottomAnimBound + mBottomChange);
			/* Draw target at midpoint */
			if (isTargetDraw) {
				canvas.drawCircle(xCenterOfFace, yCenterOfFace, 3.0f,
						mPaintBlood);
				canvas.drawCircle(xCenterOfFace, yCenterOfFace, 6.0f,
						mPaintMidCircle);
			} else {
				RectF rectBloodBitmap1 = new RectF(mLeftInc, mTopInc,
						mRightInc, mBottomInc);
				RectF rectBloodBitmap2 = new RectF(mLeftInc + 15, mTopInc,
						mRightInc + 15, mBottomInc);
				RectF rectBloodBitmap3 = new RectF(mLeftInc, mTopInc - 15,
						mRightInc, mBottomInc - 15);
				RectF rectBloodBitmap4 = new RectF(mLeftInc - 15, mTopInc,
						mRightInc - 15, mBottomInc);
				RectF rectBloodBitmap5 = new RectF(mLeftInc, mTopInc + 15,
						mRightInc, mBottomInc + 15);
				RectF rectBloodBitmap6 = new RectF(mLeftInc - 15, mTopInc - 15,
						mRightInc - 15, mBottomInc - 15);
				RectF rectBloodBitmap7 = new RectF(mLeftInc + 15, mTopInc - 15,
						mRightInc + 15, mBottomInc - 15);
				RectF rectBloodBitmap8 = new RectF(mLeftInc - 15, mTopInc + 15,
						mRightInc - 15, mBottomInc + 15);
				RectF rectBloodBitmap9 = new RectF(mLeftInc + 15, mTopInc + 15,
						mRightInc + 15, mBottomInc + 15);
				if (mButtonClick == 1) {
					canvas.drawBitmap(mBitmapBlood, null, rectBloodBitmap1,
							mPaintMidCircle);
				} else if (mButtonClick == 2) {
					canvas.drawBitmap(mBitmapBlood, null, rectBloodBitmap1,
							mPaintMidCircle);
					canvas.drawBitmap(mBitmapBlood, null, rectBloodBitmap2,
							mPaintMidCircle);
				} else if (mButtonClick == 3) {
					canvas.drawBitmap(mBitmapBlood, null, rectBloodBitmap1,
							mPaintMidCircle);
					canvas.drawBitmap(mBitmapBlood, null, rectBloodBitmap2,
							mPaintMidCircle);
					canvas.drawBitmap(mBitmapBlood, null, rectBloodBitmap3,
							mPaintMidCircle);
				} else if (mButtonClick == 4) {
					canvas.drawBitmap(mBitmapBlood, null, rectBloodBitmap1,
							mPaintMidCircle);
					canvas.drawBitmap(mBitmapBlood, null, rectBloodBitmap2,
							mPaintMidCircle);
					canvas.drawBitmap(mBitmapBlood, null, rectBloodBitmap3,
							mPaintMidCircle);
					canvas.drawBitmap(mBitmapBlood, null, rectBloodBitmap4,
							mPaintMidCircle);
				} else if (mButtonClick == 5) {
					canvas.drawBitmap(mBitmapBlood, null, rectBloodBitmap1,
							mPaintMidCircle);
					canvas.drawBitmap(mBitmapBlood, null, rectBloodBitmap2,
							mPaintMidCircle);
					canvas.drawBitmap(mBitmapBlood, null, rectBloodBitmap3,
							mPaintMidCircle);
					canvas.drawBitmap(mBitmapBlood, null, rectBloodBitmap4,
							mPaintMidCircle);
					canvas.drawBitmap(mBitmapBlood, null, rectBloodBitmap5,
							mPaintMidCircle);
				} else if (mButtonClick == 6) {
					canvas.drawBitmap(mBitmapBlood, null, rectBloodBitmap1,
							mPaintMidCircle);
					canvas.drawBitmap(mBitmapBlood, null, rectBloodBitmap2,
							mPaintMidCircle);
					canvas.drawBitmap(mBitmapBlood, null, rectBloodBitmap3,
							mPaintMidCircle);
					canvas.drawBitmap(mBitmapBlood, null, rectBloodBitmap4,
							mPaintMidCircle);
					canvas.drawBitmap(mBitmapBlood, null, rectBloodBitmap5,
							mPaintMidCircle);
					canvas.drawBitmap(mBitmapBlood, null, rectBloodBitmap6,
							mPaintMidCircle);
				} else if (mButtonClick == 7) {
					canvas.drawBitmap(mBitmapBlood, null, rectBloodBitmap1,
							mPaintMidCircle);
					canvas.drawBitmap(mBitmapBlood, null, rectBloodBitmap2,
							mPaintMidCircle);
					canvas.drawBitmap(mBitmapBlood, null, rectBloodBitmap3,
							mPaintMidCircle);
					canvas.drawBitmap(mBitmapBlood, null, rectBloodBitmap4,
							mPaintMidCircle);
					canvas.drawBitmap(mBitmapBlood, null, rectBloodBitmap5,
							mPaintMidCircle);
					canvas.drawBitmap(mBitmapBlood, null, rectBloodBitmap6,
							mPaintMidCircle);
					canvas.drawBitmap(mBitmapBlood, null, rectBloodBitmap7,
							mPaintMidCircle);
				} else if (mButtonClick == 8) {
					canvas.drawBitmap(mBitmapBlood, null, rectBloodBitmap1,
							mPaintMidCircle);
					canvas.drawBitmap(mBitmapBlood, null, rectBloodBitmap2,
							mPaintMidCircle);
					canvas.drawBitmap(mBitmapBlood, null, rectBloodBitmap3,
							mPaintMidCircle);
					canvas.drawBitmap(mBitmapBlood, null, rectBloodBitmap4,
							mPaintMidCircle);
					canvas.drawBitmap(mBitmapBlood, null, rectBloodBitmap5,
							mPaintMidCircle);
					canvas.drawBitmap(mBitmapBlood, null, rectBloodBitmap6,
							mPaintMidCircle);
					canvas.drawBitmap(mBitmapBlood, null, rectBloodBitmap7,
							mPaintMidCircle);
					canvas.drawBitmap(mBitmapBlood, null, rectBloodBitmap8,
							mPaintMidCircle);
				} else if (mButtonClick == 9) {
					isAllBloodShow = true;
				}
				if (isAllBloodShow == true) {
					canvas.drawBitmap(mBitmapBlood, null, rectBloodBitmap1,
							mPaintMidCircle);
					canvas.drawBitmap(mBitmapBlood, null, rectBloodBitmap2,
							mPaintMidCircle);
					canvas.drawBitmap(mBitmapBlood, null, rectBloodBitmap3,
							mPaintMidCircle);
					canvas.drawBitmap(mBitmapBlood, null, rectBloodBitmap4,
							mPaintMidCircle);
					canvas.drawBitmap(mBitmapBlood, null, rectBloodBitmap5,
							mPaintMidCircle);
					canvas.drawBitmap(mBitmapBlood, null, rectBloodBitmap6,
							mPaintMidCircle);
					canvas.drawBitmap(mBitmapBlood, null, rectBloodBitmap7,
							mPaintMidCircle);
					canvas.drawBitmap(mBitmapBlood, null, rectBloodBitmap8,
							mPaintMidCircle);
					canvas.drawBitmap(mBitmapBlood, null, rectBloodBitmap9,
							mPaintMidCircle);
				}
			}
			Path path = new Path();
			path.moveTo(getWidth() / 2, yCoordinateOfPath);
			path.lineTo(xCenterOfFace, yCenterOfFace);
			PathMeasure pm = new PathMeasure(path, false);
			float lengthParts = pm.getLength() / 8;
			float position[] = { 0f, 0f };
			pm.getPosTan(lengthParts * mCount, position, null);
			mAnimationDrawable.draw(canvas);
			mAnimationDrawable.setCallback(this);
			if (mCount == 8) {
				if (mButtonClick == 1) {
					mLeftChange = 15;
					mTopChange = 0;
					mRightChange = 15;
					mBottomChange = 0;
				} else if (mButtonClick == 2) {
					mLeftChange = 0;
					mTopChange = -15;
					mRightChange = 0;
					mBottomChange = -15;
				} else if (mButtonClick == 3) {
					mLeftChange = -15;
					mTopChange = 0;
					mRightChange = -15;
					mBottomChange = 0;
				} else if (mButtonClick == 4) {
					mLeftChange = 0;
					mTopChange = 15;
					mRightChange = 0;
					mBottomChange = 15;
				} else if (mButtonClick == 5) {
					mLeftChange = -15;
					mTopChange = -15;
					mRightChange = -15;
					mBottomChange = -15;
				} else if (mButtonClick == 6) {
					mLeftChange = 15;
					mTopChange = -15;
					mRightChange = 15;
					mBottomChange = -15;
				} else if (mButtonClick == 7) {
					mLeftChange = -15;
					mTopChange = 15;
					mRightChange = -15;
					mBottomChange = 15;
				} else if (mButtonClick == 8) {
					mLeftChange = 15;
					mTopChange = 15;
					mRightChange = 15;
					mBottomChange = 15;
				} else {
					mLeftChange = 0;
					mTopChange = 0;
					mRightChange = 0;
					mBottomChange = 0;
				}
				mAnimationDrawable.start();
				if (mButtonClick <= 8) {
					mButtonClick++;
					System.out.println("ButtonClick : " + mButtonClick);
				} else {
					mButtonClick = 0;
				}
				mRadius = 8;
				mCount++;
			}
			if (isShoot == true) {
				if (mCount < 8) {
					canvas.drawCircle(position[0], position[1], mRadius,
							mPaintBullet);
					mCount++;
					mRadius = mRadius - 0.5f;
				}
			} else {
				if (mBtnShoot != null) {
					mBtnShoot.setClickable(true);
				}
			}
		} else {
			mCount = 0;
			isShoot = false;
			isAllBloodShow = false;
		}
		rotateImage(canvas, mBitmapGunImage, xCoordinateOfGun, getHeight()
				- mBitmapGunImage.getHeight(), angle);
		invalidate();
	}

	@Override
	public void onPreviewFrame(byte[] data, Camera camera) {
		/* convert the data from NV21 to RGB_565 */
		YuvImage yuv = new YuvImage(data, ImageFormat.NV21,
				mWorkBitmap.getWidth(), mWorkBitmap.getHeight(), null);
		Rect rect = new Rect(0, 0, mWorkBitmap.getWidth(),
				mWorkBitmap.getHeight());
		ByteArrayOutputStream baout = new ByteArrayOutputStream();
		if (!yuv.compressToJpeg(rect, 10, baout)) {
		}
		BitmapFactory.Options bfo = new BitmapFactory.Options();
		bfo.inPreferredConfig = Bitmap.Config.RGB_565;
		mWorkBitmap = BitmapFactory.decodeStream(
				new ByteArrayInputStream(baout.toByteArray()), null, bfo);
		if (isFaceDetected == false) {
			Arrays.fill(mFaces, null);
			Arrays.fill(mEyesMidPts, null);
			mFaceDetector.findFaces(mWorkBitmap, mFaces);
		}
		for (int i = 0; i < mFaces.length; i++) {
			mFace = mFaces[i];
			if (mFaces[0] == null) {
				isFaceFind = false;
				mAnimationDrawable = (AnimationDrawable) mResources
						.getDrawable(R.drawable.blood_animation_blank);
				mButtonClick = 0;
				isAllBloodShow = false;
				isTargetDraw = true;
			} else {
				isFaceFind = true;
				try {
					PointF eyesMP = new PointF();
					mFace.getMidPoint(eyesMP);
					mEyesDistance[0] = mFace.eyesDistance();
					mEyesMidPts[0] = eyesMP;
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		}
		invalidate();
		/* Requeue the buffer so we get called again */
		mCamera.addCallbackBuffer(data);
	}

	Button mBtnShoot;

	/**
	 * Method to start bullet movement from snipher.
	 */
	public void startBulletMovement(final Button button) {
		if (isFaceFind == true) {
			this.mBtnShoot = button;
			button.setClickable(false);
			if (mHandler != null) {
				mHandler.removeCallbacks(mRunnable);
			}
			mRunnable = new Runnable() {
				@Override
				public void run() {
					isTargetDraw = true;
					isAllBloodShow = false;
					mButtonClick = 0;
					mAnimationDrawable = (AnimationDrawable) mResources
							.getDrawable(R.drawable.blood_animation_blank);
				}
			};
			mCount = 0;
			mAnimationDrawable = (AnimationDrawable) mResources
					.getDrawable(R.drawable.blood_animation);
			isShoot = true;
			isFaceDetected = true;
			new Handler().postDelayed(new Runnable() {
				@Override
				public void run() {
					isFaceDetected = false;
					isShoot = false;
					isTargetDraw = false;
					mHandler.postDelayed(mRunnable, 5000);
				}
			}, 500);
		} else {
			mCount = 0;
			isShoot = false;
		}
	}

	@Override
	public void scheduleDrawable(Drawable who, Runnable what, long when) {
		Handler h = new Handler();
		h.postAtTime(what, when);
	}

	/**
	 * Image rotation method with particular angle.
	 * 
	 * @param canvas
	 *            : The canvas on which the image will be drawn
	 * @param bitmap
	 *            : Bitmap of image
	 * @param x
	 *            : X-Co-ordinates of image
	 * @param y
	 *            : Y-Co-ordinates of image
	 * @param rotationAngle
	 *            : Rotation angle
	 */
	public void rotateImage(Canvas canvas, Bitmap bitmap, int x, int y,
			float rotationAngle) {
		Matrix matrix = new Matrix();
		matrix.postRotate(rotationAngle, bitmap.getWidth() / 2,
				bitmap.getHeight() / 2);
		matrix.postTranslate(x, y);
		canvas.drawBitmap(bitmap, matrix, null);
	}

	/**
	 * Method of getting angle between two points.
	 * 
	 * @param endPoint
	 *            : X & Y Co-ordinates of the end point
	 * @param beginPoint
	 *            : X & Y Co-ordinates of the begin point
	 * @return angle : Angle between two points.
	 */
	public float getAngle(Point endPoint, Point beginPoint) {
		float angle = (float) Math.toDegrees(Math.atan2(endPoint.y
				- beginPoint.y, endPoint.x - beginPoint.x));
		if (angle < 0) {
			angle += 360;
		}
		return angle + 90;
	}
}