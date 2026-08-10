// SPDX-FileCopyrightText: Copyright 2026 Eden Emulator Project
// SPDX-License-Identifier: GPL-3.0-or-later

package org.yuzu.yuzu_emu.ui.main

import android.content.Intent
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.provider.DocumentsContract
import android.view.View
import android.view.ViewGroup.MarginLayoutParams
import android.view.WindowManager
import android.view.animation.PathInterpolator
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import androidx.preference.PreferenceManager
import com.google.android.material.color.MaterialColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.io.File
import org.yuzu.yuzu_emu.NativeLibrary
import org.yuzu.yuzu_emu.R
import org.yuzu.yuzu_emu.databinding.ActivityMainBinding
import org.yuzu.yuzu_emu.dialogs.NetPlayDialog
import org.yuzu.yuzu_emu.features.settings.model.Settings
import org.yuzu.yuzu_emu.fragments.AddGameFolderDialogFragment
import org.yuzu.yuzu_emu.fragments.MessageDialogFragment
import org.yuzu.yuzu_emu.model.AddonViewModel
import org.yuzu.yuzu_emu.model.DriverViewModel
import org.yuzu.yuzu_emu.model.GamesViewModel
import org.yuzu.yuzu_emu.model.HomeViewModel
import android.os.Build
import org.yuzu.yuzu_emu.model.TaskViewModel
import org.yuzu.yuzu_emu.utils.*
import org.yuzu.yuzu_emu.utils.ViewUtils.setVisible
import androidx.core.content.edit
import org.yuzu.yuzu_emu.activities.EmulationActivity
import kotlin.text.compareTo
import androidx.core.net.toUri
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.google.android.material.textview.MaterialTextView
import org.yuzu.yuzu_emu.features.settings.model.BooleanSetting
import org.yuzu.yuzu_emu.YuzuApplication
import org.yuzu.yuzu_emu.updater.APKInstaller
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.lifecycle.lifecycleScope
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.yuzu.yuzu_emu.BuildConfig
import org.yuzu.yuzu_emu.features.migration.EdenImportCategory
import org.yuzu.yuzu_emu.features.migration.EdenImportManager
import org.yuzu.yuzu_emu.features.performance.AynThorDetector
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean

class MainActivity : AppCompatActivity(), ThemeProvider {
    private lateinit var binding: ActivityMainBinding

    private val homeViewModel: HomeViewModel by viewModels()
    private val gamesViewModel: GamesViewModel by viewModels()
    private val taskViewModel: TaskViewModel by viewModels()
    private val addonViewModel: AddonViewModel by viewModels()
    private val driverViewModel: DriverViewModel by viewModels()

    override var themeId: Int = 0

    private val CHECKED_DECRYPTION = "CheckedDecryption"
    private var checkedDecryption = false
    private var pendingEdenCategories = EdenImportCategory.entries.toSet()
    private val updateClient = OkHttpClient()
    private var updateDownloadJob: Job? = null
    private var updateDownloadGeneration = 0L
    private var apkInstaller: APKInstaller? = null

    private val edenImportTreeLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
            if (uri == null) return@registerForActivityResult
            if (uri.authority != "${BuildConfig.EDEN_PACKAGE}.user") {
                MaterialAlertDialogBuilder(this)
                    .setTitle(R.string.eden_import_failed)
                    .setMessage(R.string.eden_import_wrong_source)
                    .setPositiveButton(android.R.string.ok, null)
                    .show()
                return@registerForActivityResult
            }
            contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
            val progress = MaterialAlertDialogBuilder(this)
                .setTitle(R.string.eden_import_running)
                .setMessage(R.string.eden_import_running_description)
                .setCancelable(false)
                .show()
            lifecycleScope.launch {
                val result = runCatching {
                    withContext(Dispatchers.IO) {
                        EdenImportManager(applicationContext).import(uri, pendingEdenCategories)
                    }
                }
                progress.dismiss()
                result.onSuccess { report ->
                    PreferenceManager.getDefaultSharedPreferences(applicationContext)
                        .edit { putBoolean(PREF_EDEN_IMPORT_COMPLETE, true) }
                    MaterialAlertDialogBuilder(this@MainActivity)
                        .setTitle(R.string.eden_import_complete)
                        .setMessage(
                            getString(
                                R.string.eden_import_report,
                                report.importedFiles,
                                report.importedBytes,
                                report.backups,
                                report.skippedCategories.size
                            )
                        )
                        .setPositiveButton(android.R.string.ok, null)
                        .show()
                }.onFailure { error ->
                    MaterialAlertDialogBuilder(this@MainActivity)
                        .setTitle(R.string.eden_import_failed)
                        .setMessage(error.message ?: getString(R.string.error))
                        .setPositiveButton(android.R.string.ok, null)
                        .show()
                }
            }
        }

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(YuzuApplication.applyLanguage(base))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        splashScreen.setKeepOnScreenCondition { !DirectoryInitialization.areDirectoriesReady }

        ThemeHelper.ThemeChangeListener(this)
        ThemeHelper.setTheme(this)
        super.onCreate(savedInstanceState)
        NativeLibrary.initMultiplayer()

        binding = ActivityMainBinding.inflate(layoutInflater)

        display?.let {
            val supportedModes = it.supportedModes
            val maxRefreshRate = supportedModes.maxByOrNull { mode -> mode.refreshRate }

            if (maxRefreshRate != null) {
                val layoutParams = window.attributes
                layoutParams.preferredDisplayModeId = maxRefreshRate.modeId
                window.attributes = layoutParams
            }
        }

        setContentView(binding.root)

        if (savedInstanceState != null) {
            checkedDecryption = savedInstanceState.getBoolean(CHECKED_DECRYPTION)
        }
        if (!checkedDecryption) {
            val firstTimeSetup = PreferenceManager.getDefaultSharedPreferences(applicationContext)
                .getBoolean(Settings.PREF_FIRST_APP_LAUNCH, true)
            if (!firstTimeSetup) {
                checkKeys()
            }
            checkedDecryption = true
        }

        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING)

        window.statusBarColor =
            ContextCompat.getColor(applicationContext, android.R.color.transparent)
        window.navigationBarColor =
            ContextCompat.getColor(applicationContext, android.R.color.transparent)

        binding.statusBarShade.setBackgroundColor(
            ThemeHelper.getColorWithOpacity(
                MaterialColors.getColor(
                    binding.root,
                    com.google.android.material.R.attr.colorSurface
                ),
                ThemeHelper.SYSTEM_BAR_ALPHA
            )
        )
        if (
            InsetsHelper.getSystemGestureType(applicationContext) !=
            InsetsHelper.GESTURE_NAVIGATION
        ) {
            binding.navigationBarShade.setBackgroundColor(
                ThemeHelper.getColorWithOpacity(
                    MaterialColors.getColor(
                        binding.root,
                        com.google.android.material.R.attr.colorSurface
                    ),
                    ThemeHelper.SYSTEM_BAR_ALPHA
                )
            )
        }

        val navHostFragment =
            supportFragmentManager.findFragmentById(R.id.fragment_container) as NavHostFragment
        setUpNavigation(navHostFragment.navController)

        homeViewModel.statusBarShadeVisible.collect(this) { showStatusBarShade(it) }
        homeViewModel.contentToInstall.collect(
            this,
            resetState = { homeViewModel.setContentToInstall(null) }
        ) {
            if (it != null) {
                installContent(it)
            }
        }
        homeViewModel.checkKeys.collect(this, resetState = { homeViewModel.setCheckKeys(false) }) {
            if (it) checkKeys()
        }

        // Dismiss previous notifications (should not happen unless a crash occurred)
        EmulationActivity.stopForegroundService(this)

        val firstTimeSetup = PreferenceManager.getDefaultSharedPreferences(applicationContext)
            .getBoolean(Settings.PREF_FIRST_APP_LAUNCH, true)

        if (!BuildConfig.IS_OPENSW && !firstTimeSetup && NativeLibrary.isUpdateCheckerEnabled() &&
            BooleanSetting.ENABLE_UPDATE_CHECKS.getBoolean()
        ) {
            checkForUpdates()
        }
        setInsets()
        applyFullscreenPreference()
        if (!maybeOfferEdenImport()) {
            maybeOfferThorStable()
        }
    }

    private fun maybeOfferEdenImport(): Boolean {
        if (!BuildConfig.IS_OPENSW || !isEdenInstalled()) return false
        val preferences = PreferenceManager.getDefaultSharedPreferences(applicationContext)
        if (preferences.getBoolean(PREF_EDEN_IMPORT_COMPLETE, false) ||
            preferences.getBoolean(PREF_EDEN_IMPORT_NEVER, false)
        ) {
            return false
        }

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.eden_detected)
            .setMessage(R.string.eden_detected_description)
            .setPositiveButton(R.string.eden_import_action) { _, _ -> showEdenImportCategories() }
            .setNeutralButton(R.string.later, null)
            .setNegativeButton(R.string.never) { _, _ ->
                preferences.edit { putBoolean(PREF_EDEN_IMPORT_NEVER, true) }
            }
            .show()
        return true
    }

    private fun maybeOfferThorStable() {
        if (!BuildConfig.IS_OPENSW || !isFreshInstall() ||
            !AynThorDetector.matches(Build.MANUFACTURER, Build.MODEL, Build.PRODUCT)
        ) {
            return
        }
        val preferences = PreferenceManager.getDefaultSharedPreferences(applicationContext)
        if (preferences.getBoolean(PREF_THOR_PROFILE_OFFERED, false)) return
        preferences.edit { putBoolean(PREF_THOR_PROFILE_OFFERED, true) }

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.opensw_thor_detected)
            .setMessage(R.string.opensw_thor_detected_description)
            .setPositiveButton(R.string.opensw_use_thor_60_stable) { _, _ ->
                OpenSwPerformanceModeManager.apply(
                    applicationContext,
                    PerformanceMode.OPTI_60.value
                )
            }
            .setNegativeButton(R.string.opensw_keep_standard, null)
            .show()
    }

    private fun isFreshInstall(): Boolean = runCatching {
        val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.getPackageInfo(
                packageName,
                android.content.pm.PackageManager.PackageInfoFlags.of(0)
            )
        } else {
            @Suppress("DEPRECATION")
            packageManager.getPackageInfo(packageName, 0)
        }
        info.firstInstallTime == info.lastUpdateTime
    }.getOrDefault(false)

    private fun showEdenImportCategories() {
        val categories = EdenImportCategory.entries
        val selected = BooleanArray(categories.size) { true }
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.eden_import_categories)
            .setMultiChoiceItems(
                categories.map { getString(it.label) }.toTypedArray(),
                selected
            ) { _, index, checked -> selected[index] = checked }
            .setPositiveButton(R.string.eden_choose_folder) { _, _ ->
                pendingEdenCategories = categories
                    .filterIndexed { index, _ -> selected[index] }
                    .toSet()
                val root = DocumentsContract.buildRootUri(
                    "${BuildConfig.EDEN_PACKAGE}.user",
                    "root"
                )
                edenImportTreeLauncher.launch(root)
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun isEdenInstalled(): Boolean = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.getPackageInfo(
                BuildConfig.EDEN_PACKAGE,
                android.content.pm.PackageManager.PackageInfoFlags.of(0)
            )
        } else {
            @Suppress("DEPRECATION")
            packageManager.getPackageInfo(BuildConfig.EDEN_PACKAGE, 0)
        }
    }.isSuccess

    private fun checkForUpdates() {
        lifecycleScope.launch {
            val latestVersion = withContext(Dispatchers.IO) {
                NativeLibrary.checkForUpdate()
            }
            if (latestVersion != null) {
                showUpdateDialog(latestVersion)
            }
        }
    }

    // TODO(crueter): body, "View on Forgejo" button
    private fun showUpdateDialog(release: NativeLibrary.UpdateResult) {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.update_available)
            .setMessage(getString(R.string.update_available_description, release.title))
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val assets = release.assets

                if (assets.isEmpty()) {
                    openLink(release.url)
                } else {
                    downloadAndInstallUpdate(release)
                }
            }
            .setNeutralButton(R.string.cancel) { dialog, _ ->
                dialog.dismiss()
            }
            .setNegativeButton(R.string.dont_show_again) { dialog, _ ->
                BooleanSetting.ENABLE_UPDATE_CHECKS.setBoolean(false)
                NativeConfig.saveGlobalConfig()
                dialog.dismiss()
            }
            .show()
    }

    private fun openLink(link: String) {
        val intent = Intent(Intent.ACTION_VIEW, link.toUri())
        startActivity(intent)
    }

    private fun downloadAndInstallUpdate(release: NativeLibrary.UpdateResult) {
        val downloadGeneration = ++updateDownloadGeneration
        updateDownloadJob?.cancel()
        updateDownloadJob = lifecycleScope.launch {
            val asset = release.assets[0]
            val apkFile = createUpdateDownloadFile(cacheDir)

            showDownloadProgressDialog()
            var terminal: UpdateDownloadEvent = UpdateDownloadEvent.Failed
            try {
                downloadUpdate(updateClient, asset, apkFile).collect { event ->
                    when (event) {
                        is UpdateDownloadEvent.Progress ->
                            updateDownloadProgress(event.percentage)
                        else -> terminal = event
                    }
                }
            } finally {
                if (downloadGeneration == updateDownloadGeneration) {
                    dismissDownloadProgressDialog()
                }
            }

            if (downloadGeneration != updateDownloadGeneration) return@launch
            when (terminal) {
                UpdateDownloadEvent.Succeeded -> installUpdate(apkFile)
                UpdateDownloadEvent.Failed -> {
                    Toast.makeText(
                        this@MainActivity,
                        getString(R.string.update_download_failed) + "\n\nURL: $asset",
                        Toast.LENGTH_LONG
                    ).show()
                }
                UpdateDownloadEvent.Cancelled,
                is UpdateDownloadEvent.Progress -> Unit
            }
        }
    }

    private fun installUpdate(apkFile: File) {
        val archivePackage = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.getPackageArchiveInfo(
                apkFile.absolutePath,
                android.content.pm.PackageManager.PackageInfoFlags.of(0)
            )
        } else {
            @Suppress("DEPRECATION")
            packageManager.getPackageArchiveInfo(apkFile.absolutePath, 0)
        }?.packageName
        if (!apkFile.isFile || apkFile.length() <= 0L || archivePackage != packageName) {
            apkFile.delete()
            Toast.makeText(this, R.string.update_download_failed, Toast.LENGTH_LONG).show()
            return
        }

        apkInstaller?.cancel()
        val installer = APKInstaller(applicationContext)
        apkInstaller = installer
        installer.install(
            apkFile,
            onComplete = onComplete@{
                if (apkInstaller !== installer || isDestroyed) return@onComplete
                apkInstaller = null
                Toast.makeText(
                    this@MainActivity,
                    R.string.update_installed_successfully,
                    Toast.LENGTH_LONG
                ).show()
            },
            onFailure = onFailure@{ exception ->
                if (apkInstaller !== installer || isDestroyed) return@onFailure
                apkInstaller = null
                Toast.makeText(
                    this@MainActivity,
                    getString(R.string.update_install_failed, exception.message),
                    Toast.LENGTH_LONG
                ).show()
            }
        )
    }

    private var progressDialog: androidx.appcompat.app.AlertDialog? = null
    private var progressBar: LinearProgressIndicator? = null
    private var progressMessage: MaterialTextView? = null

    private fun showDownloadProgressDialog() {
        val progressView = layoutInflater.inflate(R.layout.dialog_download_progress, null)
        progressBar = progressView.findViewById(R.id.download_progress_bar)
        progressMessage = progressView.findViewById(R.id.download_progress_message)

        progressDialog = MaterialAlertDialogBuilder(this)
            .setTitle(R.string.downloading_update)
            .setView(progressView)
            .setCancelable(false)
            .create()
        progressDialog?.show()
    }

    private fun updateDownloadProgress(progress: Int) {
        progressBar?.progress = progress
        progressMessage?.text = getString(R.string.percent, progress)
    }

    private fun dismissDownloadProgressDialog() {
        progressDialog?.dismiss()
        progressDialog = null
        progressBar = null
        progressMessage = null
    }
    fun displayMultiplayerDialog() {
        val dialog = NetPlayDialog(this)
        dialog.show()
    }

    private fun checkKeys() {
        if (!NativeLibrary.areKeysPresent()) {
            MessageDialogFragment.newInstance(
                titleId = R.string.keys_missing,
                descriptionId = R.string.keys_missing_description,
                helpLinkId = R.string.keys_missing_help
            ).show(supportFragmentManager, MessageDialogFragment.TAG)
        }
    }
    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(CHECKED_DECRYPTION, checkedDecryption)
    }

    fun finishSetup(navController: NavController) {
        navController.navigate(R.id.action_firstTimeSetupFragment_to_gamesFragment)
    }

    private fun setUpNavigation(navController: NavController) {
        val firstTimeSetup = PreferenceManager.getDefaultSharedPreferences(applicationContext)
            .getBoolean(Settings.PREF_FIRST_APP_LAUNCH, true)

        if (firstTimeSetup && !homeViewModel.navigatedToSetup) {
            navController.navigate(R.id.firstTimeSetupFragment)
            homeViewModel.navigatedToSetup = true
        }
    }

    private fun showStatusBarShade(visible: Boolean) {
        binding.statusBarShade.animate().apply {
            if (visible) {
                binding.statusBarShade.setVisible(true)
                binding.statusBarShade.translationY = binding.statusBarShade.height.toFloat() * -2
                duration = 300
                translationY(0f)
                interpolator = PathInterpolator(0.05f, 0.7f, 0.1f, 1f)
            } else {
                duration = 300
                translationY(binding.statusBarShade.height.toFloat() * -2)
                interpolator = PathInterpolator(0.3f, 0f, 0.8f, 0.15f)
            }
        }.withEndAction {
            if (!visible) {
                binding.statusBarShade.setVisible(visible = false, gone = false)
            }
        }.start()
    }

    override fun onResume() {
        ThemeHelper.setCorrectTheme(this)
        super.onResume()
        applyFullscreenPreference()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            applyFullscreenPreference()
        }
    }

    private fun setInsets() = ViewCompat.setOnApplyWindowInsetsListener(
        binding.root
    ) { _: View, windowInsets: WindowInsetsCompat ->
        val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
        val mlpStatusShade = binding.statusBarShade.layoutParams as MarginLayoutParams
        mlpStatusShade.height = insets.top
        binding.statusBarShade.layoutParams = mlpStatusShade

        // The only situation where we care to have a nav bar shade is when it's at the bottom
        // of the screen where scrolling list elements can go behind it.
        val mlpNavShade = binding.navigationBarShade.layoutParams as MarginLayoutParams
        mlpNavShade.height = insets.bottom
        binding.navigationBarShade.layoutParams = mlpNavShade

        windowInsets
    }

    private fun applyFullscreenPreference() {
        FullscreenHelper.applyToActivity(this)
    }

    override fun setTheme(resId: Int) {
        super.setTheme(resId)
        themeId = resId
    }

    override fun onDestroy() {
        updateDownloadGeneration++
        updateDownloadJob?.cancel()
        updateDownloadJob = null
        apkInstaller?.cancel()
        apkInstaller = null
        dismissDownloadProgressDialog()
        EmulationActivity.stopForegroundService(this)
        super.onDestroy()
    }

    val getGamesDirectory =
        registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { result ->
            if (result != null) {
                processGamesDir(result)
            }
        }

    val getExternalContentDirectory =
        registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { result ->
            if (result != null) {
                processExternalContentDir(result)
            }
        }

    fun processGamesDir(result: Uri, calledFromGameFragment: Boolean = false) {
        contentResolver.takePersistableUriPermission(
            result,
            Intent.FLAG_GRANT_READ_URI_PERMISSION
        )

        val uriString = result.toString()
        val folder = gamesViewModel.folders.value.firstOrNull { it.uriString == uriString }
        if (folder != null) {
            Toast.makeText(
                applicationContext,
                R.string.folder_already_added,
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        AddGameFolderDialogFragment.newInstance(uriString, calledFromGameFragment)
            .show(supportFragmentManager, AddGameFolderDialogFragment.TAG)
    }

    fun processExternalContentDir(result: Uri) {
        contentResolver.takePersistableUriPermission(
            result,
            Intent.FLAG_GRANT_READ_URI_PERMISSION
        )

        val uriString = result.toString()
        val folder = gamesViewModel.folders.value.firstOrNull {
            it.uriString == uriString &&
                it.type == org.yuzu.yuzu_emu.model.DirectoryType.EXTERNAL_CONTENT
        }
        if (folder != null) {
            Toast.makeText(
                applicationContext,
                R.string.folder_already_added,
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        val externalContentDir = org.yuzu.yuzu_emu.model.GameDir(
            uriString,
            false,
            org.yuzu.yuzu_emu.model.DirectoryType.EXTERNAL_CONTENT
        )
        gamesViewModel.addFolder(externalContentDir, savedFromGameFragment = false)
    }

    val getProdKey = registerForActivityResult(ActivityResultContracts.OpenDocument()) { result ->
        if (result != null) {
            processKey(result, "keys")
        }
    }

    val getAmiiboKey = registerForActivityResult(ActivityResultContracts.OpenDocument()) { result ->
        if (result != null) {
            processKey(result, "bin")
        }
    }

    fun processKey(result: Uri, extension: String = "keys") {
        InstallableActions.processKey(
            activity = this,
            fragmentManager = supportFragmentManager,
            gamesViewModel = gamesViewModel,
            result = result,
            extension = extension
        )
    }

    val getFirmware = registerForActivityResult(ActivityResultContracts.OpenDocument()) { result ->
        if (result != null) {
            processFirmware(result)
        }
    }

    fun processFirmware(result: Uri, onComplete: (() -> Unit)? = null) {
        InstallableActions.processFirmware(
            activity = this,
            fragmentManager = supportFragmentManager,
            homeViewModel = homeViewModel,
            result = result,
            onComplete = onComplete
        )
    }

    fun uninstallFirmware() {
        InstallableActions.uninstallFirmware(
            activity = this,
            fragmentManager = supportFragmentManager,
            homeViewModel = homeViewModel
        )
    }

    private fun installContent(documents: List<Uri>) {
        InstallableActions.installContent(
            activity = this,
            fragmentManager = supportFragmentManager,
            addonViewModel = addonViewModel,
            documents = documents
        )
    }

    val exportUserData = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip")
    ) { result ->
        if (result == null) {
            return@registerForActivityResult
        }
        InstallableActions.exportUserData(
            activity = this,
            fragmentManager = supportFragmentManager,
            result = result
        )
    }

    val importUserData =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { result ->
            if (result == null) {
                return@registerForActivityResult
            }
            InstallableActions.importUserData(
                activity = this,
                fragmentManager = supportFragmentManager,
                gamesViewModel = gamesViewModel,
                driverViewModel = driverViewModel,
                result = result
            )
        }

    private companion object {
        const val PREF_EDEN_IMPORT_COMPLETE = "opensw_eden_import_complete"
        const val PREF_EDEN_IMPORT_NEVER = "opensw_eden_import_never"
        const val PREF_THOR_PROFILE_OFFERED = "opensw_thor_profile_offered"
    }
}

internal sealed interface UpdateDownloadEvent {
    data class Progress(val percentage: Int) : UpdateDownloadEvent
    data object Succeeded : UpdateDownloadEvent
    data object Failed : UpdateDownloadEvent
    data object Cancelled : UpdateDownloadEvent
}

internal fun classifyUpdateDownload(
    responseSuccessful: Boolean,
    bodyPresent: Boolean,
    contentLength: Long,
    bytesRead: Long,
    cancelled: Boolean
): UpdateDownloadEvent = when {
    cancelled -> UpdateDownloadEvent.Cancelled
    !responseSuccessful || !bodyPresent || bytesRead <= 0L -> UpdateDownloadEvent.Failed
    contentLength >= 0L && bytesRead != contentLength -> UpdateDownloadEvent.Failed
    else -> UpdateDownloadEvent.Succeeded
}

internal fun createUpdateDownloadFile(cacheDir: File): File =
    File.createTempFile("update-", ".apk", cacheDir)

private fun downloadUpdate(
    client: OkHttpClient,
    asset: String,
    apkFile: File
): Flow<UpdateDownloadEvent> = callbackFlow {
    val call = client.newCall(Request.Builder().url(asset).build())
    val completedSuccessfully = AtomicBoolean(false)
    call.enqueue(
        object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                val event = if (call.isCanceled()) {
                    UpdateDownloadEvent.Cancelled
                } else {
                    e.printStackTrace()
                    UpdateDownloadEvent.Failed
                }
                trySend(event)
                close()
            }

            override fun onResponse(call: Call, response: Response) {
                var succeeded = false
                try {
                    response.use {
                        if (!response.isSuccessful) {
                            trySend(
                                classifyUpdateDownload(
                                    responseSuccessful = false,
                                    bodyPresent = response.body != null,
                                    contentLength = response.body?.contentLength() ?: -1L,
                                    bytesRead = 0L,
                                    cancelled = call.isCanceled()
                                )
                            )
                            return@use
                        }
                        val body = response.body
                        if (body == null) {
                            trySend(
                                classifyUpdateDownload(
                                    responseSuccessful = true,
                                    bodyPresent = false,
                                    contentLength = -1L,
                                    bytesRead = 0L,
                                    cancelled = call.isCanceled()
                                )
                            )
                            return@use
                        }
                        val contentLength = body.contentLength()
                        var totalBytesRead = 0L
                        var lastProgress = -1

                        body.byteStream().use { input ->
                            apkFile.outputStream().buffered().use { output ->
                                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                                while (!call.isCanceled()) {
                                    val bytesRead = input.read(buffer)
                                    if (bytesRead == -1) break

                                    output.write(buffer, 0, bytesRead)
                                    totalBytesRead += bytesRead
                                    if (contentLength > 0) {
                                        val progress =
                                            (totalBytesRead * 100 / contentLength)
                                                .toInt()
                                                .coerceIn(0, 100)
                                        if (progress != lastProgress) {
                                            lastProgress = progress
                                            trySend(UpdateDownloadEvent.Progress(progress))
                                        }
                                    }
                                }
                            }
                        }

                        val terminal = classifyUpdateDownload(
                            responseSuccessful = true,
                            bodyPresent = true,
                            contentLength = contentLength,
                            bytesRead = totalBytesRead,
                            cancelled = call.isCanceled()
                        )
                        if (terminal == UpdateDownloadEvent.Succeeded) {
                            succeeded = true
                            completedSuccessfully.set(true)
                        }
                        trySend(terminal)
                    }
                } catch (e: IOException) {
                    val terminal = if (call.isCanceled()) {
                        UpdateDownloadEvent.Cancelled
                    } else {
                        e.printStackTrace()
                        UpdateDownloadEvent.Failed
                    }
                    trySend(terminal)
                } finally {
                    if (!succeeded) {
                        apkFile.delete()
                    }
                    close()
                }
            }
        }
    )
    awaitClose {
        call.cancel()
        if (!completedSuccessfully.get()) {
            apkFile.delete()
        }
    }
}.buffer(Channel.CONFLATED)
