package org.jellyfin.androidtv.ui.playback

import android.content.ComponentName
import android.content.Context
import androidx.appcompat.app.AlertDialog
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil3.compose.rememberAsyncImagePainter
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.data.repository.ExternalAppRepository
import org.jellyfin.androidtv.preference.UserPreferences
import org.jellyfin.androidtv.ui.base.JellyfinTheme
import org.jellyfin.androidtv.ui.base.LocalShapes
import org.jellyfin.androidtv.ui.base.Text
import org.jellyfin.androidtv.ui.base.list.ListButton
import org.jellyfin.androidtv.ui.navigation.ActivityDestinations
import org.jellyfin.androidtv.ui.navigation.Destinations
import org.jellyfin.androidtv.ui.navigation.NavigationRepository
import org.jellyfin.androidtv.util.componentName
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.MediaType
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/**
 * Utility class to launch the playback UI for an item.
 */
class PlaybackLauncher(
	private val mediaManager: MediaManager,
	private val videoQueueManager: VideoQueueManager,
	private val navigationRepository: NavigationRepository,
	private val userPreferences: UserPreferences,
	private val externalAppRepository: ExternalAppRepository,
) {
	private val BaseItemDto.supportsExternalPlayer
		get() = when (type) {
			BaseItemKind.MOVIE,
			BaseItemKind.EPISODE,
			BaseItemKind.VIDEO,
			BaseItemKind.SERIES,
			BaseItemKind.SEASON,
			BaseItemKind.RECORDING,
			BaseItemKind.TV_CHANNEL,
			BaseItemKind.PROGRAM,
				-> true

			else -> false
		}

	@JvmOverloads
	fun launch(
		context: Context,
		items: List<BaseItemDto>,
		position: Int? = null,
		replace: Boolean = false,
		itemsPosition: Int = 0,
		shuffle: Boolean = false,
	) {
		val isAudio = items.any { it.mediaType == MediaType.AUDIO }

		if (isAudio) {
			mediaManager.playNow(context, items, itemsPosition, shuffle)
			navigationRepository.navigate(Destinations.nowPlaying)
		} else {
			val items = if (shuffle) items.shuffled() else items

			videoQueueManager.setCurrentVideoQueue(items.toList())
			videoQueueManager.setCurrentMediaPosition(itemsPosition)

			if (items.isEmpty()) return

			fun launchExternalPlayer(componentName: ComponentName?) {
				ExternalPlayerActivity.specifyComponentName(componentName)
				context.startActivity(ActivityDestinations.externalPlayer(context, position?.milliseconds ?: Duration.ZERO))
			}

			fun launchInternalPlayer() {
				if (userPreferences[UserPreferences.playbackRewriteVideoEnabled]) {
					val destination = Destinations.videoPlayerNew(position)
					navigationRepository.navigate(destination, replace)
				} else {
					val destination = Destinations.videoPlayer(position)
					navigationRepository.navigate(destination, replace)
				}
			}

			if (userPreferences[UserPreferences.askPlayer]) {
				// TODO: Currently doesn't check whether items support external players
				val externalPlayerApps = externalAppRepository.getExternalPlayerApps(context);

				var dialog: AlertDialog? = null;
				val builder: AlertDialog.Builder = AlertDialog.Builder(context, R.style.Theme_Jellyfin_Dialog)
				builder.setView(ComposeView(context).apply {
					val packageManager = context.packageManager
					setContent {
						LazyColumn(
							contentPadding = PaddingValues(8.dp, 8.dp),
							modifier = Modifier
								.clip(LocalShapes.current.large)
								.background(JellyfinTheme.colorScheme.surface)
						) {
							item() {
								PlayerSelectButton(
									rememberAsyncImagePainter(R.mipmap.app_icon),
									stringResource(R.string.video_player_internal),
								) {
									launchInternalPlayer()
									dialog?.dismiss()
								}
							}
							items(externalPlayerApps) { app ->
								val icon = remember(app, packageManager) { app.loadIcon(packageManager) }
								val displayName = remember(app, packageManager) { app.loadLabel(packageManager).toString() }

								PlayerSelectButton(
									rememberAsyncImagePainter(icon),
									displayName,
								) {
									launchExternalPlayer(app.activityInfo.componentName)
									dialog?.dismiss()
								}
							}
						}
					}
				})

				dialog = builder.create()
				dialog.show()
				return
			}

			if (userPreferences[UserPreferences.useExternalPlayer] && items.all { it.supportsExternalPlayer }) {
				launchExternalPlayer(null)
			} else {
				launchInternalPlayer()
			}
		}
	}
}

@Composable
fun PlayerSelectButton(
	image: Painter,
	content: String,
	onClick: () -> Unit,
) {
	ListButton(
		leadingContent = {
			Image(
				painter = image,
				contentDescription = null,
				modifier = Modifier
					.size(32.dp)
					.clip(LocalShapes.current.small)
			)
		},
		headingContent = { Text(content) },
		onClick = onClick,
	)
}
