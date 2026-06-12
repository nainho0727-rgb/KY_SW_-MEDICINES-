package com.medicine.app.activities;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.cardview.widget.CardView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.ai.client.generativeai.GenerativeModel;
import com.google.ai.client.generativeai.java.GenerativeModelFutures;
import com.google.ai.client.generativeai.type.Content;
import com.google.ai.client.generativeai.type.GenerateContentResponse;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.korean.KoreanTextRecognizerOptions;
import com.medicine.app.BuildConfig;
import com.medicine.app.R;

import java.io.File;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class OcrActivity extends AppCompatActivity {

    private static final int CAMERA_PERMISSION_CODE = 101;
    private static final String TAG = "OcrActivity_Gemini";

    private PreviewView previewView;
    private Button btnCapture, btnAiResponse;
    private ImageButton btnBack, btnGallery;
    private CardView cardResult;
    private TextView tvOcrResult, tvAnalyzing;
    private ProgressBar progressAnalyzing;

    private ImageCapture imageCapture;
    private ExecutorService cameraExecutor;
    private TextRecognizer textRecognizer;
    private String recognizedText = "";

    private GenerativeModelFutures model;
    private Executor mainExecutor;

    private final ActivityResultLauncher<Intent> galleryLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    Uri imageUri = result.getData().getData();
                    if (imageUri != null) {
                        processImageFromUri(imageUri);
                    }
                }
            }
    );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ocr);

        // Gemini 모델 초기화
        GenerativeModel gm = new GenerativeModel("gemini-2.5-flash", BuildConfig.GEMINI_API_KEY);
        model = GenerativeModelFutures.from(gm);
        mainExecutor = ContextCompat.getMainExecutor(this);

        previewView = findViewById(R.id.preview_view);
        btnCapture = findViewById(R.id.btn_capture);
        btnAiResponse = findViewById(R.id.btn_ai_response);
        btnBack = findViewById(R.id.btn_back);
        btnGallery = findViewById(R.id.btn_gallery_select);
        cardResult = findViewById(R.id.card_result);
        tvOcrResult = findViewById(R.id.tv_ocr_result);
        tvAnalyzing = findViewById(R.id.tv_analyzing);
        progressAnalyzing = findViewById(R.id.progress_analyzing);

        cameraExecutor = Executors.newSingleThreadExecutor();
        textRecognizer = TextRecognition.getClient(new KoreanTextRecognizerOptions.Builder().build());

        btnBack.setOnClickListener(v -> finish());
        btnCapture.setOnClickListener(v -> captureImage());
        
        if (btnGallery != null) {
            btnGallery.setOnClickListener(v -> openGallery());
        }

        btnAiResponse.setOnClickListener(v -> {
            Log.d(TAG, "AI Response Button Clicked");
            if (recognizedText == null || recognizedText.trim().isEmpty()) {
                Log.e(TAG, "Recognized text is null or empty");
                Toast.makeText(this, "인식된 텍스트가 없습니다. 다시 촬영해주세요.", Toast.LENGTH_SHORT).show();
                return;
            }
            Log.d(TAG, "OCR Result to be sent: \n" + recognizedText);
            getAiMedicineInfo(recognizedText.trim());
        });

        showSelectionDialog();
    }

    private void showSelectionDialog() {
        String[] options = {"카메라로 촬영", "갤러리에서 선택"};
        new AlertDialog.Builder(this)
                .setTitle("이미지 가져오기")
                .setItems(options, (dialog, which) -> {
                    if (which == 0) {
                        checkCameraPermission();
                    } else {
                        openGallery();
                    }
                })
                .setCancelable(true)
                .show();
    }

    private void checkCameraPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED) {
            startCamera();
        } else {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.CAMERA},
                    CAMERA_PERMISSION_CODE);
        }
    }

    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture =
                ProcessCameraProvider.getInstance(this);

        cameraProviderFuture.addListener(() -> {
            try {
                ProcessCameraProvider cameraProvider = cameraProviderFuture.get();

                Preview preview = new Preview.Builder().build();
                preview.setSurfaceProvider(previewView.getSurfaceProvider());

                imageCapture = new ImageCapture.Builder()
                        .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                        .build();

                CameraSelector cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA;

                cameraProvider.unbindAll();
                cameraProvider.bindToLifecycle(
                        this, cameraSelector, preview, imageCapture);

            } catch (ExecutionException | InterruptedException e) {
                Log.e(TAG, "Camera start failed", e);
                Toast.makeText(this, "카메라 시작 실패", Toast.LENGTH_SHORT).show();
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void captureImage() {
        if (imageCapture == null) {
            Toast.makeText(this, "카메라가 준비되지 않았습니다.", Toast.LENGTH_SHORT).show();
            return;
        }

        setLoading(true);
        File photoFile = new File(getCacheDir(), "ocr_capture.jpg");
        ImageCapture.OutputFileOptions outputOptions =
                new ImageCapture.OutputFileOptions.Builder(photoFile).build();

        imageCapture.takePicture(outputOptions, ContextCompat.getMainExecutor(this),
                new ImageCapture.OnImageSavedCallback() {
                    @Override
                    public void onImageSaved(@NonNull ImageCapture.OutputFileResults outputFileResults) {
                        processImageFromFile(photoFile);
                    }

                    @Override
                    public void onError(@NonNull ImageCaptureException exception) {
                        setLoading(false);
                        Toast.makeText(OcrActivity.this, "촬영 실패: " + exception.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void openGallery() {
        Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        galleryLauncher.launch(intent);
    }

    private void processImageFromFile(File imageFile) {
        try {
            processImage(InputImage.fromFilePath(this, Uri.fromFile(imageFile)));
        } catch (Exception e) {
            Toast.makeText(this, "이미지 파일 로드 실패", Toast.LENGTH_SHORT).show();
        }
    }

    private void processImageFromUri(Uri uri) {
        try {
            processImage(InputImage.fromFilePath(this, uri));
        } catch (Exception e) {
            Toast.makeText(this, "이미지 처리 실패", Toast.LENGTH_SHORT).show();
        }
    }

    private void processImage(InputImage image) {
        setLoading(true);
        textRecognizer.process(image)
                .addOnSuccessListener(visionText -> {
                    recognizedText = visionText.getText().trim();
                    setLoading(false);

                    if (recognizedText.isEmpty()) {
                        Toast.makeText(this, "텍스트를 인식하지 못했습니다.", Toast.LENGTH_SHORT).show();
                    } else {
                        tvOcrResult.setText(recognizedText);
                        cardResult.setVisibility(View.VISIBLE);
                    }
                })
                .addOnFailureListener(e -> {
                    setLoading(false);
                    Toast.makeText(this, "OCR 분석 실패: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }

    private void getAiMedicineInfo(String ocrText) {
        setLoading(true);
        tvAnalyzing.setText("AI가 약 정보를 분석 중입니다...");

        String prompt = "다음 OCR 결과를 분석하여 약 이름, 효능, 복용법, 주의사항을 설명해주세요.\n\n" +
                "OCR 결과:\n" + ocrText;

        Log.d(TAG, "Full Prompt to Gemini: \n" + prompt);

        Content content = new Content.Builder()
                .addText(prompt)
                .build();

        try {
            ListenableFuture<GenerateContentResponse> response = model.generateContent(content);

            Futures.addCallback(response, new FutureCallback<GenerateContentResponse>() {
                @Override
                public void onSuccess(GenerateContentResponse result) {
                    String aiResponse = result.getText();
                    Log.d(TAG, "Gemini Success Response: " + aiResponse);
                    mainExecutor.execute(() -> {
                        setLoading(false);
                        showAiResultDialog(aiResponse);
                    });
                }

                @Override
                public void onFailure(Throwable t) {
                    Log.e(TAG, "Gemini API Failure: " + t.getMessage(), t);
                    mainExecutor.execute(() -> {
                        setLoading(false);
                        Toast.makeText(OcrActivity.this, "AI 설명을 불러오지 못했습니다.", Toast.LENGTH_SHORT).show();
                    });
                }
            }, cameraExecutor);
        } catch (Exception e) {
            Log.e(TAG, "Exception during Gemini call: " + e.getMessage(), e);
            setLoading(false);
            Toast.makeText(this, "AI 호출 중 예외 발생", Toast.LENGTH_SHORT).show();
        }
    }

    private void showAiResultDialog(String message) {
        new AlertDialog.Builder(this)
                .setTitle("💊 약 정보 분석 결과")
                .setMessage(message)
                .setPositiveButton("확인", null)
                .setCancelable(false)
                .show();
    }

    private void setLoading(boolean isLoading) {
        progressAnalyzing.setVisibility(isLoading ? View.VISIBLE : View.GONE);
        tvAnalyzing.setVisibility(isLoading ? View.VISIBLE : View.GONE);
        btnCapture.setEnabled(!isLoading);
        if (isLoading) {
            cardResult.setVisibility(View.GONE);
        } else {
            // 분석이 끝나면 결과 카드가 보여야 함 (OCR 성공 시에만)
            if (!recognizedText.isEmpty()) {
                cardResult.setVisibility(View.VISIBLE);
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == CAMERA_PERMISSION_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startCamera();
            } else {
                Toast.makeText(this, "카메라 권한이 필요합니다. 갤러리를 이용해 주세요.", Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (cameraExecutor != null) cameraExecutor.shutdown();
        if (textRecognizer != null) textRecognizer.close();
    }
}
