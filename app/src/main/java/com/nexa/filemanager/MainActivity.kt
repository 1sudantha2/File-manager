package com.nexa.filemanager

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.fragment.app.FragmentActivity
import com.nexa.filemanager.ui.FileManagerApp

class MainActivity : FragmentActivity() {
    private val model: FileViewModel by viewModels()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { FileManagerApp(model) }
    }
    override fun onStop() {
        super.onStop()
        model.lockVault()
    }
    fun protectScreen(protect: Boolean) {
        if (protect) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        else window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
    }
}
