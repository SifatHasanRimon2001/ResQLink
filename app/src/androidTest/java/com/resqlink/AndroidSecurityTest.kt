package com.resqlink

import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.security.NetworkSecurityPolicy
import android.view.MotionEvent
import android.view.WindowManager
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.resqlink.domain.repository.LocationResult
import com.resqlink.location.AndroidLocationRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AndroidSecurityTest {
    @Test fun installedAppHasNoNetworkPermissionOrBackupAccess() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertEquals(PackageManager.PERMISSION_DENIED, context.checkSelfPermission(android.Manifest.permission.INTERNET))
        assertEquals(0, context.applicationInfo.flags and ApplicationInfo.FLAG_ALLOW_BACKUP)
        assertFalse(NetworkSecurityPolicy.getInstance().isCleartextTrafficPermitted)
    }

    @Suppress("DEPRECATION")
    @Test fun installedComponentsHaveNoUnprotectedNonLauncherEntryPoints() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val info = context.packageManager.getPackageInfo(context.packageName,
            PackageManager.GET_ACTIVITIES or PackageManager.GET_SERVICES or PackageManager.GET_RECEIVERS or PackageManager.GET_PROVIDERS)
        val allowedDebugActivities = setOf(MainActivity::class.java.name,
            "androidx.activity.ComponentActivity", "androidx.compose.ui.tooling.PreviewActivity")
        assertTrue(info.activities.orEmpty().filter { it.exported }.all { it.name in allowedDebugActivities })
        assertTrue(info.services.orEmpty().none { it.exported })
        assertTrue(info.providers.orEmpty().none { it.exported })
        info.receivers.orEmpty().filter { it.exported }.forEach {
            // AndroidX's profile installer is protected by the platform signature-level DUMP permission.
            assertEquals("android.permission.DUMP", it.permission)
        }
    }

    @Test fun activityProtectsPrivateScreensAcrossRecreation() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity {
                assertTrue(it.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE != 0)
                assertTrue(it.window.decorView.filterTouchesWhenObscured)
            }
            scenario.recreate()
            scenario.onActivity {
                assertTrue(it.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE != 0)
            }
        }
    }

    @Test fun obscuredTouchesAreRejected() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val properties = arrayOf(MotionEvent.PointerProperties().apply { id = 0; toolType = MotionEvent.TOOL_TYPE_FINGER })
                val coordinates = arrayOf(MotionEvent.PointerCoords().apply { x = 100f; y = 100f; pressure = 1f; size = 1f })
                val event = MotionEvent.obtain(1L, 1L, MotionEvent.ACTION_DOWN, 1, properties, coordinates,
                    0, 0, 1f, 1f, 0, 0, android.view.InputDevice.SOURCE_TOUCHSCREEN, MotionEvent.FLAG_WINDOW_IS_OBSCURED)
                try { assertFalse(activity.dispatchTouchEvent(event)) } finally { event.recycle() }
            }
        }
    }

    @Test fun deniedLocationReturnsExplicitResultWithoutPlatformAccess() = runBlocking {
        val context = object : ContextWrapper(ApplicationProvider.getApplicationContext<Context>()) {
            override fun checkPermission(permission: String, pid: Int, uid: Int) = PackageManager.PERMISSION_DENIED
        }
        assertEquals(LocationResult.PermissionDenied, AndroidLocationRepository(context).currentLocation())
    }
}
