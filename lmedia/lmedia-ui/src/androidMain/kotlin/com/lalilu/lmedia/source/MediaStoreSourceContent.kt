package com.lalilu.lmedia.source

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.ActivityCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lalilu.lmedia.component.SourceCard
import com.lalilu.lmedia.entity.Snapshot
import com.lalilu.lmedia.entity.SnapshotState


@Composable
fun MediaSource.MediaStoreSourceContent(modifier: Modifier) {
    val context = LocalContext.current
    val state = source().collectAsStateWithLifecycle(initialValue = Snapshot.Loading)
    val permission = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
    }
    val granted = remember {
        val result = ActivityCompat.checkSelfPermission(context, permission)
        mutableStateOf(result == PackageManager.PERMISSION_GRANTED)
    }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { success -> granted.value = success }
    )

    val extraFunctions = remember {
        listOf<Declaration.Function<*>>(
            Declaration.Function(
                key = "request_permissions",
                name = "授权",
                description = "请求必要权限",
                parameters = emptyList(),
                returnType = Unit::class,
                isAvailable = { state.value.state is SnapshotState.Idle && !granted.value },
                callback = { launcher.launch(permission) }
            )
        )
    }

    SourceCard(
        modifier = modifier,
        state = { state.value },
        extraFunctions = { extraFunctions },
        extraMessage = msg@{ if (granted.value) "请求权限成功" else "请授权访问媒体文件" }
    )
}
