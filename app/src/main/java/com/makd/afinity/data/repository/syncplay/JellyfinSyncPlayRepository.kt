package com.makd.afinity.data.repository.syncplay

import com.makd.afinity.data.manager.SessionManager
import com.makd.afinity.data.models.syncplay.SyncPlayState
import com.makd.afinity.data.syncplay.SyncPlayGroupEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.jellyfin.sdk.api.client.exception.ApiClientException
import org.jellyfin.sdk.api.operations.SyncPlayApi
import org.jellyfin.sdk.model.DateTime
import org.jellyfin.sdk.model.UUID
import org.jellyfin.sdk.model.api.BufferRequestDto
import org.jellyfin.sdk.model.api.GroupInfoDto
import org.jellyfin.sdk.model.api.JoinGroupRequestDto
import org.jellyfin.sdk.model.api.NewGroupRequestDto
import org.jellyfin.sdk.model.api.PingRequestDto
import org.jellyfin.sdk.model.api.PlayRequestDto
import org.jellyfin.sdk.model.api.ReadyRequestDto
import org.jellyfin.sdk.model.api.SeekRequestDto
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class JellyfinSyncPlayRepository @Inject constructor(private val sessionManager: SessionManager) :
    SyncPlayRepository {

    private val _syncPlayState = MutableStateFlow(SyncPlayState())
    override val syncPlayState: StateFlow<SyncPlayState> = _syncPlayState.asStateFlow()

    private fun syncPlayApi(): SyncPlayApi? {
        val client = sessionManager.getCurrentApiClient() ?: return null
        return SyncPlayApi(client)
    }

    override suspend fun getGroups(): List<GroupInfoDto> =
        withContext(Dispatchers.IO) {
            try {
                syncPlayApi()?.syncPlayGetGroups()?.content ?: emptyList()
            } catch (e: ApiClientException) {
                Timber.e(e, "Failed to get SyncPlay groups")
                emptyList()
            } catch (e: Exception) {
                Timber.e(e, "Unexpected error getting SyncPlay groups")
                emptyList()
            }
        }

    override suspend fun createGroup(name: String): Unit =
        withContext(Dispatchers.IO) {
            try {
                syncPlayApi()?.syncPlayCreateGroup(NewGroupRequestDto(groupName = name))
                Timber.d("SyncPlay: created group '$name'")
            } catch (e: ApiClientException) {
                Timber.e(e, "Failed to create SyncPlay group '$name'")
            } catch (e: Exception) {
                Timber.e(e, "Unexpected error creating SyncPlay group '$name'")
            }
        }

    override suspend fun joinGroup(groupId: UUID): Unit =
        withContext(Dispatchers.IO) {
            try {
                syncPlayApi()?.syncPlayJoinGroup(JoinGroupRequestDto(groupId = groupId))
                Timber.d("SyncPlay: join request sent for group $groupId")
            } catch (e: ApiClientException) {
                Timber.e(e, "Failed to join SyncPlay group $groupId")
            } catch (e: Exception) {
                Timber.e(e, "Unexpected error joining SyncPlay group $groupId")
            }
        }

    override suspend fun leaveGroup(): Unit =
        withContext(Dispatchers.IO) {
            try {
                syncPlayApi()?.syncPlayLeaveGroup()
                _syncPlayState.value = SyncPlayState()
                Timber.d("SyncPlay: left group")
            } catch (e: ApiClientException) {
                Timber.e(e, "Failed to leave SyncPlay group")
            } catch (e: Exception) {
                Timber.e(e, "Unexpected error leaving SyncPlay group")
            }
        }

    override suspend fun pause(): Unit =
        withContext(Dispatchers.IO) {
            try {
                syncPlayApi()?.syncPlayPause()
            } catch (e: ApiClientException) {
                Timber.e(e, "Failed to send SyncPlay pause")
            } catch (e: Exception) {
                Timber.e(e, "Unexpected error sending SyncPlay pause")
            }
        }

    override suspend fun unpause(): Unit =
        withContext(Dispatchers.IO) {
            try {
                syncPlayApi()?.syncPlayUnpause()
            } catch (e: ApiClientException) {
                Timber.e(e, "Failed to send SyncPlay unpause")
            } catch (e: Exception) {
                Timber.e(e, "Unexpected error sending SyncPlay unpause")
            }
        }

    override suspend fun seek(positionTicks: Long): Unit =
        withContext(Dispatchers.IO) {
            try {
                syncPlayApi()?.syncPlaySeek(SeekRequestDto(positionTicks = positionTicks))
            } catch (e: ApiClientException) {
                Timber.e(e, "Failed to send SyncPlay seek to $positionTicks ticks")
            } catch (e: Exception) {
                Timber.e(e, "Unexpected error sending SyncPlay seek")
            }
        }

    override suspend fun stop(): Unit =
        withContext(Dispatchers.IO) {
            try {
                syncPlayApi()?.syncPlayStop()
            } catch (e: ApiClientException) {
                Timber.e(e, "Failed to send SyncPlay stop")
            } catch (e: Exception) {
                Timber.e(e, "Unexpected error sending SyncPlay stop")
            }
        }

    override suspend fun reportBuffering(
        positionTicks: Long,
        isPlaying: Boolean,
        playlistItemId: UUID,
    ): Unit =
        withContext(Dispatchers.IO) {
            try {
                syncPlayApi()
                    ?.syncPlayBuffering(
                        BufferRequestDto(
                            `when` = DateTime.now(),
                            positionTicks = positionTicks,
                            isPlaying = isPlaying,
                            playlistItemId = playlistItemId,
                        )
                    )
            } catch (e: ApiClientException) {
                Timber.e(e, "Failed to report SyncPlay buffering")
            } catch (e: Exception) {
                Timber.e(e, "Unexpected error reporting SyncPlay buffering")
            }
        }

    override suspend fun reportReady(
        positionTicks: Long,
        isPlaying: Boolean,
        playlistItemId: UUID,
    ): Unit =
        withContext(Dispatchers.IO) {
            try {
                syncPlayApi()
                    ?.syncPlayReady(
                        ReadyRequestDto(
                            `when` = DateTime.now(),
                            positionTicks = positionTicks,
                            isPlaying = isPlaying,
                            playlistItemId = playlistItemId,
                        )
                    )
            } catch (e: ApiClientException) {
                Timber.e(e, "Failed to report SyncPlay ready")
            } catch (e: Exception) {
                Timber.e(e, "Unexpected error reporting SyncPlay ready")
            }
        }

    override suspend fun ping(clientTimeMs: Long): Unit =
        withContext(Dispatchers.IO) {
            try {
                syncPlayApi()?.syncPlayPing(PingRequestDto(ping = clientTimeMs))
            } catch (e: ApiClientException) {
                Timber.e(e, "Failed to send SyncPlay ping")
            } catch (e: Exception) {
                Timber.e(e, "Unexpected error sending SyncPlay ping")
            }
        }

    override suspend fun setNewQueue(itemIds: List<UUID>, position: Int, startPositionTicks: Long) =
        withContext(Dispatchers.IO) {
            try {
                syncPlayApi()
                    ?.syncPlaySetNewQueue(
                        PlayRequestDto(
                            playingQueue = itemIds,
                            playingItemPosition = position,
                            startPositionTicks = startPositionTicks,
                        )
                    )
                Timber.d(
                    "SyncPlay: set new queue — ${itemIds.size} item(s) at ${startPositionTicks / 10_000}ms"
                )
            } catch (e: ApiClientException) {
                Timber.e(e, "Failed to set SyncPlay queue")
            } catch (e: Exception) {
                Timber.e(e, "Unexpected error setting SyncPlay queue")
            }
        }

    override fun setGroupJoined(groupId: UUID) {
        _syncPlayState.value = SyncPlayState(isInGroup = true, groupId = groupId)
    }

    override fun updateFromGroupEvent(event: SyncPlayGroupEvent) {
        val current = _syncPlayState.value
        _syncPlayState.value =
            when (event) {
                is SyncPlayGroupEvent.GroupStateRefreshed ->
                    SyncPlayState(
                        isInGroup = true,
                        groupId = event.groupInfo.groupId,
                        groupName = event.groupInfo.groupName,
                        members = event.groupInfo.participants,
                        groupState = event.groupInfo.state,
                    )
                is SyncPlayGroupEvent.GroupLeft -> SyncPlayState()
                is SyncPlayGroupEvent.StateChanged -> current.copy(groupState = event.newState)
                is SyncPlayGroupEvent.UserJoined -> {
                    if (event.userName !in current.members)
                        current.copy(members = current.members + event.userName)
                    else current
                }
                is SyncPlayGroupEvent.UserLeft ->
                    current.copy(members = current.members.filter { it != event.userName })
                is SyncPlayGroupEvent.QueueChanged -> {
                    val u = event.update
                    if (u != null) {
                        current.copy(
                            queue = u.playlist,
                            playingItemIndex = u.playingItemIndex,
                            shuffleMode = u.shuffleMode,
                            repeatMode = u.repeatMode,
                        )
                    } else current
                }
                is SyncPlayGroupEvent.Error -> {
                    Timber.w("SyncPlay group error: ${event.type} — ${event.message}")
                    current
                }
                else -> current
            }
    }
}
