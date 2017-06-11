package com.formichelli.dfnightselfies.preference

import android.app.AlertDialog
import android.content.Context
import android.preference.DialogPreference
import android.util.AttributeSet

class SimpleDialogPreference : DialogPreference {
    constructor(context: Context, attrs: AttributeSet, defStyleAttr: Int) : super(context, attrs, defStyleAttr) {}

    constructor(context: Context, attrs: AttributeSet) : super(context, attrs) {}

    override fun onPrepareDialogBuilder(builder: AlertDialog.Builder) {
        super.onPrepareDialogBuilder(builder)
        builder.setNegativeButton(null, null)
    }
}

