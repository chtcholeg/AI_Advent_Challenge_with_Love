package ru.chtcholeg.app.presentation.session

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import ru.chtcholeg.app.domain.model.ChatSession
import ru.chtcholeg.app.presentation.components.PlatformLazyVerticalScrollbar
import ru.chtcholeg.app.presentation.theme.chatColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionListScreen(
    store: SessionListStore,
    onNavigateBack: () -> Unit,
    onSessionSelected: (String) -> Unit,
    onNewChat: () -> Unit,
    modifier: Modifier = Modifier
) {
    val state by store.state.collectAsState()
    val colors = chatColors()

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("Chat History") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                actions = {
                    IconButton(onClick = onNewChat) {
                        Icon(Icons.Default.Add, "New Chat")
                    }
                    IconButton(onClick = { store.dispatch(SessionListIntent.ToggleShowArchived) }) {
                        Icon(
                            if (state.showArchived) Icons.Default.Check
                            else Icons.Default.Info,
                            "Toggle Archived"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = colors.headerBackground,
                    titleContentColor = colors.headerText
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onNewChat,
                containerColor = colors.primaryAccent
            ) {
                Icon(Icons.Default.Add, "New Chat")
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Search bar
            OutlinedTextField(
                value = state.searchQuery,
                onValueChange = { store.dispatch(SessionListIntent.Search(it)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                placeholder = { Text("Search chats...") },
                leadingIcon = { Icon(Icons.Default.Search, "Search") },
                trailingIcon = {
                    if (state.searchQuery.isNotEmpty()) {
                        IconButton(onClick = { store.dispatch(SessionListIntent.ClearSearch) }) {
                            Icon(Icons.Default.Clear, "Clear")
                        }
                    }
                },
                singleLine = true
            )

            // Loading indicator
            if (state.isLoading || state.isSearching) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth(),
                    color = colors.primaryAccent
                )
            }

            // Sessions list
            val sessionsToShow = when {
                state.searchQuery.isNotEmpty() -> state.searchResults
                state.showArchived -> state.archivedSessions
                else -> state.activeSessions
            }

            if (sessionsToShow.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = when {
                            state.searchQuery.isNotEmpty() -> "No results found"
                            state.showArchived -> "No archived chats"
                            else -> "No chats yet. Start a new conversation!"
                        },
                        style = MaterialTheme.typography.bodyLarge,
                        color = colors.headerText.copy(alpha = 0.6f)
                    )
                }
            } else {
                val listState = rememberLazyListState()

                Box(
                    modifier = Modifier.fillMaxSize()
                ) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(end = 12.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp)
                    ) {
                        items(sessionsToShow, key = { it.id }) { session ->
                            SessionItem(
                                session = session,
                                onClick = { onSessionSelected(session.id) },
                                onArchive = {
                                    store.dispatch(SessionListIntent.ArchiveSession(session.id))
                                },
                                onUnarchive = {
                                    store.dispatch(SessionListIntent.UnarchiveSession(session.id))
                                },
                                onDelete = {
                                    store.dispatch(SessionListIntent.DeleteSession(session.id))
                                }
                            )
                        }
                    }

                    PlatformLazyVerticalScrollbar(
                        listState = listState,
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .fillMaxHeight()
                    )
                }
            }
        }
    }
}

@Composable
private fun SessionItem(
    session: ChatSession,
    onClick: () -> Unit,
    onArchive: () -> Unit,
    onUnarchive: () -> Unit,
    onDelete: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }
    val colors = chatColors()

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = colors.surface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = session.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = session.modelName,
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.primaryAccent
                    )
                    Text(
                        text = "${session.messageCount} messages",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                session.lastMessage?.let { lastMsg ->
                    Text(
                        text = lastMsg.content.take(100),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = formatTimestamp(session.updatedAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Box {
                IconButton(onClick = { showMenu = true }) {
                    Icon(Icons.Default.MoreVert, "More options")
                }

                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false }
                ) {
                    if (session.isArchived) {
                        DropdownMenuItem(
                            text = { Text("Unarchive") },
                            onClick = {
                                showMenu = false
                                onUnarchive()
                            },
                            leadingIcon = { Icon(Icons.Default.KeyboardArrowUp, null) }
                        )
                    } else {
                        DropdownMenuItem(
                            text = { Text("Archive") },
                            onClick = {
                                showMenu = false
                                onArchive()
                            },
                            leadingIcon = { Icon(Icons.Default.KeyboardArrowDown, null) }
                        )
                    }

                    DropdownMenuItem(
                        text = { Text("Delete") },
                        onClick = {
                            showMenu = false
                            onDelete()
                        },
                        leadingIcon = {
                            Icon(
                                Icons.Default.Delete,
                                null,
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    )
                }
            }
        }
    }
}

private fun formatTimestamp(timestamp: Long): String {
    val instant = Instant.fromEpochMilliseconds(timestamp)
    val localDateTime = instant.toLocalDateTime(TimeZone.currentSystemDefault())

    return "${localDateTime.dayOfMonth.toString().padStart(2, '0')}/" +
            "${localDateTime.monthNumber.toString().padStart(2, '0')}/" +
            "${localDateTime.year} " +
            "${localDateTime.hour.toString().padStart(2, '0')}:" +
            "${localDateTime.minute.toString().padStart(2, '0')}"
}
