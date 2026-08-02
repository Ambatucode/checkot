package com.app.checkot.utils

import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

/**
 * Local "is it really you" confirmation for sensitive admin actions
 * (approve/reject). This is a UX confirmation layer only — the real access
 * control is enforced server-side by the Firestore rules (isAdmin()). Using the
 * device biometric/lock instead of the account password means it works no matter
 * how the admin signed in (Google or email/password).
 */
object BiometricAuth {

    /**
     * Which authenticators we accept. BIOMETRIC_STRONG (fingerprint/face) plus the
     * device credential (PIN/pattern/password) as a fallback. The combined pair is
     * only supported on Android 11+ (API 30); below that we accept biometrics only
     * and let the password dialog cover phones without an enrolled fingerprint.
     */
    private fun allowedAuthenticators(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            BiometricManager.Authenticators.BIOMETRIC_STRONG or
                BiometricManager.Authenticators.DEVICE_CREDENTIAL
        } else {
            BiometricManager.Authenticators.BIOMETRIC_STRONG
        }

    /** True if this device can actually run a biometric/device-credential prompt. */
    fun canAuthenticate(context: Context): Boolean =
        BiometricManager.from(context).canAuthenticate(allowedAuthenticators()) ==
            BiometricManager.BIOMETRIC_SUCCESS

    /**
     * Shows the system biometric/device-credential prompt.
     * [onSuccess] fires only on a verified confirmation. [onError] fires for real
     * failures (e.g. lockout) — user-initiated cancels are ignored so we don't nag.
     */
    fun prompt(
        activity: FragmentActivity,
        title: String,
        subtitle: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        val executor = ContextCompat.getMainExecutor(activity)
        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                onSuccess()
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                // Silent on deliberate cancels; surface anything else (lockout, etc.).
                when (errorCode) {
                    BiometricPrompt.ERROR_USER_CANCELED,
                    BiometricPrompt.ERROR_NEGATIVE_BUTTON,
                    BiometricPrompt.ERROR_CANCELED -> Unit
                    else -> onError(errString.toString())
                }
            }
            // onAuthenticationFailed = one wrong finger; not terminal, the prompt stays up.
        }

        val builder = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setAllowedAuthenticators(allowedAuthenticators())
        // A negative button is required when a device credential is NOT allowed,
        // and forbidden when it is. So only add it on the biometric-only path.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            builder.setNegativeButtonText("Cancel")
        }

        BiometricPrompt(activity, executor, callback).authenticate(builder.build())
    }
}

/** Walks the Context wrapper chain to the hosting FragmentActivity, or null. */
fun Context.findFragmentActivity(): FragmentActivity? {
    var ctx: Context = this
    while (ctx is ContextWrapper) {
        if (ctx is FragmentActivity) return ctx
        ctx = ctx.baseContext
    }
    return null
}
