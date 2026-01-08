package com.lalilu.lmedia.source

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.lalilu.common.ext.md5
import com.lalilu.common.kv.KVItem
import com.lalilu.lmedia.source.subsonic.SubsonicConfig
import com.lalilu.lmedia.source.subsonic.SubsonicSource
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.random.Random


@Composable
fun SubsonicSource.SubsonicSourceContent(
    modifier: Modifier = Modifier,
) {
    SubsonicSourceContent(
        modifier = modifier,
        configItem = configItem
    )
}

@Composable
fun SubsonicSourceContent(
    modifier: Modifier = Modifier,
    configItem: KVItem<SubsonicConfig>
) {
    var username by remember { mutableStateOf(configItem.value.username) }
    var password by remember { mutableStateOf("") }
    var url by remember { mutableStateOf(configItem.value.url) }
    var isSaving by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Card(
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .padding(top = 16.dp, bottom = 12.dp)
                .fillMaxWidth()
        ) {
            Text(
                text = "SubsonicSource",
                style = MaterialTheme.typography.titleMedium
            )

            Text(
                modifier = Modifier
                    .alpha(0.6f)
                    .padding(top = 8.dp),
                text = "connect to Subsonic API or Navidrome",
                style = MaterialTheme.typography.labelSmall,
            )

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = url,
                onValueChange = { url = it },
                label = { Text("Server URL") },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri)
            )

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = username,
                onValueChange = { username = it },
                label = { Text("Username") },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text)
            )

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Password") },
                modifier = Modifier.fillMaxWidth(),
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
            )

            Spacer(modifier = Modifier.height(16.dp))

            Row {
                Button(
                    onClick = {
                        scope.launch {
                            isSaving = true
                            // 生成随机salt
                            val salt = Random.nextBytes(16).toHexString()
                            // 生成token: md5(password + salt)
                            val token = (password + salt).md5()

                            // 更新配置
                            configItem.value = configItem.value.copy(
                                username = username,
                                url = url,
                                salt = salt,
                                token = token
                            )

                            // 清空密码字段
                            password = ""

                            // 保存配置
                            configItem.save()
                            delay(500L)

                            isSaving = false
                        }
                    },
                    enabled = username.isNotBlank() && password.isNotBlank() && url.isNotBlank() && !isSaving,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (isSaving) "Saving..." else "Save Configuration")
                }
            }
        }
    }
}

