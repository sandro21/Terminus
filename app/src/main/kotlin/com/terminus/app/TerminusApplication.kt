package com.terminus.app

import android.app.Application
import com.terminus.app.data.TransitRepository
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class TerminusApplication : Application() {
    lateinit var repository: TransitRepository
        private set

    override fun onCreate() {
        super.onCreate()
        repository = TransitRepository(this)
        val errors = CoroutineExceptionHandler { _, error -> error.printStackTrace() }
        CoroutineScope(SupervisorJob() + Dispatchers.IO + errors).launch {
            repository.seedIfEmpty()
            runCatching { repository.syncBundle() }
        }
    }
}
