package com.formichelli.dfnightselfies.preference;

import android.content.Context;
import android.preference.DialogPreference;
import android.util.AttributeSet;

public class SimpleDialogPreference extends DialogPreference {
    public SimpleDialogPreference(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public SimpleDialogPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
    }
}
