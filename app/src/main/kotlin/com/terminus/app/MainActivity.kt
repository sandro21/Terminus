package com.terminus.app

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import com.terminus.app.ui.AppViewModel
import com.terminus.app.ui.TerminusApp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val repository = (application as TerminusApplication).repository
        setContent { AppContent(AppViewModel.Factory(repository)) }
    }
}

@Composable
private fun AppContent(factory: AppViewModel.Factory) {
    val viewModel: AppViewModel = viewModel(factory = factory)
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    TerminusApp(viewModel) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}
