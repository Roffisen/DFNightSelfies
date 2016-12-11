package com.formichelli.dfnightselfies;

import android.app.Activity;
import android.content.Context;
import android.view.Window;
import android.view.WindowManager;

public class WindowUtil {
    public static void setupWindow(Activity activity)
    {
        Window w = activity.getWindow();

        // hide title
        activity.requestWindowFeature(Window.FEATURE_NO_TITLE);

        // hide statusbar if not on lollipop
        if (android.os.Build.VERSION.SDK_INT < 21)
            w.setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);

        // keep screen on
        w.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        // set brightness to maximum
        WindowManager.LayoutParams windowAttributes = w.getAttributes();
        windowAttributes.screenBrightness = 1;
        w.setAttributes(windowAttributes);
    }
}
