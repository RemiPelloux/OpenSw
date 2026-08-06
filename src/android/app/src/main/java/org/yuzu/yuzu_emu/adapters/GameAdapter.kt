// SPDX-FileCopyrightText: Copyright 2025 Eden Emulator Project
// SPDX-License-Identifier: GPL-3.0-or-later

package org.yuzu.yuzu_emu.adapters

import android.content.DialogInterface
import android.text.Html
import android.provider.Settings.Global
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.navigation.findNavController
import androidx.preference.PreferenceManager
import androidx.viewbinding.ViewBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.yuzu.yuzu_emu.HomeNavigationDirections
import org.yuzu.yuzu_emu.R
import org.yuzu.yuzu_emu.YuzuApplication
import org.yuzu.yuzu_emu.databinding.CardGameListBinding
import org.yuzu.yuzu_emu.databinding.CardGameGridBinding
import org.yuzu.yuzu_emu.databinding.CardGameCarouselBinding
import org.yuzu.yuzu_emu.model.Game
import org.yuzu.yuzu_emu.model.GamesViewModel
import org.yuzu.yuzu_emu.utils.GameIconUtils
import org.yuzu.yuzu_emu.utils.ViewUtils.marquee
import org.yuzu.yuzu_emu.viewholder.AbstractViewHolder
import androidx.core.net.toUri
import androidx.core.content.edit
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import org.yuzu.yuzu_emu.NativeLibrary
import org.yuzu.yuzu_emu.databinding.CardGameGridCompactBinding

class GameAdapter(
    private val activity: AppCompatActivity,
    private val onGameFocused: (Game?) -> Unit = {}
) :
    AbstractDiffAdapter<Game, GameAdapter.GameViewHolder>(exact = false) {

    companion object {
        const val VIEW_TYPE_GRID = 0
        const val VIEW_TYPE_GRID_COMPACT = 1
        const val VIEW_TYPE_LIST = 2
        const val VIEW_TYPE_CAROUSEL = 3
        private const val CARD_SIZE_PAYLOAD = "card-size"
    }

    private var viewType = 0

    fun setViewType(type: Int) {
        viewType = type
    }

    public var cardSize: Int = 0
        private set

    fun setCardSize(size: Int) {
        if (cardSize != size && size > 0) {
            cardSize = size
            notifyItemRangeChanged(0, itemCount, CARD_SIZE_PAYLOAD)
        }
    }

    override fun getItemViewType(position: Int): Int = viewType

    override fun onBindViewHolder(holder: GameViewHolder, position: Int) {
        super.onBindViewHolder(holder, position)
        when (getItemViewType(position)) {
            VIEW_TYPE_LIST -> {
                val listBinding = holder.binding as CardGameListBinding
                listBinding.cardGameList.scaleX = 1f
                listBinding.cardGameList.scaleY = 1f
                listBinding.cardGameList.alpha = 1f
                // Reset layout params to XML defaults
                listBinding.root.layoutParams.width = ViewGroup.LayoutParams.MATCH_PARENT
                listBinding.root.layoutParams.height = ViewGroup.LayoutParams.WRAP_CONTENT
            }

            VIEW_TYPE_GRID -> {
                val gridBinding = holder.binding as CardGameGridBinding
                gridBinding.cardGameGrid.scaleX = 1f
                gridBinding.cardGameGrid.scaleY = 1f
                gridBinding.cardGameGrid.alpha = 1f
                // Reset layout params to XML defaults
                gridBinding.root.layoutParams.width = ViewGroup.LayoutParams.MATCH_PARENT
                gridBinding.root.layoutParams.height = ViewGroup.LayoutParams.WRAP_CONTENT
            }

            VIEW_TYPE_GRID_COMPACT -> {
                val gridCompactBinding = holder.binding as CardGameGridCompactBinding
                gridCompactBinding.cardGameGridCompact.scaleX = 1f
                gridCompactBinding.cardGameGridCompact.scaleY = 1f
                gridCompactBinding.cardGameGridCompact.alpha = 1f
                // Reset layout params to XML defaults (same as normal grid)
                gridCompactBinding.root.layoutParams.width = ViewGroup.LayoutParams.MATCH_PARENT
                gridCompactBinding.root.layoutParams.height = ViewGroup.LayoutParams.WRAP_CONTENT
            }

            VIEW_TYPE_CAROUSEL -> {
                val carouselBinding = holder.binding as CardGameCarouselBinding
                // soothens transient flickering
                carouselBinding.cardGameCarousel.scaleY = 0f
                carouselBinding.cardGameCarousel.alpha = 0f
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): GameViewHolder {
        val binding = when (viewType) {
            VIEW_TYPE_LIST -> CardGameListBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            )

            VIEW_TYPE_GRID -> CardGameGridBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            )

            VIEW_TYPE_GRID_COMPACT -> CardGameGridCompactBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            )

            VIEW_TYPE_CAROUSEL -> CardGameCarouselBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            )

            else -> throw IllegalArgumentException("Invalid view type")
        }
        return GameViewHolder(binding, viewType)
    }

    override fun onViewRecycled(holder: GameViewHolder) {
        holder.recycle()
        super.onViewRecycled(holder)
    }

    private fun bindInteraction(card: View, model: Game) {
        card.setOnClickListener { launchGame(model, card) }
        card.setOnLongClickListener {
            val action = HomeNavigationDirections.actionGlobalPerGamePropertiesFragment(model)
            card.findNavController().navigate(action)
            true
        }
        card.setOnFocusChangeListener { view, focused ->
            if (focused) onGameFocused(model)
            val animationsEnabled = Global.getFloat(
                view.context.contentResolver,
                Global.ANIMATOR_DURATION_SCALE,
                1f
            ) != 0f
            view.animate().cancel()
            if (animationsEnabled) {
                view.animate().translationZ(if (focused) 8f else 0f).setDuration(140).start()
            } else {
                view.translationZ = if (focused) 8f else 0f
            }
        }
    }

    inner class GameViewHolder(
        internal val binding: ViewBinding,
        private val viewType: Int
    ) : AbstractViewHolder<Game>(binding) {

        private var iconRequest: coil.request.Disposable? = null

        override fun bind(model: Game) {
            when (viewType) {
                VIEW_TYPE_LIST -> bindListView(model)
                VIEW_TYPE_GRID -> bindGridView(model)
                VIEW_TYPE_CAROUSEL -> bindCarouselView(model)
                VIEW_TYPE_GRID_COMPACT -> bindGridCompactView(model)
            }
        }

        private fun bindListView(model: Game) {
            val listBinding = binding as CardGameListBinding

            listBinding.imageGameScreen.scaleType = ImageView.ScaleType.CENTER_CROP
            iconRequest?.dispose()
            iconRequest = GameIconUtils.loadGameIcon(model, listBinding.imageGameScreen)

            listBinding.textGameTitle.text = model.title.replace("[\\t\\n\\r]+".toRegex(), " ")
            listBinding.textGameDeveloper.text = model.developer

            listBinding.textGameTitle.marquee()
            bindInteraction(listBinding.cardGameList, model)

            // Reset layout params to XML defaults
            listBinding.root.layoutParams.width = ViewGroup.LayoutParams.MATCH_PARENT
            listBinding.root.layoutParams.height = ViewGroup.LayoutParams.WRAP_CONTENT
        }

        private fun bindGridView(model: Game) {
            val gridBinding = binding as CardGameGridBinding

            gridBinding.imageGameScreen.scaleType = ImageView.ScaleType.CENTER_CROP
            iconRequest?.dispose()
            iconRequest = GameIconUtils.loadGameIcon(model, gridBinding.imageGameScreen)

            gridBinding.textGameTitle.text = model.title.replace("[\\t\\n\\r]+".toRegex(), " ")

            bindInteraction(gridBinding.cardGameGrid, model)

            // Reset layout params to XML defaults
            gridBinding.root.layoutParams.width = ViewGroup.LayoutParams.MATCH_PARENT
            gridBinding.root.layoutParams.height = ViewGroup.LayoutParams.WRAP_CONTENT
        }

        private fun bindGridCompactView(model: Game) {
            val gridCompactBinding = binding as CardGameGridCompactBinding

            gridCompactBinding.imageGameScreenCompact.scaleType = ImageView.ScaleType.CENTER_CROP
            iconRequest?.dispose()
            iconRequest = GameIconUtils.loadGameIcon(
                model,
                gridCompactBinding.imageGameScreenCompact
            )

            gridCompactBinding.textGameTitleCompact.text = model.title.replace(
                "[\\t\\n\\r]+".toRegex(),
                " "
            )

            bindInteraction(gridCompactBinding.cardGameGridCompact, model)

            // Reset layout params to XML defaults (same as normal grid)
            gridCompactBinding.root.layoutParams.width = ViewGroup.LayoutParams.MATCH_PARENT
            gridCompactBinding.root.layoutParams.height = ViewGroup.LayoutParams.WRAP_CONTENT
        }

        private fun bindCarouselView(model: Game) {
            val carouselBinding = binding as CardGameCarouselBinding

            carouselBinding.imageGameScreen.scaleType = ImageView.ScaleType.CENTER_CROP
            iconRequest?.dispose()
            iconRequest = GameIconUtils.loadGameIcon(model, carouselBinding.imageGameScreen)

            carouselBinding.textGameTitle.text = model.title.replace("[\\t\\n\\r]+".toRegex(), " ")
            bindInteraction(carouselBinding.cardGameCarousel, model)

            carouselBinding.imageGameScreen.contentDescription =
                binding.root.context.getString(R.string.game_image_desc, model.title)

            // Ensure zero-heighted-full-width cards for carousel
            carouselBinding.root.layoutParams.width = cardSize
        }

        fun recycle() {
            iconRequest?.dispose()
            iconRequest = null
        }
    }

    fun launchGame(game: Game, anchor: View) {
        val gameExists = DocumentFile.fromSingleUri(
            YuzuApplication.appContext,
            game.path.toUri()
        )?.exists() == true
        if (!gameExists) {
            Toast.makeText(
                YuzuApplication.appContext,
                R.string.loader_error_file_not_found,
                Toast.LENGTH_LONG
            ).show()
            ViewModelProvider(activity)[GamesViewModel::class.java].reloadGames(true)
            return
        }

        val launch = {
            PreferenceManager.getDefaultSharedPreferences(YuzuApplication.appContext).edit {
                putLong(game.keyLastPlayedTime, System.currentTimeMillis())
            }
            activity.lifecycleScope.launch {
                withContext(Dispatchers.IO) {
                    val shortcut = ShortcutInfoCompat.Builder(YuzuApplication.appContext, game.path)
                        .setShortLabel(game.title)
                        .setIcon(GameIconUtils.getShortcutIcon(activity, game))
                        .setIntent(game.launchIntent)
                        .build()
                    ShortcutManagerCompat.pushDynamicShortcut(YuzuApplication.appContext, shortcut)
                }
            }
            anchor.findNavController().navigate(
                HomeNavigationDirections.actionGlobalEmulationActivity(game, true)
            )
        }

        if (NativeLibrary.gameRequiresFirmware(game.programId) && !NativeLibrary.isFirmwareAvailable()) {
            MaterialAlertDialogBuilder(activity)
                .setTitle(R.string.loader_requires_firmware)
                .setMessage(
                    Html.fromHtml(
                        activity.getString(R.string.loader_requires_firmware_description),
                        Html.FROM_HTML_MODE_LEGACY
                    )
                )
                .setPositiveButton(android.R.string.ok) { _: DialogInterface?, _: Int -> launch() }
                .setNegativeButton(android.R.string.cancel) { _, _ -> }
                .show()
        } else {
            launch()
        }
    }
}
