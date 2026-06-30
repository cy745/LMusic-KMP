package com.lalilu

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lalilu.extensions.koinInjectOrNull
import org.koin.core.qualifier.named


/**
 * 需要注意Koin注入的时候不能定义默认值
 */
fun interface SlotContent {
    @Composable
    fun SlotParamContext.Content(modifier: Modifier)

    @Composable
    fun ApplyContent(modifier: Modifier, parameters: SlotParamContext.Builder.() -> Unit = {}) {
        val paramContext = remember { slotParams {}.apply(parameters).build() }
        paramContext.Content(modifier)
    }
}

@Composable
fun Slot(
    modifier: Modifier = Modifier,
    key: String,
    elseContent: @Composable (modifier: Modifier) -> Unit = { UnlinkSlot(modifier = it, key = key) },
    parameters: SlotParamContext.Builder.() -> Unit = { }
) {
    val content = slotContent(key)

    if (content == null) {
        elseContent(modifier)
        return
    }

    content.ApplyContent(modifier, parameters)
}

/**
 * 需确保函数的调用顺序和参数顺序一致
 */
class SlotParamContext internal constructor(
    val stateMap: SnapshotStateMap<String, Any>
) {
    inline fun <reified T> param(key: String): T? = stateMap[key] as? T
    inline fun <reified T> param(key: String, elseValue: T): T = param(key) ?: elseValue


    class Builder internal constructor() {
        private val stateMap = mutableStateMapOf<String, Any>()
        internal fun build(): SlotParamContext = SlotParamContext(stateMap)

        fun <T : Any> value(key: String, value: T) = run { stateMap[key] = value }
        infix fun String.reg(value: Any) = value(this, value)
        infix fun <T> String.composableT(content: @Composable () -> T) = run { stateMap[this] = content }
        infix fun String.composable(content: @Composable () -> Unit) = run { stateMap[this] = content }
    }
}

@Composable
fun slotContent(key: String): SlotContent? {
    return koinInjectOrNull<SlotContent?>(qualifier = named(key))
}

/**
 * 构建自定义的参数注入
 */
@Stable
fun slotParams(block: SlotParamContext.Builder.() -> Unit): SlotParamContext.Builder =
    SlotParamContext.Builder().apply(block)

@Composable
private fun UnlinkSlot(
    modifier: Modifier = Modifier,
    key: String
) {
    Box(
        modifier = modifier
    ) {
        Card(
            modifier = Modifier.padding(16.dp),
            shape = RoundedCornerShape(8.dp),
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    modifier = Modifier,
                    text = "Unsupported content, Please upgrade to latest version.",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    modifier = Modifier,
                    text = "UnlinkSlot: $key",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onBackground.copy(0.7f)
                )
            }
        }
    }
}