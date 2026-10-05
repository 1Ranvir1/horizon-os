package dev.gtos.launcher.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.launch
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.gtos.domain.AppId
import dev.gtos.domain.LaunchableApp
import dev.gtos.launcher.R

@Composable
fun AppLibrary(
    state: HomeUiState,
    isDefaultHome: Boolean,
    onSearch: (String) -> Unit,
    onChooseHome: () -> Unit,
    onLaunch: (AppId) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    icon: @Composable (LaunchableApp) -> Unit = { AppIconPlaceholder(it.label) },
    onAdd: ((LaunchableApp) -> Unit)? = null,
    onClose: (() -> Unit)? = null,
) {
    val grid = rememberLazyGridState()
    val scope = rememberCoroutineScope()
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    // Keep IME edits synchronous. Background search results must never overwrite typing.
    val editor = rememberTextFieldState(initialText = state.query)
    val currentOnSearch by rememberUpdatedState(onSearch)
    var observedReset by rememberSaveable { mutableIntStateOf(state.homeGeneration) }
    LaunchedEffect(editor) {
        snapshotFlow { editor.text.toString() }.collect { currentOnSearch(it) }
    }
    LaunchedEffect(state.homeGeneration) {
        if (observedReset != state.homeGeneration) {
            editor.clearText()
            observedReset = state.homeGeneration
        }
        focus.clearFocus()
        keyboard?.hide()
        grid.scrollToItem(0)
    }
    LaunchedEffect(state.query) { grid.scrollToItem(0) }

    Column(modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        Row(
            Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 16.dp).pointerInput(onClose) {
                var distance = 0f
                detectVerticalDragGestures(onDragStart = { distance = 0f },
                    onVerticalDrag = { change, delta -> change.consume(); distance += delta },
                    onDragEnd = { if (distance < -40.dp.toPx()) onClose?.invoke() })
            },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.width(3.dp).height(40.dp).background(MaterialTheme.colorScheme.primary))
            Spacer(Modifier.width(12.dp))
            Column {
                Text("Apps", fontSize = 30.sp, fontWeight = FontWeight.Medium)
            }
            Spacer(Modifier.weight(1f))
            if (onClose != null) TextButton(onClick = onClose) { Text("Home") }
        }
        OutlinedTextField(
            state = editor,
            label = { Text(stringResource(R.string.search_apps)) },
            modifier = Modifier.fillMaxWidth(),
            lineLimits = TextFieldLineLimits.SingleLine,
            inputTransformation = InputTransformation { if (length > 256) revertAllChanges() },
            shape = RoundedCornerShape(16.dp),
            trailingIcon = if (editor.text.isNotEmpty()) {
                { TextButton(onClick = { editor.clearText() }) { Text(stringResource(R.string.clear_search)) } }
            } else null,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            onKeyboardAction = { keyboard?.hide(); focus.clearFocus() },
        )
        Row(Modifier.weight(1f)) {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(88.dp), state = grid,
            modifier = Modifier.weight(1f).fillMaxSize(),
            contentPadding = PaddingValues(top = 20.dp, bottom = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item(key = "home-role", span = { GridItemSpan(maxLineSpan) }) {
                if (!isDefaultHome) {
                    Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surface) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(stringResource(R.string.choose_home), style = MaterialTheme.typography.titleMedium)
                            Text(
                                stringResource(R.string.choose_home_hint),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Button(onClick = onChooseHome) { Text(stringResource(R.string.set_default)) }
                        }
                    }
                }
            }
            item(key = "catalog-heading", span = { GridItemSpan(maxLineSpan) }) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(stringResource(R.string.all_apps), style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(pluralStringResource(R.plurals.app_count, state.apps.size, state.apps.size), style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (state.loading) {
                item(key = "loading", span = { GridItemSpan(maxLineSpan) }) {
                    Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(Modifier.size(24.dp))
                        Text(stringResource(R.string.loading_apps), Modifier.padding(top = 12.dp))
                    }
                }
            }
            if (state.loadFailed) {
                item(key = "error", span = { GridItemSpan(maxLineSpan) }) {
                    Column {
                        Text(stringResource(R.string.load_error), color = MaterialTheme.colorScheme.error)
                        TextButton(onClick = onRetry) { Text(stringResource(R.string.retry)) }
                    }
                }
            }
            if (!state.loading && !state.loadFailed && state.apps.isEmpty()) {
                item(key = "empty", span = { GridItemSpan(maxLineSpan) }) {
                    Column(Modifier.padding(vertical = 24.dp)) {
                        Text(stringResource(if (state.query.isEmpty()) R.string.empty_catalog else R.string.no_results))
                        if (state.query.isNotEmpty()) Text(
                            stringResource(R.string.no_results_hint), Modifier.padding(top = 8.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            items(state.apps, key = { it.id.stableKey }, contentType = { "app" }) { app ->
                val description = stringResource(R.string.open_app, app.label)
                Column(
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                        .combinedClickable(role = Role.Button, onClickLabel = description,
                            onLongClickLabel = "Add to Home", onLongClick = onAdd?.let { { it(app) } },
                            onClick = { onLaunch(app.id) })
                        .semantics(mergeDescendants = true) { contentDescription = app.label }
                        .padding(horizontal = 4.dp, vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    icon(app)
                    Text(app.label, fontSize = 13.sp, lineHeight = 17.sp, maxLines = 2,
                        overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
                }
            }
        }
        Column(Modifier.width(28.dp).padding(top = 12.dp, bottom = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            for (letter in "#ABCDEFGHIJKLMNOPQRSTUVWXYZ") {
                Text(letter.toString(), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f).fillMaxWidth().clickable(role = Role.Button,
                        onClickLabel = "Jump to $letter") {
                        val index = state.apps.indexOfFirst {
                            val first = it.label.firstOrNull()?.uppercaseChar()
                            if (letter == '#') first !in 'A'..'Z' else first == letter
                        }
                        if (index >= 0) scope.launch { grid.scrollToItem(index + 2) }
                    }, textAlign = TextAlign.Center)
            }
        }
        }
    }
}

@Composable
fun AppIconPlaceholder(label: String) {
    Box(
        Modifier.size(48.dp).clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        Text(label.take(1).uppercase(), fontSize = 24.sp, color = MaterialTheme.colorScheme.primary)
    }
}
