package com.lalilu.lartist.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.MarqueeSpacing
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.lalilu.extensions.SharedContext
import com.lalilu.extensions.SharedMap
import com.lalilu.extensions.buildSharedMap
import com.lalilu.lmedia.entity.LArtist
import com.lalilu.lmedia.entity.LAudio
import com.lalilu.lmedia.entity.ref

@Composable
fun ArtistCard(
    modifier: Modifier = Modifier,
    artist: LArtist,
    sharedMapPrefix: String = "",
    onClick: (SharedMap) -> Unit = {}
) = SharedContext(
    sharedMap = buildSharedMap(
        id = artist.idValue(),
        keys = listOf("TITLE", "SUBTITLE"),
        prefix = sharedMapPrefix
    )
) {
    val title = artist.titleValue()
    val subTitle = artist.subtitleValue()
    val context = LocalPlatformContext.current
    val bgColor = animateColorAsState(
        targetValue = if (false) MaterialTheme.colorScheme.onBackground.copy(0.3f)
        else Color.Transparent, label = ""
    )

    Row(
        modifier = modifier
            .clickable(onClick = { onClick(sharedMap) })
            .drawBehind { drawRect(bgColor.value) }
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .wrapContentHeight()
            .padding(start = 20.dp, end = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.Start
        ) {
            Text(
                modifier = Modifier
                    .sharedBoundsV2("TITLE")
                    .basicMarquee(
                        iterations = Int.MAX_VALUE,
                        spacing = MarqueeSpacing(30.dp)
                    ),
                maxLines = 1,
                text = title,
                fontSize = 14.sp,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.onBackground,
                style = MaterialTheme.typography.bodyMedium,
                overflow = TextOverflow.Ellipsis
            )

            subTitle.takeIf { it.isNotBlank() }.let {
                Text(
                    modifier = Modifier.sharedBoundsV2("SUBTITLE"),
                    maxLines = 1,
                    text = subTitle,
                    fontSize = 10.sp,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onBackground.copy(0.5f),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }

        Box(
            modifier = Modifier.padding(vertical = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                shape = RoundedCornerShape(2.dp),
                shadowElevation = 2.dp
            ) {
                val textColor = MaterialTheme.colorScheme.background

                AsyncImage(
                    modifier = Modifier
                        .height(64.dp)
                        .drawWithContent {
                            drawContent()
                            drawRect(color = textColor, alpha = 0.5f)
                        }
                        .aspectRatio(2f / 1f),
                    model = remember {
                        ImageRequest.Builder(context)
                            .data(artist)
                            .crossfade(true)
                            .build()
                    },
                    contentScale = ContentScale.Crop,
                    contentDescription = "Song Card Image"
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = remember(artist) { "${artist.ref<LAudio>().size} 首歌曲" },
                    maxLines = 1,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onBackground,
                    fontWeight = FontWeight.Bold,
                    overflow = TextOverflow.Ellipsis
                )
//                PlayingTipIcon(isPlaying = isPlaying)
            }
        }
    }
}