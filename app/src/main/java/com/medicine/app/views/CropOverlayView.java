package com.medicine.app.views;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.Nullable;

/**
 * 촬영한 사진을 정지 화면으로 보여주고, 그 위에서 손가락으로 위치/크기를 조절할 수 있는
 * 크롭(자르기) 박스를 제공하는 커스텀 뷰.
 *
 * - 비트맵을 fitCenter 로 그려 넣고, 박스 바깥은 어둡게 처리.
 * - 박스 내부를 드래그하면 이동, 네 모서리를 잡고 드래그하면 크기 조절.
 * - getCroppedBitmap() 으로 박스 영역만 잘라낸 비트맵을 돌려준다(원본 좌표로 매핑).
 *
 * 외부 라이브러리 없이 동작하며, OcrActivity 의 "촬영 후 영역 조절" 단계에 사용된다.
 */
public class CropOverlayView extends View {

    private Bitmap bitmap;
    private final Matrix drawMatrix = new Matrix(); // bitmap -> view 좌표
    private final Matrix invMatrix = new Matrix();  // view -> bitmap 좌표
    private final RectF imageRect = new RectF();     // 비트맵이 실제로 그려진 영역(view 좌표)
    private final RectF cropRect = new RectF();       // 크롭 선택 영역(view 좌표)

    private final Paint dimPaint = new Paint();
    private final Paint borderPaint = new Paint();
    private final Paint handlePaint = new Paint();

    private float handleTouchPx;  // 모서리 터치 허용 반경
    private float minSizePx;       // 크롭 박스 최소 크기
    private float handleLen;        // 모서리 ㄱ자 길이
    private float handleThick;      // 모서리 ㄱ자 두께

    private static final int HANDLE_NONE = -1;
    private static final int HANDLE_MOVE = 0;
    private static final int HANDLE_TL = 1;
    private static final int HANDLE_TR = 2;
    private static final int HANDLE_BL = 3;
    private static final int HANDLE_BR = 4;

    private int activeHandle = HANDLE_NONE;
    private float lastX, lastY;

    public CropOverlayView(Context context) {
        super(context);
        init();
    }

    public CropOverlayView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        float d = getResources().getDisplayMetrics().density;
        handleTouchPx = 24f * d;
        minSizePx = 64f * d;
        handleLen = 20f * d;
        handleThick = 4f * d;

        dimPaint.setColor(Color.parseColor("#A6000000"));
        borderPaint.setStyle(Paint.Style.STROKE);
        borderPaint.setStrokeWidth(2f * d);
        borderPaint.setColor(Color.WHITE);
        borderPaint.setAntiAlias(true);
        handlePaint.setStyle(Paint.Style.FILL);
        handlePaint.setColor(Color.WHITE);
        handlePaint.setAntiAlias(true);
    }

    /** 정지 화면으로 보여줄 비트맵 설정. 뷰 크기가 정해지면 박스를 초기화한다. */
    public void setBitmap(Bitmap bmp) {
        this.bitmap = bmp;
        this.activeHandle = HANDLE_NONE;
        if (getWidth() > 0 && getHeight() > 0) {
            computeImageRect();
            initCropRect();
        }
        invalidate();
    }

    public void clear() {
        this.bitmap = null;
        invalidate();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (bitmap != null) {
            computeImageRect();
            initCropRect();
        }
    }

    private void computeImageRect() {
        if (bitmap == null) return;
        float vw = getWidth(), vh = getHeight();
        float bw = bitmap.getWidth(), bh = bitmap.getHeight();
        if (bw <= 0 || bh <= 0) return;
        float scale = Math.min(vw / bw, vh / bh); // fitCenter
        float dw = bw * scale, dh = bh * scale;
        float left = (vw - dw) / 2f, top = (vh - dh) / 2f;
        imageRect.set(left, top, left + dw, top + dh);
        drawMatrix.reset();
        drawMatrix.postScale(scale, scale);
        drawMatrix.postTranslate(left, top);
        drawMatrix.invert(invMatrix);
    }

    private void initCropRect() {
        // 기본값: 이미지 중앙, 가로 80% · 세로 60% (대부분의 약봉투를 넉넉히 포함)
        float cx = imageRect.centerX(), cy = imageRect.centerY();
        float w = imageRect.width() * 0.8f;
        float h = imageRect.height() * 0.6f;
        cropRect.set(cx - w / 2f, cy - h / 2f, cx + w / 2f, cy + h / 2f);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (bitmap == null) return;

        canvas.drawBitmap(bitmap, drawMatrix, null);

        // 크롭 박스 바깥을 어둡게(이미지 영역 기준 4분할)
        canvas.drawRect(imageRect.left, imageRect.top, imageRect.right, cropRect.top, dimPaint);
        canvas.drawRect(imageRect.left, cropRect.bottom, imageRect.right, imageRect.bottom, dimPaint);
        canvas.drawRect(imageRect.left, cropRect.top, cropRect.left, cropRect.bottom, dimPaint);
        canvas.drawRect(cropRect.right, cropRect.top, imageRect.right, cropRect.bottom, dimPaint);

        // 테두리
        canvas.drawRect(cropRect, borderPaint);

        // 네 모서리 ㄱ자 핸들
        drawCorner(canvas, cropRect.left, cropRect.top, 1, 1);
        drawCorner(canvas, cropRect.right, cropRect.top, -1, 1);
        drawCorner(canvas, cropRect.left, cropRect.bottom, 1, -1);
        drawCorner(canvas, cropRect.right, cropRect.bottom, -1, -1);
    }

    private void drawCorner(Canvas c, float x, float y, int dx, int dy) {
        // 가로 팔
        float hx1 = dx > 0 ? x : x - handleLen;
        float hx2 = dx > 0 ? x + handleLen : x;
        float hy1 = dy > 0 ? y : y - handleThick;
        float hy2 = dy > 0 ? y + handleThick : y;
        c.drawRect(hx1, hy1, hx2, hy2, handlePaint);
        // 세로 팔
        float vx1 = dx > 0 ? x : x - handleThick;
        float vx2 = dx > 0 ? x + handleThick : x;
        float vy1 = dy > 0 ? y : y - handleLen;
        float vy2 = dy > 0 ? y + handleLen : y;
        c.drawRect(vx1, vy1, vx2, vy2, handlePaint);
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (bitmap == null) return false;
        float x = e.getX(), y = e.getY();
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                activeHandle = hitTest(x, y);
                lastX = x;
                lastY = y;
                return activeHandle != HANDLE_NONE;
            case MotionEvent.ACTION_MOVE:
                if (activeHandle == HANDLE_NONE) return false;
                applyDrag(x - lastX, y - lastY);
                lastX = x;
                lastY = y;
                invalidate();
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                activeHandle = HANDLE_NONE;
                return true;
            default:
                return false;
        }
    }

    private int hitTest(float x, float y) {
        if (near(x, y, cropRect.left, cropRect.top)) return HANDLE_TL;
        if (near(x, y, cropRect.right, cropRect.top)) return HANDLE_TR;
        if (near(x, y, cropRect.left, cropRect.bottom)) return HANDLE_BL;
        if (near(x, y, cropRect.right, cropRect.bottom)) return HANDLE_BR;
        if (cropRect.contains(x, y)) return HANDLE_MOVE;
        return HANDLE_NONE;
    }

    private boolean near(float x, float y, float px, float py) {
        return Math.abs(x - px) <= handleTouchPx && Math.abs(y - py) <= handleTouchPx;
    }

    private void applyDrag(float dx, float dy) {
        switch (activeHandle) {
            case HANDLE_MOVE:
                float mdx = dx, mdy = dy;
                if (cropRect.left + mdx < imageRect.left) mdx = imageRect.left - cropRect.left;
                if (cropRect.right + mdx > imageRect.right) mdx = imageRect.right - cropRect.right;
                if (cropRect.top + mdy < imageRect.top) mdy = imageRect.top - cropRect.top;
                if (cropRect.bottom + mdy > imageRect.bottom) mdy = imageRect.bottom - cropRect.bottom;
                cropRect.offset(mdx, mdy);
                break;
            case HANDLE_TL:
                cropRect.left = clamp(cropRect.left + dx, imageRect.left, cropRect.right - minSizePx);
                cropRect.top = clamp(cropRect.top + dy, imageRect.top, cropRect.bottom - minSizePx);
                break;
            case HANDLE_TR:
                cropRect.right = clamp(cropRect.right + dx, cropRect.left + minSizePx, imageRect.right);
                cropRect.top = clamp(cropRect.top + dy, imageRect.top, cropRect.bottom - minSizePx);
                break;
            case HANDLE_BL:
                cropRect.left = clamp(cropRect.left + dx, imageRect.left, cropRect.right - minSizePx);
                cropRect.bottom = clamp(cropRect.bottom + dy, cropRect.top + minSizePx, imageRect.bottom);
                break;
            case HANDLE_BR:
                cropRect.right = clamp(cropRect.right + dx, cropRect.left + minSizePx, imageRect.right);
                cropRect.bottom = clamp(cropRect.bottom + dy, cropRect.top + minSizePx, imageRect.bottom);
                break;
            default:
                break;
        }
    }

    private float clamp(float v, float lo, float hi) {
        if (hi < lo) hi = lo;
        return Math.max(lo, Math.min(hi, v));
    }

    /** 현재 크롭 박스 영역만 잘라낸 비트맵. 박스가 비정상이면 원본을 그대로 반환. */
    @Nullable
    public Bitmap getCroppedBitmap() {
        if (bitmap == null) return null;
        RectF dst = new RectF();
        invMatrix.mapRect(dst, cropRect); // view -> bitmap 좌표
        int left = Math.max(0, Math.round(dst.left));
        int top = Math.max(0, Math.round(dst.top));
        int right = Math.min(bitmap.getWidth(), Math.round(dst.right));
        int bottom = Math.min(bitmap.getHeight(), Math.round(dst.bottom));
        int w = right - left;
        int h = bottom - top;
        if (w <= 0 || h <= 0) return bitmap;
        return Bitmap.createBitmap(bitmap, left, top, w, h);
    }
}
