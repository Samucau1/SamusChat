package com.samuschat.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.onFocusedBoundsChanged
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController

/** Observe outside taps without consuming button clicks, text selection or scrolling. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Modifier.dismissKeyboardOnOutsideTap(): Modifier {
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    var focusedCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    var containerCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    return this
        .onFocusedBoundsChanged { focusedCoordinates = it }
        .onGloballyPositioned { containerCoordinates = it }
        .pointerInput(focusManager, keyboard) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                val focused = focusedCoordinates
                val container = containerCoordinates
                val outside = focused != null && focused.isAttached && container != null && container.isAttached &&
                    !focused.boundsInRoot().contains(container.localToRoot(down.position))
                val up = waitForUpOrCancellation(pass = PointerEventPass.Initial)
                if (outside && up != null && focused === focusedCoordinates && focused?.isAttached == true &&
                    container?.isAttached == true && !focused.boundsInRoot().contains(container.localToRoot(up.position))) {
                    focusManager.clearFocus()
                    keyboard?.hide()
                }
            }
        }
}
