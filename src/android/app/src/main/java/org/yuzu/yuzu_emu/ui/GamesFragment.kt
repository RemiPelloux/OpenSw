// SPDX-FileCopyrightText: Copyright 2026 Eden Emulator Project
// SPDX-License-Identifier: GPL-3.0-or-later

package org.yuzu.yuzu_emu.ui

import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.PopupMenu
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.core.widget.doOnTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import org.yuzu.yuzu_emu.HomeNavigationDirections
import org.yuzu.yuzu_emu.NativeLibrary
import org.yuzu.yuzu_emu.R
import org.yuzu.yuzu_emu.YuzuApplication
import org.yuzu.yuzu_emu.adapters.GameAdapter
import org.yuzu.yuzu_emu.databinding.FragmentGamesBinding
import org.yuzu.yuzu_emu.features.settings.model.BooleanSetting
import org.yuzu.yuzu_emu.model.AppletInfo
import org.yuzu.yuzu_emu.model.Game
import org.yuzu.yuzu_emu.model.GamesViewModel
import org.yuzu.yuzu_emu.model.HomeViewModel
import org.yuzu.yuzu_emu.ui.main.MainActivity
import org.yuzu.yuzu_emu.utils.ViewUtils.setVisible
import org.yuzu.yuzu_emu.utils.GameIconUtils
import org.yuzu.yuzu_emu.utils.collect
import info.debatty.java.stringsimilarity.Jaccard
import info.debatty.java.stringsimilarity.JaroWinkler
import java.util.Locale
import androidx.core.content.edit
import androidx.core.content.ContextCompat
import androidx.core.view.doOnNextLayout
import coil.request.Disposable
import org.yuzu.yuzu_emu.features.library.GameLibraryOrganizer
import org.yuzu.yuzu_emu.features.library.LibraryEntry
import org.yuzu.yuzu_emu.features.library.LibrarySort

class GamesFragment : Fragment() {
    private var _binding: FragmentGamesBinding? = null
    private val binding get() = _binding!!

    private var originalHeaderTopMargin: Int? = null
    private var originalHeaderBottomMargin: Int? = null
    private var originalHeaderRightMargin: Int? = null
    private var originalHeaderLeftMargin: Int? = null

    private var lastViewType: Int = GameAdapter.VIEW_TYPE_GRID
    private var fallbackBottomInset: Int = 0

    companion object {
        private const val SEARCH_TEXT = "SearchText"
        private const val SEARCH_OPEN = "SearchOpen"
        private const val PREF_SORT_TYPE = "GamesSortType"
        private const val PREF_FAVORITE_PATHS = "OpenSwFavoriteGamePaths"
        private const val RECENT_WINDOW_MS = 24L * 60L * 60L * 1000L
        private val TITLE_WHITESPACE = Regex("[\\t\\n\\r]+")
    }

    private val gamesViewModel: GamesViewModel by activityViewModels()
    private val homeViewModel: HomeViewModel by activityViewModels()
    private lateinit var gameAdapter: GameAdapter

    private val preferences =
        PreferenceManager.getDefaultSharedPreferences(YuzuApplication.appContext)

    private lateinit var mainActivity: MainActivity
    private val getGamesDirectory =
        registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { result ->
            if (result != null) {
                mainActivity.processGamesDir(result, true)
            }
        }

    private fun getCurrentViewType(): Int {
        val isLandscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        val key = if (isLandscape) CarouselRecyclerView.CAROUSEL_VIEW_TYPE_LANDSCAPE else CarouselRecyclerView.CAROUSEL_VIEW_TYPE_PORTRAIT
        val fallback = if (isLandscape) GameAdapter.VIEW_TYPE_GRID_COMPACT else GameAdapter.VIEW_TYPE_GRID
        return preferences.getInt(key, fallback)
    }

    private fun setCurrentViewType(type: Int) {
        val isLandscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        val key = if (isLandscape) CarouselRecyclerView.CAROUSEL_VIEW_TYPE_LANDSCAPE else CarouselRecyclerView.CAROUSEL_VIEW_TYPE_PORTRAIT
        preferences.edit { putInt(key, type) }
    }
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentGamesBinding.inflate(inflater)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        homeViewModel.setStatusBarShadeVisibility(true)
        mainActivity = requireActivity() as MainActivity

        if (savedInstanceState != null) {
            binding.searchText.setText(savedInstanceState.getString(SEARCH_TEXT))
        }

        gameAdapter = GameAdapter(requireActivity() as AppCompatActivity, ::selectGame)

        applyGridGamesBinding()

        binding.swipeRefresh.apply {
            (binding.swipeRefresh as? SwipeRefreshLayout)?.setOnRefreshListener {
                gamesViewModel.reloadGames(false)
            }
            (binding.swipeRefresh as? SwipeRefreshLayout)?.setProgressBackgroundColorSchemeColor(
                com.google.android.material.color.MaterialColors.getColor(
                    binding.swipeRefresh,
                    com.google.android.material.R.attr.colorPrimary
                )
            )
            (binding.swipeRefresh as? SwipeRefreshLayout)?.setColorSchemeColors(
                com.google.android.material.color.MaterialColors.getColor(
                    binding.swipeRefresh,
                    com.google.android.material.R.attr.colorOnPrimary
                )
            )
            post {
                if (_binding == null) {
                    return@post
                }
                (binding.swipeRefresh as? SwipeRefreshLayout)?.isRefreshing = gamesViewModel.isReloading.value
            }
        }

        gamesViewModel.isReloading.collect(viewLifecycleOwner) {
            (binding.swipeRefresh as? SwipeRefreshLayout)?.isRefreshing = it
            binding.noticeText.setVisible(
                visible = gamesViewModel.games.value.isEmpty() && !it,
                gone = false
            )
            binding.loadingIndicator.setVisible(it)
        }
        gamesViewModel.games.collect(viewLifecycleOwner) {
            setAdapter(it)
        }
        gamesViewModel.libraryError.collect(viewLifecycleOwner) { error ->
            binding.errorContainer.setVisible(error != null)
            binding.errorText.text = error?.takeIf { it.isNotBlank() }
                ?: getString(R.string.opensw_library_error)
        }
        gamesViewModel.shouldSwapData.collect(
            viewLifecycleOwner,
            resetState = { gamesViewModel.setShouldSwapData(false) }
        ) {
            if (it) {
                setAdapter(gamesViewModel.games.value)
            }
        }
        gamesViewModel.shouldScrollToTop.collect(
            viewLifecycleOwner,
            resetState = { gamesViewModel.setShouldScrollToTop(false) }
        ) { if (it) scrollToTop() }

        gamesViewModel.shouldScrollAfterReload.collect(viewLifecycleOwner) { shouldScroll ->
            if (shouldScroll) {
                val gridGames = binding.gridGames
                gridGames.post {
                    if (_binding?.gridGames !== gridGames) return@post
                    (gridGames as? CarouselRecyclerView)?.pendingScrollAfterReload = true
                    (gridGames as? CarouselRecyclerView)?.refreshView()
                }
                gamesViewModel.setShouldScrollAfterReload(false)
            }
        }

        setupTopView()
        if (savedInstanceState?.getBoolean(SEARCH_OPEN) == true ||
            binding.searchText.text.isNotEmpty()
        ) {
            openSearch()
        }

        updateButtonsVisibility()

        binding.addDirectory.setOnClickListener {
            getGamesDirectory.launch(Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).data)
        }

        binding.launchQlaunch?.setOnClickListener {
            launchQLaunch()
        }

        binding.retryButton.setOnClickListener { gamesViewModel.reloadGames(false) }
        binding.selectedLaunch.setOnClickListener {
            selectedGame?.let { gameAdapter.launchGame(it, binding.root) }
        }
        binding.selectedFavorite.setOnClickListener {
            selectedGame?.let(::toggleFavorite)
        }

        setInsets()
    }

    val applyGridGamesBinding = {
        (binding.gridGames as? RecyclerView)?.apply {
            val isLandscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
            val currentViewType = getCurrentViewType()
            val savedViewType = if (isLandscape || currentViewType != GameAdapter.VIEW_TYPE_CAROUSEL) currentViewType else GameAdapter.VIEW_TYPE_GRID

            // This prevents Grid/List views from reusing scaled or otherwise modified ViewHolders left over from the carousel.
            adapter = null
            recycledViewPool.clear()

            gameAdapter.setViewType(savedViewType)
            currentFilter = preferences.getInt(PREF_SORT_TYPE, View.NO_ID)

            // Set the correct layout manager
            layoutManager = when (savedViewType) {
                GameAdapter.VIEW_TYPE_GRID -> {
                    val columns = resources.getInteger(R.integer.game_columns_grid)
                    GridLayoutManager(context, columns)
                }
                GameAdapter.VIEW_TYPE_GRID_COMPACT -> {
                    val columns = resources.getInteger(R.integer.game_columns_grid_compact)
                    GridLayoutManager(context, columns)
                }
                GameAdapter.VIEW_TYPE_LIST -> {
                    val columns = resources.getInteger(R.integer.game_columns_list)
                    GridLayoutManager(context, columns)
                }
                GameAdapter.VIEW_TYPE_CAROUSEL -> {
                    LinearLayoutManager(context, RecyclerView.HORIZONTAL, false)
                }
                else -> throw IllegalArgumentException("Invalid view type: $savedViewType")
            }
            if (savedViewType == GameAdapter.VIEW_TYPE_CAROUSEL) {
                (binding.gridGames as? View)?.let { it -> ViewCompat.requestApplyInsets(it) }
                doOnNextLayout { // Carousel: important to avoid overlap issues
                    (this as? CarouselRecyclerView)?.notifyLaidOut(fallbackBottomInset)
                }
            } else {
                (this as? CarouselRecyclerView)?.setupCarousel(false)
            }
            adapter = gameAdapter
            lastViewType = savedViewType
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        if (_binding != null) {
            outState.putString(SEARCH_TEXT, binding.searchText.text.toString())
            outState.putBoolean(SEARCH_OPEN, binding.frameSearch.visibility == View.VISIBLE)
        }
    }

    override fun onPause() {
        super.onPause()
        if (getCurrentViewType() == GameAdapter.VIEW_TYPE_CAROUSEL) {
            gamesViewModel.lastScrollPosition = (binding.gridGames as? CarouselRecyclerView)?.getClosestChildPosition() ?: 0
        }
    }

    override fun onResume() {
        super.onResume()
        if (getCurrentViewType() == GameAdapter.VIEW_TYPE_CAROUSEL) {
            (binding.gridGames as? CarouselRecyclerView)?.setupCarousel(true)
            (binding.gridGames as? CarouselRecyclerView)?.restoreScrollState(
                gamesViewModel.lastScrollPosition
            )
        }
    }

    private fun setAdapter(games: List<Game>) {
        filterAndSearch(games)
        binding.noticeText.setVisible(games.isEmpty() && !gamesViewModel.isReloading.value)
        if (games.isEmpty()) {
            selectGame(null)
        }
    }

    private fun setupTopView() {
        binding.searchText.doOnTextChanged() { text: CharSequence?, _: Int, _: Int, _: Int ->
            if (text.toString().isNotEmpty()) {
                binding.clearButton.visibility = View.VISIBLE
            } else {
                binding.clearButton.visibility = View.INVISIBLE
            }
            filterAndSearch()
        }

        binding.clearButton.setOnClickListener {
            if (binding.searchText.text.isNullOrEmpty()) {
                closeSearch()
            } else {
                binding.searchText.setText("")
            }
        }
        binding.searchBackground.setOnClickListener { focusSearch() }
        binding.searchButton.setOnClickListener {
            if (binding.frameSearch.visibility == View.VISIBLE) closeSearch() else openSearch()
        }

        binding.viewButton.setOnClickListener { showViewMenu(it) }

        // Setup filter button
        binding.filterButton.setOnClickListener { view ->
            showFilterMenu(view)
        }

        // Setup settings button
        binding.settingsButton.setOnClickListener { navigateToSettings() }
    }

    private fun navigateToSettings() {
        val navController = findNavController()
        navController.navigate(R.id.action_gamesFragment_to_homeSettingsFragment)
    }

    private fun showViewMenu(anchor: View) {
        val popup = PopupMenu(requireContext(), anchor)
        popup.menuInflater.inflate(R.menu.menu_game_views, popup.menu)
        val isLandscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        if (!isLandscape) {
            popup.menu.findItem(R.id.view_carousel)?.isVisible = false
        }

        when (getCurrentViewType()) {
            GameAdapter.VIEW_TYPE_LIST -> popup.menu.findItem(R.id.view_list).isChecked = true
            GameAdapter.VIEW_TYPE_GRID_COMPACT ->
                popup.menu.findItem(R.id.view_grid_compact).isChecked = true
            GameAdapter.VIEW_TYPE_GRID -> popup.menu.findItem(R.id.view_grid).isChecked = true
            GameAdapter.VIEW_TYPE_CAROUSEL ->
                popup.menu.findItem(R.id.view_carousel).isChecked = true
        }

        popup.setOnMenuItemClickListener { item ->
            val viewType = when (item.itemId) {
                R.id.view_grid -> GameAdapter.VIEW_TYPE_GRID
                R.id.view_grid_compact -> GameAdapter.VIEW_TYPE_GRID_COMPACT
                R.id.view_list -> GameAdapter.VIEW_TYPE_LIST
                R.id.view_carousel -> GameAdapter.VIEW_TYPE_CAROUSEL
                else -> return@setOnMenuItemClickListener false
            }
            if (viewType != getCurrentViewType()) {
                if (getCurrentViewType() == GameAdapter.VIEW_TYPE_CAROUSEL) onPause()
                setCurrentViewType(viewType)
                applyGridGamesBinding()
                if (viewType == GameAdapter.VIEW_TYPE_CAROUSEL) onResume()
            }
            item.isChecked = true
            true
        }
        popup.show()
    }

    private fun showFilterMenu(anchor: View) {
        val popup = PopupMenu(requireContext(), anchor)
        popup.menuInflater.inflate(R.menu.menu_game_filters, popup.menu)

        // Set checked state based on current filter
        when (currentFilter) {
            R.id.alphabetical -> popup.menu.findItem(R.id.alphabetical).isChecked = true
            R.id.filter_recently_played -> popup.menu.findItem(R.id.filter_recently_played).isChecked =
                true

            R.id.filter_recently_added -> popup.menu.findItem(R.id.filter_recently_added).isChecked =
                true
        }

        popup.setOnMenuItemClickListener { item ->
            currentFilter = item.itemId
            preferences.edit { putInt(PREF_SORT_TYPE, currentFilter) }
            filterAndSearch()
            true
        }

        popup.show()
    }

    // Track current filter
    private var currentFilter = View.NO_ID

    private fun filterAndSearch(baseList: List<Game> = gamesViewModel.games.value) {
        val favoritePaths = favoritePaths()
        val sort = when (currentFilter) {
            R.id.alphabetical -> LibrarySort.ALPHABETICAL
            R.id.filter_recently_played -> LibrarySort.RECENTLY_PLAYED
            R.id.filter_recently_added -> LibrarySort.RECENTLY_ADDED
            else -> LibrarySort.DEFAULT
        }
        val entries = baseList.map { game ->
            LibraryEntry(
                item = game,
                title = game.title,
                isFavorite = game.path in favoritePaths,
                lastPlayedTime = if (sort == LibrarySort.RECENTLY_PLAYED) {
                    preferences.getLong(game.keyLastPlayedTime, 0L)
                } else {
                    0L
                },
                addedTime = if (sort == LibrarySort.RECENTLY_ADDED) {
                    preferences.getLong(game.keyAddedToLibraryTime, 0L)
                } else {
                    0L
                }
            )
        }
        val filteredList = GameLibraryOrganizer.organize(
            entries = entries,
            sort = sort,
            recentAfter = System.currentTimeMillis() - RECENT_WINDOW_MS
        )

        val searchTerm = binding.searchText.text.toString().lowercase(Locale.getDefault())
        if (searchTerm.isEmpty()) {
            submitFilteredList(filteredList)
            return
        }

        val searchAlgorithm = if (searchTerm.length > 1) Jaccard(2) else JaroWinkler()
        val sortedList = filteredList.mapNotNull { game ->
            val title = game.title.lowercase(Locale.getDefault())
            val score = searchAlgorithm.similarity(searchTerm, title)
            if (score > 0.03) {
                ScoredGame(score, game, game.path in favoritePaths)
            } else {
                null
            }
        }.sortedWith(
            compareByDescending<ScoredGame> { it.isFavorite }
                .thenByDescending { it.score }
        ).map { it.item }

        submitFilteredList(sortedList)
    }

    private fun submitFilteredList(games: List<Game>) {
        gamesViewModel.setFilteredGames(games)
        ((binding.gridGames as? RecyclerView)?.adapter as? GameAdapter)?.submitList(games) {
            restoreSelection(games)
        }
        binding.noticeText.setVisible(games.isEmpty() && !gamesViewModel.isReloading.value)
        if (games.isEmpty()) {
            selectGame(null)
        }
    }

    private inner class ScoredGame(
        val score: Double,
        val item: Game,
        val isFavorite: Boolean
    )

    private fun focusSearch() {
        binding.searchText.requestFocus()
        val imm = requireActivity()
            .getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager?
        imm?.showSoftInput(binding.searchText, InputMethodManager.SHOW_IMPLICIT)
    }

    private fun openSearch() {
        binding.title.visibility = View.INVISIBLE
        binding.frameSearch.visibility = View.VISIBLE
        setUtilityActionsVisible(false)
        focusSearch()
    }

    private fun closeSearch() {
        binding.searchText.setText("")
        binding.searchText.clearFocus()
        binding.frameSearch.visibility = View.GONE
        binding.title.visibility = View.VISIBLE
        setUtilityActionsVisible(true)
        val imm = requireActivity().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(binding.searchText.windowToken, 0)
    }

    private fun setUtilityActionsVisible(visible: Boolean) {
        binding.viewButton.setVisible(visible)
        binding.filterButton.setVisible(visible)
        binding.settingsButton.setVisible(visible)
        if (visible) {
            updateButtonsVisibility()
        } else {
            binding.addDirectory.visibility = View.GONE
        }
    }

    private var selectedGame: Game? = null
    private var preloadRequests: List<Disposable> = emptyList()

    private fun restoreSelection(games: List<Game>) {
        if (games.isEmpty()) {
            selectGame(null)
            return
        }
        val path = gamesViewModel.selectedGamePath.value
        selectGame(games.firstOrNull { it.path == path } ?: games.first())
    }

    private fun selectGame(game: Game?) {
        preloadRequests.forEach(Disposable::dispose)
        preloadRequests = emptyList()
        selectedGame = game
        gamesViewModel.setSelectedGame(game)
        binding.focusScene.setVisible(game != null)
        if (game == null) return
        binding.selectedTitle.text = game.title.replace(TITLE_WHITESPACE, " ")
        binding.selectedVersion.text = game.version.ifBlank { getString(R.string.opensw_no_version) }
        binding.selectedPlaytime.text = formatPlayTime(game)
        updateFavoriteButton(game)

        val filtered = gamesViewModel.filteredGames.value
        val index = filtered.indexOfFirst { it.path == game.path }
        if (index >= 0) {
            preloadRequests = ((index - 2)..(index + 2)).mapNotNull { neighbor ->
                filtered.getOrNull(neighbor)
                    ?.takeUnless { it.path == game.path }
                    ?.let { GameIconUtils.preloadGameIcon(it, 256) }
            }
        }
    }

    private fun formatPlayTime(game: Game): String {
        if (game.programId.isBlank()) return getString(R.string.opensw_never_played)
        val seconds = NativeLibrary.playTimeManagerGetPlayTime(game.programId)
        if (seconds <= 0) return getString(R.string.opensw_never_played)
        return "${seconds / 3600} h ${(seconds % 3600) / 60} min"
    }

    private fun favoritePaths(): Set<String> =
        preferences.getStringSet(PREF_FAVORITE_PATHS, emptySet()).orEmpty().toSet()

    private fun toggleFavorite(game: Game) {
        val favorites = favoritePaths().toMutableSet()
        if (!favorites.add(game.path)) {
            favorites.remove(game.path)
        }
        preferences.edit { putStringSet(PREF_FAVORITE_PATHS, favorites) }
        updateFavoriteButton(game)
        filterAndSearch()
    }

    private fun updateFavoriteButton(game: Game) {
        val isFavorite = game.path in favoritePaths()
        binding.selectedFavorite.apply {
            setImageResource(
                if (isFavorite) R.drawable.ic_star_filled else R.drawable.ic_star_outline
            )
            contentDescription = getString(
                if (isFavorite) R.string.opensw_remove_favorite else R.string.opensw_add_favorite
            )
            imageTintList = ColorStateList.valueOf(
                ContextCompat.getColor(
                    requireContext(),
                    if (isFavorite) R.color.opensw_yellow else R.color.opensw_outline
                )
            )
        }
    }

    override fun onDestroyView() {
        preloadRequests.forEach(Disposable::dispose)
        preloadRequests = emptyList()
        super.onDestroyView()
        _binding = null
    }

    private fun scrollToTop() {
        if (_binding != null) {
            (binding.gridGames as? CarouselRecyclerView)?.smoothScrollToPosition(0)
        }
    }

    private fun launchQLaunch() {
        try {
            val appletPath = NativeLibrary.getAppletLaunchPath(AppletInfo.QLaunch.entryId)
            if (appletPath.isEmpty()) {
                Toast.makeText(
                    requireContext(),
                    R.string.applets_error_applet,
                    Toast.LENGTH_SHORT
                ).show()
                return
            }

            NativeLibrary.setCurrentAppletId(AppletInfo.QLaunch.appletId)

            val qlaunchGame = Game(
                title = getString(R.string.qlaunch_applet),
                path = appletPath
            )

            val action = HomeNavigationDirections.actionGlobalEmulationActivity(qlaunchGame)
            findNavController().navigate(action)
        } catch (e: Exception) {
            Toast.makeText(
                requireContext(),
                "Failed to launch QLaunch: ${e.message}",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun updateButtonsVisibility() {
        val showQLaunch = BooleanSetting.ENABLE_QLAUNCH_BUTTON.getBoolean()
        val showFolder = BooleanSetting.ENABLE_FOLDER_BUTTON.getBoolean()
        val isFirmwareAvailable = NativeLibrary.isFirmwareAvailable()

        val shouldShowQLaunch = showQLaunch && isFirmwareAvailable
        binding.launchQlaunch.visibility = if (shouldShowQLaunch) View.VISIBLE else View.GONE

        binding.addDirectory.visibility = if (showFolder) View.VISIBLE else View.GONE
    }

    private fun setInsets() =
        ViewCompat.setOnApplyWindowInsetsListener(
            binding.root
        ) { _: View, windowInsets: WindowInsetsCompat ->
            val barInsets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            val cutoutInsets = windowInsets.getInsets(WindowInsetsCompat.Type.displayCutout())
            val spacingNavigation = resources.getDimensionPixelSize(R.dimen.spacing_navigation)
            resources.getDimensionPixelSize(R.dimen.spacing_navigation_rail)

            (binding.swipeRefresh as? SwipeRefreshLayout)?.setProgressViewEndTarget(
                false,
                barInsets.top + resources.getDimensionPixelSize(R.dimen.spacing_refresh_end)
            )

            val leftInset = maxOf(barInsets.left, cutoutInsets.left)
            val rightInset = maxOf(barInsets.right, cutoutInsets.right)
            val topInset = maxOf(barInsets.top, cutoutInsets.top)

            val mlpSwipe = binding.swipeRefresh.layoutParams as ViewGroup.MarginLayoutParams
            mlpSwipe.leftMargin = leftInset
            mlpSwipe.rightMargin = rightInset
            binding.swipeRefresh.layoutParams = mlpSwipe

            val mlpHeader = binding.header.layoutParams as ViewGroup.MarginLayoutParams

            // Store original margins only once
            if (originalHeaderTopMargin == null) {
                originalHeaderTopMargin = mlpHeader.topMargin
                originalHeaderRightMargin = mlpHeader.rightMargin
                originalHeaderLeftMargin = mlpHeader.leftMargin
            }

            // Always set margin as original + insets
            mlpHeader.leftMargin = (originalHeaderLeftMargin ?: 0) + leftInset
            mlpHeader.rightMargin = (originalHeaderRightMargin ?: 0) + rightInset
            mlpHeader.topMargin = (originalHeaderTopMargin ?: 0) + topInset
            binding.header.layoutParams = mlpHeader

            binding.noticeText.updatePadding(bottom = spacingNavigation)

            binding.gridGames.updatePadding(
                top = resources.getDimensionPixelSize(R.dimen.spacing_med)
            )

            val fabPadding = resources.getDimensionPixelSize(R.dimen.spacing_large)

            binding.launchQlaunch?.let { qlaunchButton ->
                val mlpQLaunch = qlaunchButton.layoutParams as ViewGroup.MarginLayoutParams
                mlpQLaunch.leftMargin = leftInset + fabPadding
                mlpQLaunch.bottomMargin = barInsets.bottom + fabPadding
                qlaunchButton.layoutParams = mlpQLaunch
            }

            val navInsets = windowInsets.getInsets(WindowInsetsCompat.Type.navigationBars())
            val gestureInsets = windowInsets.getInsets(WindowInsetsCompat.Type.systemGestures())
            val bottomInset = maxOf(navInsets.bottom, gestureInsets.bottom, cutoutInsets.bottom)
            fallbackBottomInset = bottomInset
            (binding.gridGames as? CarouselRecyclerView)?.notifyInsetsReady(bottomInset)
            windowInsets
        }
}
