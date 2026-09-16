package com.terminus.app.push

import com.google.firebase.messaging.FirebaseMessagingService
import com.terminus.app.data.TransitRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class TerminusMessagingService : FirebaseMessagingService() {
    override fun onNewToken(token: String) {
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            runCatching { TransitRepository(applicationContext).registerRotatedToken(token) }
        }
    }
}
