package com.medicine.app.activities;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
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
import android.media.ExifInterface;

import com.google.ai.client.generativeai.GenerativeModel;
import com.google.ai.client.generativeai.java.GenerativeModelFutures;
import com.google.ai.client.generativeai.type.Content;
import com.google.ai.client.generativeai.type.GenerateContentResponse;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.medicine.app.BuildConfig;
import com.medicine.app.R;
import com.medicine.app.views.CropOverlayView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.InputStream;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 약봉투/처방전을 촬영하거나 갤러리에서 선택 → 이미지를 그대로 Gemini 2.5 Flash(멀티모달)에
 * 보내 한 번에 (1) 글자 인식 + (2) 약 정보 구조화(JSON) 까지 처리한다.
 *
 * ML Kit 온디바이스 OCR + 칸 분할 방식은 한글 약봉투에서 일부만 인식되는 한계가 있어 제거.
 * Gemini 멀티모달이 이미지 전체를 읽고 약 이름/용량/횟수/복용시간/주의사항을 JSON 으로 반환한다.
 *
 * 결과는 setResult 로 호출한 화면(MyMedicineFragment)에 "medicines_json" 으로 돌려준다.
 */
public class OcrActivity extends AppCompatActivity {

    private static final int CAMERA_PERMISSION_CODE = 101;
    private static final String TAG = "OcrActivity_Gemini";
    private static final int MAX_DIM = 1536; // Gemini 전송 전 최대 변 길이(업로드 속도/비용 절감)
    /** 사용 모델 우선순위. 한도(429)에 막히면 다음 모델(별도 무료 할당량 버킷)로 자동 전환.
     *  2.0 계열은 이 키에서 limit=0 이라 제외. */
    private static final String[] MODELS = {
            "gemini-2.5-flash", "gemini-2.5-flash-lite", "gemini-flash-latest"
    };
    /** 일시적 503 시 같은 모델 재시도 횟수. */
    private static final int MAX_RETRIES = 2;

    private PreviewView previewView;
    private Button btnCapture, btnRegister, btnRetake;
    private ImageButton btnBack, btnGallery;
    private CardView cardResult;
    private TextView tvOcrResult, tvAnalyzing, tvScanHint;
    private ProgressBar progressAnalyzing;
    private View scanOverlay;
    private CropOverlayView cropOverlay;

    private ImageCapture imageCapture;
    private ProcessCameraProvider cameraProvider;
    private ExecutorService cameraExecutor;
    private Executor mainExecutor;

    /** true = 촬영 후 크롭 화면(분석 대기), false = 라이브 카메라. */
    private boolean cropMode = false;

    /** 직전에 성공한 모델 인덱스. 한도로 막힌 모델을 매번 다시 찌르지 않도록 여기서 시작. */
    private volatile int preferredModelIndex = 0;

    /** Gemini 가 돌려준 약 목록 JSON (배열). 등록 버튼에서 결과로 반환. */
    private String medicinesJson = null;

    /** 문서 전체 공통 주의사항(warnings). 등록 시 각 약 메모에 덧붙여 전달. */
    private String warningsText = null;

    private final ActivityResultLauncher<Intent> galleryLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    Uri imageUri = result.getData().getData();
                    if (imageUri != null) {
                        Bitmap bmp = loadBitmap(imageUri);
                        if (bmp != null) enterCropMode(bmp);
                        else Toast.makeText(this, "이미지를 불러오지 못했습니다.", Toast.LENGTH_SHORT).show();
                    }
                }
            }
    );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ocr);

        mainExecutor = ContextCompat.getMainExecutor(this);

        previewView = findViewById(R.id.preview_view);
        btnCapture = findViewById(R.id.btn_capture);
        btnRetake = findViewById(R.id.btn_retake);
        btnRegister = findViewById(R.id.btn_ai_response); // 기존 버튼 재사용 (등록 버튼으로)
        btnBack = findViewById(R.id.btn_back);
        btnGallery = findViewById(R.id.btn_gallery_select);
        cardResult = findViewById(R.id.card_result);
        tvOcrResult = findViewById(R.id.tv_ocr_result);
        tvAnalyzing = findViewById(R.id.tv_analyzing);
        tvScanHint = findViewById(R.id.tv_scan_hint);
        progressAnalyzing = findViewById(R.id.progress_analyzing);
        scanOverlay = findViewById(R.id.scan_overlay);
        cropOverlay = findViewById(R.id.crop_overlay);

        cameraExecutor = Executors.newSingleThreadExecutor();

        btnBack.setOnClickListener(v -> finish());
        // 라이브 상태면 촬영, 크롭 상태면 선택 영역 분석
        btnCapture.setOnClickListener(v -> {
            if (cropMode) analyzeCroppedRegion();
            else captureImage();
        });
        btnRetake.setOnClickListener(v -> retake());
        if (btnGallery != null) btnGallery.setOnClickListener(v -> openGallery());

        btnRegister.setText("✅ 이 약들 등록하기");
        btnRegister.setOnClickListener(v -> {
            if (medicinesJson == null || medicinesJson.isEmpty()) {
                Toast.makeText(this, "먼저 약봉투를 스캔해주세요.", Toast.LENGTH_SHORT).show();
                return;
            }
            Intent data = new Intent();
            data.putExtra("medicines_json", medicinesJson);
            if (warningsText != null && !warningsText.isEmpty()) {
                data.putExtra("warnings_text", warningsText);
            }
            setResult(RESULT_OK, data);
            finish();
        });

        showSelectionDialog();
    }

    private void showSelectionDialog() {
        String[] options = {"카메라로 촬영", "갤러리에서 선택"};
        new AlertDialog.Builder(this)
                .setTitle("이미지 가져오기")
                .setItems(options, (dialog, which) -> {
                    if (which == 0) checkCameraPermission();
                    else openGallery();
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
                    new String[]{Manifest.permission.CAMERA}, CAMERA_PERMISSION_CODE);
        }
    }

    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture =
                ProcessCameraProvider.getInstance(this);
        cameraProviderFuture.addListener(() -> {
            try {
                cameraProvider = cameraProviderFuture.get();
                Preview preview = new Preview.Builder().build();
                preview.setSurfaceProvider(previewView.getSurfaceProvider());

                // 글자 선명도 우선(품질 모드) → 작은 약 이름까지 또렷하게 촬영
                imageCapture = new ImageCapture.Builder()
                        .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                        .build();

                CameraSelector cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA;
                cameraProvider.unbindAll();
                cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageCapture);
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
                    public void onImageSaved(@NonNull ImageCapture.OutputFileResults out) {
                        Bitmap bmp = loadBitmap(Uri.fromFile(photoFile));
                        if (bmp != null) enterCropMode(bmp); // 분석 대신 정지 화면 + 크롭 단계로
                        else {
                            setLoading(false);
                            Toast.makeText(OcrActivity.this, "이미지 로드 실패", Toast.LENGTH_SHORT).show();
                        }
                    }

                    @Override
                    public void onError(@NonNull ImageCaptureException e) {
                        setLoading(false);
                        Toast.makeText(OcrActivity.this, "촬영 실패: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void openGallery() {
        Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        galleryLauncher.launch(intent);
    }

    /**
     * 촬영/선택한 사진을 정지 화면으로 보여주고 크롭 박스를 띄운다.
     * 카메라는 unbind 해서 라이브 화면을 멈춘다(폰을 들고 있을 필요 없음).
     */
    private void enterCropMode(Bitmap bmp) {
        setLoading(false);
        cropMode = true;

        if (cameraProvider != null) cameraProvider.unbindAll();

        previewView.setVisibility(View.GONE);
        if (scanOverlay != null) scanOverlay.setVisibility(View.GONE);
        if (tvScanHint != null) tvScanHint.setVisibility(View.GONE);

        cropOverlay.setBitmap(bmp);
        cropOverlay.setVisibility(View.VISIBLE);

        cardResult.setVisibility(View.GONE);
        medicinesJson = null;

        btnRetake.setVisibility(View.VISIBLE);
        btnCapture.setText("🔍  선택 영역 분석하기");
    }

    /** 크롭 박스 영역만 잘라 Gemini 로 분석. */
    private void analyzeCroppedRegion() {
        Bitmap cropped = cropOverlay.getCroppedBitmap();
        if (cropped == null) {
            Toast.makeText(this, "분석할 영역이 없습니다. 다시 촬영해주세요.", Toast.LENGTH_SHORT).show();
            return;
        }
        analyzeWithGemini(cropped);
    }

    /** 라이브 카메라로 복귀(다시 촬영). */
    private void retake() {
        cropMode = false;
        cropOverlay.clear();
        cropOverlay.setVisibility(View.GONE);
        btnRetake.setVisibility(View.GONE);
        cardResult.setVisibility(View.GONE);
        medicinesJson = null;

        previewView.setVisibility(View.VISIBLE);
        if (scanOverlay != null) scanOverlay.setVisibility(View.VISIBLE);
        if (tvScanHint != null) tvScanHint.setVisibility(View.VISIBLE);

        btnCapture.setText("📷  촬영");
        startCamera(); // 카메라 재바인딩
    }

    /** URI → 적당히 다운스케일 + EXIF 회전 보정된 Bitmap. */
    private Bitmap loadBitmap(Uri uri) {
        try {
            // 1) 크기만 먼저 읽어 샘플링 비율 계산
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            try (InputStream is = getContentResolver().openInputStream(uri)) {
                BitmapFactory.decodeStream(is, null, bounds);
            }
            int sample = 1;
            int longer = Math.max(bounds.outWidth, bounds.outHeight);
            while (longer / sample > MAX_DIM * 2) sample *= 2;

            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inSampleSize = sample;
            Bitmap bmp;
            try (InputStream is = getContentResolver().openInputStream(uri)) {
                bmp = BitmapFactory.decodeStream(is, null, opts);
            }
            if (bmp == null) return null;

            // 2) EXIF 회전 보정
            int rotation = 0;
            try (InputStream is = getContentResolver().openInputStream(uri)) {
                if (is != null) {
                    ExifInterface exif = new ExifInterface(is);
                    int o = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION,
                            ExifInterface.ORIENTATION_NORMAL);
                    if (o == ExifInterface.ORIENTATION_ROTATE_90) rotation = 90;
                    else if (o == ExifInterface.ORIENTATION_ROTATE_180) rotation = 180;
                    else if (o == ExifInterface.ORIENTATION_ROTATE_270) rotation = 270;
                }
            }
            if (rotation != 0) {
                Matrix m = new Matrix();
                m.postRotate(rotation);
                bmp = Bitmap.createBitmap(bmp, 0, 0, bmp.getWidth(), bmp.getHeight(), m, true);
            }
            return bmp;
        } catch (Exception e) {
            Log.e(TAG, "loadBitmap failed", e);
            return null;
        }
    }

    /** 이미지를 Gemini 에 직접 보내 약 정보를 JSON 으로 추출. */
    private void analyzeWithGemini(Bitmap bitmap) {
        analyzeWithGemini(bitmap, 0, preferredModelIndex);
    }

    private void analyzeWithGemini(Bitmap bitmap, int attempt, int modelIndex) {
        setLoading(true);
        tvAnalyzing.setText(attempt == 0
                ? "AI가 약봉투를 분석 중입니다..."
                : "서버가 혼잡해 다시 시도 중입니다...");

        String prompt =
                "너는 한국 약국의 약봉투/처방전을 분석하는 전문가야. 이미지에 있는 모든 글자를 빠짐없이 읽고, " +
                "각 약의 정보를 추출해서 JSON 으로만 답해. 설명, 마크다운, 코드블록 없이 JSON 객체 하나만 출력해.\n\n" +
                "형식:\n" +
                "{\n" +
                "  \"medicines\": [\n" +
                "    {\n" +
                "      \"name\": \"약 이름(상품명/성분명)\",\n" +
                "      \"category\": \"이 약의 분류/효능 (예: 위산과다증약, 소화진통제, 진경제, 항생제). 이미지에 적혀 있으면 그대로, 없으면 빈 문자열\",\n" +
                "      \"dose\": \"1회 복용량 (예: 1정, 1포, 5ml)\",\n" +
                "      \"frequency\": \"하루 복용 횟수/방법 (예: 하루 3회 식후 30분)\",\n" +
                "      \"timeSlots\": [\"morning\",\"lunch\",\"evening\",\"bedtime\" 중 해당하는 것],\n" +
                "      \"cautions\": \"이 약의 주의사항/부작용 한두 줄\"\n" +
                "    }\n" +
                "  ],\n" +
                "  \"warnings\": \"전체 병용금기/함께 복용 시 주의할 점 요약\"\n" +
                "}\n\n" +
                "timeSlots 규칙: '하루 3회'면 [\"morning\",\"lunch\",\"evening\"], '아침저녁 2회'면 " +
                "[\"morning\",\"evening\"], '하루 1회'면 가장 적절한 시간 하나, '취침 전/자기 전'이면 " +
                "\"bedtime\" 포함. 식전/식후 등 문구로 추론해. 정보가 없으면 합리적으로 추정하되 비우지 마.\n\n" +
                "표 읽기: '약품명 | 1회 투약량 | 1일 투여횟수' 같은 표가 있으면 그 숫자로 dose 와 frequency 를 정해. " +
                "예: '씨프로바이정  1  2' 면 dose=\"1정\", frequency=\"하루 2회\", timeSlots 는 2회에 맞춰 [\"morning\",\"evening\"].\n" +
                "주의사항: 이미지의 '주의사항', '효능·복약안내' 칸을 꼼꼼히 읽어 각 약에 해당하는 내용을 cautions 에 최대한 담아. " +
                "특정 약과 연결이 안 되는 일반 주의(예: '증상이 개선되어도 임의로 중단 말 것', '음주 시 복용 주의')는 warnings 에 모아 넣어.\n" +
                "category 는 이미지에 약 분류(예: '위산과다증약')가 적혀 있을 때만 그대로 넣고, 없으면 빈 문자열로 둬(추측 금지).\n" +
                "약을 하나도 못 읽으면 medicines 를 빈 배열로 해.";

        Content content = new Content.Builder()
                .addText(prompt)
                .addImage(bitmap)
                .build();

        GenerativeModelFutures model = GenerativeModelFutures.from(new GenerativeModel(
                MODELS[Math.min(modelIndex, MODELS.length - 1)], BuildConfig.GEMINI_API_KEY));
        ListenableFuture<GenerateContentResponse> response = model.generateContent(content);
        Futures.addCallback(response, new FutureCallback<GenerateContentResponse>() {
            @Override
            public void onSuccess(GenerateContentResponse result) {
                preferredModelIndex = modelIndex; // 이 모델이 성공 → 다음부터 여기서 시작
                final String raw = result.getText();
                Log.d(TAG, "Gemini raw: " + raw);
                mainExecutor.execute(() -> handleGeminiResult(raw));
            }

            @Override
            public void onFailure(@NonNull Throwable t) {
                Log.e(TAG, "Gemini failure (model=" + modelIndex + ", attempt " + attempt + ")", t);
                boolean hasNextModel = modelIndex + 1 < MODELS.length;
                // 1) 한도(429): 다음 모델 버킷으로 즉시 전환
                if (isQuota(t) && hasNextModel) {
                    mainExecutor.execute(() -> progressAnalyzing.post(
                            () -> analyzeWithGemini(bitmap, 0, modelIndex + 1)));
                    return;
                }
                // 2) 일시적 과부하(503): 같은 모델 백오프 재시도(로딩 유지)
                if (!isQuota(t) && isRetryable(t) && attempt < MAX_RETRIES) {
                    long delayMs = 1500L * (attempt + 1); // 1.5s → 3s
                    mainExecutor.execute(() -> progressAnalyzing.postDelayed(
                            () -> analyzeWithGemini(bitmap, attempt + 1, modelIndex), delayMs));
                    return;
                }
                // 3) 재시도 소진 → 남은 다른 모델로 전환
                if (hasNextModel) {
                    mainExecutor.execute(() -> progressAnalyzing.post(
                            () -> analyzeWithGemini(bitmap, 0, modelIndex + 1)));
                    return;
                }
                // 4) 모든 모델 실패 → 안내
                mainExecutor.execute(() -> {
                    setLoading(false);
                    Toast.makeText(OcrActivity.this, friendlyError(t), Toast.LENGTH_LONG).show();
                });
            }
        }, cameraExecutor);
    }

    /** 사용량 한도(429/RESOURCE_EXHAUSTED) 오류인지. */
    private boolean isQuota(Throwable t) {
        String m = (t == null || t.getMessage() == null) ? "" : t.getMessage().toLowerCase();
        return m.contains("429") || m.contains("resource_exhausted") || m.contains("quota");
    }

    /**
     * 서버 과부하/일시적 오류면 재시도 대상.
     * SDK 가 503 응답을 제대로 파싱 못 해 MissingFieldException 으로 던지는 케이스도 포함.
     */
    private boolean isRetryable(Throwable t) {
        String m = (t == null || t.getMessage() == null) ? "" : t.getMessage().toLowerCase();
        return m.contains("503") || m.contains("unavailable") || m.contains("high demand")
                || m.contains("overload") || m.contains("500") || m.contains("internal")
                || m.contains("timeout") || m.contains("deadline")
                || m.contains("missingfield") || m.contains("unexpected response");
    }

    /** 사용자에게 보여줄 깔끔한 안내 문구. */
    private String friendlyError(Throwable t) {
        String m = (t == null || t.getMessage() == null) ? "" : t.getMessage().toLowerCase();
        if (m.contains("429") || m.contains("resource_exhausted") || m.contains("quota")) {
            return "오늘 무료 AI 사용량 한도에 도달했습니다. 잠시 후 다시 시도해주세요.";
        }
        if (m.contains("503") || m.contains("unavailable") || m.contains("high demand")
                || m.contains("overload") || m.contains("missingfield")) {
            return "AI 서버가 잠시 혼잡합니다. 잠시 후 다시 시도해주세요.";
        }
        return "AI 분석에 실패했습니다. 인터넷 연결을 확인하고 다시 시도해주세요.";
    }

    private void handleGeminiResult(String raw) {
        setLoading(false);
        if (raw == null) {
            Toast.makeText(this, "분석 결과가 없습니다.", Toast.LENGTH_SHORT).show();
            return;
        }
        String json = extractJson(raw);
        try {
            JSONObject root = new JSONObject(json);
            JSONArray meds = root.optJSONArray("medicines");
            if (meds == null || meds.length() == 0) {
                tvOcrResult.setText("약 정보를 찾지 못했어요. 글자가 잘 보이게 다시 촬영해주세요.");
                cardResult.setVisibility(View.VISIBLE);
                medicinesJson = null;
                warningsText = null;
                return;
            }

            // 화면에 보여줄 요약 + 결과로 넘길 배열 보관
            medicinesJson = meds.toString();
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < meds.length(); i++) {
                JSONObject m = meds.getJSONObject(i);
                sb.append("• ").append(m.optString("name", "(이름 미상)"));
                String category = m.optString("category", "").trim();
                if (!category.isEmpty()) sb.append(" (").append(category).append(")");
                String freq = m.optString("frequency", "");
                if (!freq.isEmpty()) sb.append(" — ").append(freq);
                sb.append("\n");
            }
            warningsText = root.optString("warnings", "").trim();
            if (!warningsText.isEmpty()) sb.append("\n⚠ ").append(warningsText);

            tvOcrResult.setText(sb.toString().trim());
            cardResult.setVisibility(View.VISIBLE);
        } catch (Exception e) {
            Log.e(TAG, "JSON parse failed. raw=" + raw, e);
            // 파싱 실패 시에도 사용자가 원문은 볼 수 있게
            medicinesJson = null;
            warningsText = null;
            tvOcrResult.setText(raw);
            cardResult.setVisibility(View.VISIBLE);
            Toast.makeText(this, "결과 형식을 분석하지 못했습니다. 다시 시도해주세요.", Toast.LENGTH_SHORT).show();
        }
    }

    /** Gemini 응답에서 코드펜스/잡텍스트를 걷어내고 순수 JSON 부분만 추출. */
    private String extractJson(String raw) {
        String s = raw.trim();
        // ```json ... ``` 제거
        if (s.startsWith("```")) {
            int firstNl = s.indexOf('\n');
            if (firstNl != -1) s = s.substring(firstNl + 1);
            if (s.endsWith("```")) s = s.substring(0, s.length() - 3);
            s = s.trim();
        }
        // 첫 { 부터 마지막 } 까지
        int start = s.indexOf('{');
        int end = s.lastIndexOf('}');
        if (start != -1 && end != -1 && end > start) {
            return s.substring(start, end + 1);
        }
        return s;
    }

    private void setLoading(boolean isLoading) {
        progressAnalyzing.setVisibility(isLoading ? View.VISIBLE : View.GONE);
        tvAnalyzing.setVisibility(isLoading ? View.VISIBLE : View.GONE);
        btnCapture.setEnabled(!isLoading);
        if (isLoading) cardResult.setVisibility(View.GONE);
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
    }
}
