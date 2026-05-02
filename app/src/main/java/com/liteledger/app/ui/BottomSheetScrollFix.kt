package com.liteledger.app.ui

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.Velocity

class BottomSheetNestedScrollConnection(private val lazyListState: LazyListState) :
        NestedScrollConnection {

    override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
        val isScrollingDown = available.y > 0 // Finger moving down, content scrolling up

        // If scrolling down and we're NOT at the top of the list,
        // don't let the sheet consume ANY of the scroll
        if (isScrollingDown) {
            val isAtTop =
                    lazyListState.firstVisibleItemIndex == 0 &&
                            lazyListState.firstVisibleItemScrollOffset == 0

            if (!isAtTop) {
                // The list can still scroll up, so consume nothing here
                // and let the LazyColumn handle it
                return Offset.Zero
            }
        }

        // For scroll-up (negative y) or when at top, don't interfere
        return Offset.Zero
    }

    override fun onPostScroll(
            consumed: Offset,
            available: Offset,
            source: NestedScrollSource
    ): Offset {
        return Offset.Zero
    }

    override suspend fun onPreFling(available: Velocity): Velocity {
        val isFlingingDown = available.y > 0

        if (isFlingingDown) {
            val isAtTop =
                    lazyListState.firstVisibleItemIndex == 0 &&
                            lazyListState.firstVisibleItemScrollOffset == 0

            if (!isAtTop) {
                // List has content to scroll, don't let sheet consume the fling
                return Velocity.Zero
            }
        }

        return Velocity.Zero
    }

    override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
        return Velocity.Zero
    }
}

@Composable
fun Modifier.bottomSheetScrollFix(lazyListState: LazyListState): Modifier {
    val nestedScrollConnection =
            remember(lazyListState) { BottomSheetNestedScrollConnection(lazyListState) }
    return this.nestedScroll(nestedScrollConnection)
}
