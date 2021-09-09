package org.tensorflow.lite.examples.detection.activities;

import androidx.appcompat.app.AppCompatActivity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.provider.MediaStore;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Toast;
import org.tensorflow.lite.examples.detection.R;
import org.tensorflow.lite.examples.detection.activities.backcamera.BackDetectorActivity;
import org.tensorflow.lite.examples.detection.activities.frontcamera.FrontDetectorActivity;
import org.tensorflow.lite.examples.detection.customview.OverlayView;
import org.tensorflow.lite.examples.detection.env.ImageUtils;
import org.tensorflow.lite.examples.detection.env.Logger;
import org.tensorflow.lite.examples.detection.env.Utils;
import org.tensorflow.lite.examples.detection.tflite.Classifier;
import org.tensorflow.lite.examples.detection.tflite.YoloV4Classifier;
import org.tensorflow.lite.examples.detection.tracking.MultiBoxTracker;
import org.tensorflow.lite.examples.detection.utils.Utilities;
import java.io.IOException;
import java.util.LinkedList;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    public static final float MINIMUM_CONFIDENCE_TF_OD_API = 0.5f;

    // Change these two to our custom .tflite model and mask-coco.txt
    private static final String TF_OD_API_MODEL_FILE = "custom_data/yolov4-tiny-416.tflite";
    private static final String TF_OD_API_LABELS_FILE = "file:///android_asset/custom_data/mask-coco.txt";
    private static final int SELECT_PHOTO = 1;
    private int imageCounter = 1;
    private boolean canDetectImage = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        backCameraButton = findViewById(R.id.backCameraButton);
        frontCameraButton = findViewById(R.id.frontCameraButton);
        detectButton = findViewById(R.id.detectButton);
        imageView = findViewById(R.id.imageView);
        changeImageButton = findViewById(R.id.changeImageButton);
        chooseImageButton = findViewById(R.id.chooseImageButton);

        backCameraButton.setOnClickListener(v -> startActivity(new Intent(MainActivity.this, BackDetectorActivity.class)));

        frontCameraButton.setOnClickListener(view -> startActivity(new Intent(MainActivity.this, FrontDetectorActivity.class)));

        chooseImageButton.setOnClickListener(view -> openGallery());

        detectButton.setOnClickListener(v -> {

            // detectButton.setEnabled(false);
            // detectButton.setBackgroundColor(getApplicationContext().getResources().getColor(R.color.tfe_button_disabled));


            if(canDetectImage) {
                Handler handler = new Handler();

                Utilities.toggleProgressDialogue(true, MainActivity.this);

                new Thread(() -> {
                    final List<Classifier.Recognition> results = detector.recognizeImage(cropBitmap);
                    handler.post(() -> handleResult(cropBitmap, results));
                }).start();
            }
            else {
                Utilities.showToast(MainActivity.this, "You have already detected the bounding boxes in this image");
            }

        });

        changeImageButton.setOnClickListener(view -> {
            imageCounter++;
            if(imageCounter > 10)
                imageCounter = 1;

            canDetectImage = true;
            this.sourceBitmap = Utils.getBitmapFromAsset(MainActivity.this, "custom_data/testimages/example" + imageCounter + ".png");
            this.cropBitmap = Utils.processBitmap(sourceBitmap, TF_OD_API_INPUT_SIZE);
            this.imageView.setImageBitmap(cropBitmap);
        });

        this.sourceBitmap = Utils.getBitmapFromAsset(MainActivity.this, "custom_data/testimages/example" + imageCounter + ".png");
        this.cropBitmap = Utils.processBitmap(sourceBitmap, TF_OD_API_INPUT_SIZE);
        this.imageView.setImageBitmap(cropBitmap);



        initBox();
    }

    private void openGallery() {
        Intent photoPickerIntent = new Intent(Intent.ACTION_PICK);
        photoPickerIntent.setType("image/*");
        startActivityForResult(photoPickerIntent, SELECT_PHOTO);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
        if (requestCode == SELECT_PHOTO) {
            if (resultCode == RESULT_OK) {
                if (intent != null) {
                    // Get the URI of the selected file
                    final Uri uri = intent.getData();
                    useImage(uri);
                }
            }
            super.onActivityResult(requestCode, resultCode, intent);

        }
    }

    void useImage(Uri uri) {
        Bitmap bitmap = null;
        try {
            bitmap = MediaStore.Images.Media.getBitmap(this.getContentResolver(), uri);
            // imageView.setImageBitmap(bitmap);
            // detectButton.setEnabled(true);
            // detectButton.setBackgroundColor(getApplicationContext().getResources().getColor(R.color.tfe_color_primary_dark));
            canDetectImage = true;
            this.sourceBitmap = bitmap;
            this.cropBitmap = Utils.processBitmap(sourceBitmap, TF_OD_API_INPUT_SIZE);
            this.imageView.setImageBitmap(cropBitmap);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static final Logger LOGGER = new Logger();

    public static final int TF_OD_API_INPUT_SIZE = 416;

    private static final boolean TF_OD_API_IS_QUANTIZED = false;

    // Minimum detection confidence to track a detection.
    private static final boolean MAINTAIN_ASPECT = false;
    private Integer sensorOrientation = 90;

    private Classifier detector;

    private Matrix frameToCropTransform;
    private Matrix cropToFrameTransform;
    private MultiBoxTracker tracker;
    private OverlayView trackingOverlay;

    protected int previewWidth = 0;
    protected int previewHeight = 0;

    private Bitmap sourceBitmap;
    private Bitmap cropBitmap;

    private Button backCameraButton, frontCameraButton, chooseImageButton, detectButton, changeImageButton;
    private ImageView imageView;

    private void initBox() {
        previewHeight = TF_OD_API_INPUT_SIZE;
        previewWidth = TF_OD_API_INPUT_SIZE;
        frameToCropTransform =
                ImageUtils.getTransformationMatrix(
                        previewWidth, previewHeight,
                        TF_OD_API_INPUT_SIZE, TF_OD_API_INPUT_SIZE,
                        sensorOrientation, MAINTAIN_ASPECT, false);

        cropToFrameTransform = new Matrix();
        frameToCropTransform.invert(cropToFrameTransform);

        tracker = new MultiBoxTracker(this);
        trackingOverlay = findViewById(R.id.tracking_overlay);
        trackingOverlay.addCallback(
                canvas -> tracker.draw(canvas, false));

        tracker.setFrameConfiguration(TF_OD_API_INPUT_SIZE, TF_OD_API_INPUT_SIZE, sensorOrientation);

        try {
            detector =
                    YoloV4Classifier.create(
                            getAssets(),
                            TF_OD_API_MODEL_FILE,
                            TF_OD_API_LABELS_FILE,
                            TF_OD_API_IS_QUANTIZED);
        } catch (final IOException e) {
            e.printStackTrace();
            LOGGER.e(e, "Exception initializing classifier!");
            Toast toast =
                    Toast.makeText(
                            getApplicationContext(), "Classifier could not be initialized", Toast.LENGTH_SHORT);
            toast.show();
            finish();
        }
    }

    private void handleResult(Bitmap bitmap, List<Classifier.Recognition> results) {
        final Canvas canvas = new Canvas(bitmap);

        // Mask paint red
        final Paint maskPaint = new Paint();
        maskPaint.setColor(Color.RED);
        maskPaint.setStyle(Paint.Style.STROKE);
        maskPaint.setStrokeWidth(2.0f);

        // No mask paint blue
        final Paint noMaskPaint = new Paint();
        noMaskPaint.setColor(Color.BLUE);
        noMaskPaint.setStyle(Paint.Style.STROKE);
        noMaskPaint.setStrokeWidth(2.0f);

        final List<Classifier.Recognition> mappedRecognitions = new LinkedList<Classifier.Recognition>();

        for (final Classifier.Recognition result : results) {
            final RectF location = result.getLocation();
            if (location != null && result.getConfidence() >= MINIMUM_CONFIDENCE_TF_OD_API) {

                // If mask red color
                if(result.getDetectedClass() == 0) {
                    canvas.drawRect(location, maskPaint);
                }
                // If no mask blue color
                else {
                    canvas.drawRect(location, noMaskPaint);
                }

//                cropToFrameTransform.mapRect(location);
//
//                result.setLocation(location);
//                mappedRecognitions.add(result);
            }

        }
//        tracker.trackResults(mappedRecognitions, new Random().nextInt());
//        trackingOverlay.postInvalidate();
        Utilities.toggleProgressDialogue(false, MainActivity.this);
        canDetectImage = false;
        imageView.setImageBitmap(bitmap);
    }
}
