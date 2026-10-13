package com.andrerinas.openheadunit.app

import android.content.Context
import android.content.res.Resources
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.andrerinas.openheadunit.R
import com.andrerinas.openheadunit.librehu.LibreHuTheme
import com.andrerinas.openheadunit.utils.LocaleHelper

/**
 * Base for the projection activity. Does NOT extend [BaseActivity] to avoid
 * [BaseActivity.onResume] calling [recreate] on night-mode or theme changes,
 * which would destroy the video surface mid-session.
 */
abstract class SurfaceActivity : AppCompatActivity() {

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.wrapContext(newBase))
    }

    // LibreHU fork: the launcher accent over every theme set on this activity (manifest, setTheme).
    override fun onApplyThemeResource(theme: Resources.Theme, resid: Int, first: Boolean) {
        super.onApplyThemeResource(theme, resid, first)
        LibreHuTheme.applyTo(theme)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }
}
