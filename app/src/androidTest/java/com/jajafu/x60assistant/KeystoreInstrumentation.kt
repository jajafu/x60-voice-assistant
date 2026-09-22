package com.jajafu.x60assistant

import android.app.Activity
import android.app.Instrumentation
import android.os.Bundle
import com.jajafu.x60assistant.security.AndroidKeystoreSecretStore

/** Device-only round trip; uses a synthetic value and deletes it before finishing. */
class KeystoreInstrumentation : Instrumentation() {
    override fun onCreate(arguments: Bundle?) {
        super.onCreate(arguments)
        start()
    }

    override fun onStart() {
        val results = Bundle()
        val store = AndroidKeystoreSecretStore(targetContext)
        val expected = "sk-device-instrumentation-only-1234".toCharArray()
        try {
            store.deleteApiKey()
            store.saveApiKey(expected.copyOf())
            val actual = checkNotNull(store.readApiKey())
            try {
                check(actual.contentEquals(expected))
                check(store.maskedApiKey() == "••••1234")
                store.deleteApiKey()
                check(store.readApiKey() == null)
                results.putString("keystore_round_trip", "PASS")
            } finally {
                actual.fill('\u0000')
            }
            finish(Activity.RESULT_OK, results)
        } catch (error: Throwable) {
            results.putString("keystore_round_trip", "FAIL:${error.javaClass.simpleName}")
            finish(Activity.RESULT_CANCELED, results)
        } finally {
            expected.fill('\u0000')
            store.deleteApiKey()
        }
    }
}
