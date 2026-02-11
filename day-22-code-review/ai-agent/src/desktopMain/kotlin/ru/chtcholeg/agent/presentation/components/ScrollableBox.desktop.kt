package ru.chtcholeg.agent.presentation.components

import androidx.compose.foundation.ScrollbarStyle
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Desktop implementation with visible scrollbar.
 */
@Composable
actual fun ScrollableBox(
    listState: LazyListState,
    modifier: Modifier,
    content: @Composable () -> Unit
) {
    Box(modifier = modifier.fillMaxSize()) {
        content()

        // Scrollbar with background for visibility
        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight()
                .width(14.dp)
                .background(Color(0xFF2A2A2A))
        ) {
            VerticalScrollbar(
                modifier = Modifier
                    .fillMaxHeight()
                    .padding(vertical = 8.dp, horizontal = 2.dp),
                adapter = rememberScrollbarAdapter(scrollState = listState),
                style = ScrollbarStyle(
                    minimalHeight = 48.dp,
                    thickness = 10.dp,
                    shape = RoundedCornerShape(5.dp),
                    hoverDurationMillis = 200,
                    unhoverColor = Color(0xFF707070),
                    hoverColor = Color(0xFF6CB6FF)
                )
            )
        }
    }
}
