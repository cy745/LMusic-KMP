package com.lalilu.lmedia.source

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.ActivityCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lalilu.component.LazyStaggeredGridContent
import com.lalilu.lmedia.component.SourcePipelineCard
import com.lalilu.lmedia.domain.repository.MediaSourceBindingRepository
import com.lalilu.lmedia.domain.repository.SourceStatus
import com.lalilu.lmedia.domain.source.SnapshotState
import com.lalilu.lmedia.source.mediastore.MediaStoreSource
import org.koin.compose.koinInject


fun MediaStoreSource.mediaStoreSourceContent(modifier: Modifier) = LazyStaggeredGridContent {
    val context = LocalContext.current
    val repository = koinInject<MediaSourceBindingRepository>()
    val syncState = state.collectAsStateWithLifecycle()
    val latestSnapshot = snapshot.collectAsStateWithLifecycle()
    val status = repository.observeSource(name)
        .collectAsStateWithLifecycle(initialValue = null)
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
        onResult = { success ->
            granted.value = success
            if (success) configOrNullCompat?.call<Unit>("扫描")
        }
    )

    val extraFunctions = remember {
        listOf<Declaration.Function<*>>(
            Declaration.Function(
                key = "request_permissions",
                name = "授权",
                description = "请求必要权限",
                parameters = emptyList(),
                returnType = Unit::class,
                isAvailable = { syncState.value !is SnapshotState.Loading && !granted.value },
                callback = { launcher.launch(permission) }
            )
        )
    }

    return@LazyStaggeredGridContent {
        item(key = this@mediaStoreSourceContent.name) {
            SourcePipelineCard(
                modifier = modifier,
                status = {
                    status.value ?: SourceStatus(
                        syncState = syncState.value,
                        resultRevision = latestSnapshot.value?.revision,
                        songCount = latestSnapshot.value?.audios?.size ?: 0,
                    )
                },
                snapshot = { latestSnapshot.value },
                extraFunctions = { extraFunctions },
                extraMessage = msg@{ if (granted.value) "请求权限成功" else "请授权访问媒体文件" }
            )
        }
    }
}
