package com.lockin.app

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.fragment.app.FragmentActivity
import com.lockin.app.core.data.payment.RazorpayManager
import com.lockin.app.navigation.LockInNavigation
import com.lockin.app.ui.theme.LockInTheme
import com.lockin.app.core.domain.usecase.SyncUseCase
import com.razorpay.PaymentResultListener
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : FragmentActivity(), PaymentResultListener {

  @Inject
  lateinit var razorpayManager: RazorpayManager

  @Inject
  lateinit var syncUseCase: SyncUseCase

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    enableEdgeToEdge()
    setContent {
      LockInTheme { Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) { LockInNavigation() } }
    }
  }

  override fun onStop() {
    super.onStop()
    runAppClosingSync()
  }

  override fun onDestroy() {
    runAppClosingSync()
    super.onDestroy()
  }

  private fun runAppClosingSync() {
    CoroutineScope(Dispatchers.IO).launch {
      try {
        syncUseCase()
        Timber.d("App closing sync executed successfully.")
      } catch (e: Exception) {
        Timber.e(e, "App closing sync failed.")
      }
    }
  }

  override fun onPaymentSuccess(razorpayPaymentId: String?) {
    razorpayPaymentId?.let { razorpayManager.onPaymentSuccess(it) }
  }

  override fun onPaymentError(code: Int, description: String?) {
    razorpayManager.onPaymentError(code, description ?: "Unknown payment error")
  }
}


