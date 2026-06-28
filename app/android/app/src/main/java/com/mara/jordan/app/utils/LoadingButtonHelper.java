package com.mara.jordan.app.utils;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.util.Log;

import androidx.annotation.DrawableRes;
import androidx.core.content.ContextCompat;

import com.mara.jordan.app.R;

import lombok.Getter;

public class LoadingButtonHelper {

    private static final String TAG = "LoadingBtnHlpr";
    private static LoadingButtonHelper INSTANCE;
    private static final long DELAY_BEFORE_REVERT_ACTION_BUTTON_STATE_MS = 2500;

    @Getter
    private final Context context;

    private LoadingButtonHelper(Context ctx) {
        super();
        context = ctx.getApplicationContext();
    }

    public static LoadingButtonHelper getInstance(Context ctx) {
        if(INSTANCE == null){
            INSTANCE = new LoadingButtonHelper(ctx);
        }
        return INSTANCE;
    }

    public int getProgressionButtonFillColor() {
        return ContextCompat.getColor(getContext(), R.color.red_bull);
    }

    public Bitmap getSuccessBitmap() {
        return drawBitmap(R.drawable.check);
    }

    public Bitmap getErrorBitmap() {
        return drawBitmap(R.drawable.cross);
    }

    private Bitmap drawBitmap(@DrawableRes int resId){
        Drawable drawable = ContextCompat.getDrawable(getContext(), resId);
        try {
            Bitmap bitmap = Bitmap.createBitmap(drawable.getIntrinsicWidth(), drawable.getIntrinsicHeight(), Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmap);
            drawable.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
            drawable.draw(canvas);
            return bitmap;
        } catch (OutOfMemoryError e) {
            Log.e(TAG, "Cannot create bitmap from resource " + resId, e);
            return Bitmap.createBitmap(0,0, Bitmap.Config.ALPHA_8);
        }
    }

    public void waitAndResetButton(LoadingButton button, long delayMs) {
        new Handler().postDelayed(button::revertAnimation, delayMs);
    }

    public void waitAndResetButton(LoadingButton button) {
        waitAndResetButton(button, DELAY_BEFORE_REVERT_ACTION_BUTTON_STATE_MS);
    }

    public void successAndReset(LoadingButton buttonClicked) {
        doneLoadingAnimationAndReset(buttonClicked, getProgressionButtonFillColor(), getSuccessBitmap());
    }

    public void errorAndReset(LoadingButton buttonClicked) {
        doneLoadingAnimationAndReset(buttonClicked, getProgressionButtonFillColor(), getErrorBitmap());
    }

    public void doneLoadingAnimationAndReset(LoadingButton buttonClicked, int fillColor, Bitmap bitmap) {
        buttonClicked.doneLoadingAnimation(fillColor, bitmap);
        waitAndResetButton(buttonClicked);
    }

    public void doneLoadingAnimationAndReset(LoadingButton buttonClicked, int fillColor, Bitmap bitmap, long delayMs) {
        buttonClicked.doneLoadingAnimation(fillColor, bitmap);
        waitAndResetButton(buttonClicked, delayMs);
    }

}
