package com.mara.jordan.app.utils;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.Animatable;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.widget.ProgressBar;

import androidx.appcompat.widget.AppCompatButton;
import androidx.core.graphics.drawable.DrawableCompat;

public class LoadingButton extends AppCompatButton {

    private CharSequence savedText;
    private ColorStateList savedBackgroundTintList;
    private Drawable currentSpinner;

    public LoadingButton(Context context) {
        super(context);
    }

    public LoadingButton(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public LoadingButton(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public void startAnimation() {
        savedText = getText();
        savedBackgroundTintList = getBackgroundTintList();
        setEnabled(false);

        ProgressBar pb = new ProgressBar(getContext());
        currentSpinner = pb.getIndeterminateDrawable().mutate();
        DrawableCompat.setTint(currentSpinner, Color.WHITE);
        int size = (int) getTextSize();
        currentSpinner.setBounds(0, 0, size, size);
        setCompoundDrawables(currentSpinner, null, null, null);
        setCompoundDrawablePadding(size / 4);

        if (currentSpinner instanceof Animatable) {
            ((Animatable) currentSpinner).start();
        }
    }

    public void revertAnimation() {
        stopCurrentSpinner();
        setCompoundDrawables(null, null, null, null);
        setEnabled(true);
        if (savedText != null) {
            setText(savedText);
            savedText = null;
        }
        if (savedBackgroundTintList != null) {
            setBackgroundTintList(savedBackgroundTintList);
            savedBackgroundTintList = null;
        }
    }

    public void doneLoadingAnimation(int fillColor, Bitmap bitmap) {
        stopCurrentSpinner();
        setBackgroundTintList(ColorStateList.valueOf(fillColor));
        BitmapDrawable icon = new BitmapDrawable(getResources(), bitmap);
        setCompoundDrawablesWithIntrinsicBounds(icon, null, null, null);
        setText("");
    }

    private void stopCurrentSpinner() {
        if (currentSpinner instanceof Animatable) {
            ((Animatable) currentSpinner).stop();
        }
        currentSpinner = null;
    }
}
