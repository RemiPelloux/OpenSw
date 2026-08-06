// SPDX-FileCopyrightText: Copyright 2026 Eden Emulator Project
// SPDX-License-Identifier: GPL-3.0-or-later

package org.yuzu.yuzu_emu.fragments

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.documentfile.provider.DocumentFile
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.transition.MaterialSharedAxis
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.tabs.TabLayout
import org.yuzu.yuzu_emu.R
import org.yuzu.yuzu_emu.adapters.AddonAdapter
import org.yuzu.yuzu_emu.databinding.FragmentAddonsBinding
import org.yuzu.yuzu_emu.databinding.ListItemCatalogCheatBinding
import org.yuzu.yuzu_emu.features.cheats.CatalogCheat
import org.yuzu.yuzu_emu.features.cheats.CheatCatalogClient
import org.yuzu.yuzu_emu.features.cheats.CheatImportManager
import org.yuzu.yuzu_emu.features.cheats.CheatInstaller
import org.yuzu.yuzu_emu.features.cheats.CheatTextValidator
import org.yuzu.yuzu_emu.model.AddonViewModel
import org.yuzu.yuzu_emu.model.HomeViewModel
import org.yuzu.yuzu_emu.utils.AddonUtil
import org.yuzu.yuzu_emu.utils.FileUtil.copyFilesTo
import org.yuzu.yuzu_emu.utils.InstallableActions
import org.yuzu.yuzu_emu.utils.ViewUtils.updateMargins
import org.yuzu.yuzu_emu.utils.collect
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AddonsFragment : Fragment() {
    private var _binding: FragmentAddonsBinding? = null
    private val binding get() = _binding!!

    private val homeViewModel: HomeViewModel by activityViewModels()
    private val addonViewModel: AddonViewModel by activityViewModels()

    private val args by navArgs<AddonsFragmentArgs>()
    private var catalogSelected = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        addonViewModel.onAddonsViewCreated(args.game)
        enterTransition = MaterialSharedAxis(MaterialSharedAxis.X, true)
        returnTransition = MaterialSharedAxis(MaterialSharedAxis.X, false)
        reenterTransition = MaterialSharedAxis(MaterialSharedAxis.X, false)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAddonsBinding.inflate(inflater)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        homeViewModel.setStatusBarShadeVisibility(false)

        binding.toolbarAddons.setNavigationOnClickListener {
            requireActivity().onBackPressedDispatcher.onBackPressed()
        }

        binding.toolbarAddons.title = getString(R.string.addons_game, args.game.title)

        binding.addonTabs.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                catalogSelected = tab.position == 1
                binding.listAddons.visibility = if (catalogSelected) View.GONE else View.VISIBLE
                binding.catalogPanel.visibility = if (catalogSelected) View.VISIBLE else View.GONE
                binding.buttonInstall.setText(
                    if (catalogSelected) R.string.cheat_import else R.string.install
                )
                binding.buttonInstall.setIconResource(
                    if (catalogSelected) R.drawable.ic_folder_open else R.drawable.ic_add
                )
                if (catalogSelected && binding.catalogBuildId.text.isNullOrBlank()) {
                    binding.catalogBuildId.setText(findInstalledBuildId().orEmpty())
                    if (!binding.catalogBuildId.text.isNullOrBlank()) loadCatalog(force = false)
                }
            }

            override fun onTabUnselected(tab: TabLayout.Tab) = Unit
            override fun onTabReselected(tab: TabLayout.Tab) = Unit
        })

        binding.listAddons.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = AddonAdapter(addonViewModel)
        }

        addonViewModel.addonList.collect(viewLifecycleOwner) {
            (binding.listAddons.adapter as AddonAdapter).submitList(it)
        }
        addonViewModel.showModInstallPicker.collect(
            viewLifecycleOwner,
            resetState = { addonViewModel.showModInstallPicker(false) }
        ) { if (it) installAddon.launch(Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).data) }
        addonViewModel.showModNoticeDialog.collect(
            viewLifecycleOwner,
            resetState = { addonViewModel.showModNoticeDialog(false) }
        ) {
            if (it) {
                MessageDialogFragment.newInstance(
                    requireActivity(),
                    titleId = R.string.addon_notice,
                    descriptionId = R.string.addon_notice_description,
                    dismissible = false,
                    positiveAction = { addonViewModel.showModInstallPicker(true) },
                    negativeAction = {},
                    negativeButtonTitleId = R.string.close
                ).show(parentFragmentManager, MessageDialogFragment.TAG)
            }
        }
        addonViewModel.addonToDelete.collect(
            viewLifecycleOwner,
            resetState = { addonViewModel.setAddonToDelete(null) }
        ) {
            if (it != null) {
                MessageDialogFragment.newInstance(
                    requireActivity(),
                    titleId = R.string.confirm_uninstall,
                    descriptionId = R.string.confirm_uninstall_description,
                    positiveAction = { addonViewModel.onDeleteAddon(it) },
                    negativeAction = {}
                ).show(parentFragmentManager, MessageDialogFragment.TAG)
            }
        }
        parentFragmentManager.setFragmentResultListener(
            ContentTypeSelectionDialogFragment.REQUEST_INSTALL_GAME_UPDATE,
            viewLifecycleOwner
        ) { _, _ ->
            installGameUpdate.launch(arrayOf("*/*"))
        }

        binding.catalogRefresh.setOnClickListener { loadCatalog(force = true) }

        binding.buttonInstall.setOnClickListener {
            if (catalogSelected) {
                showCheatImportPicker()
            } else {
                ContentTypeSelectionDialogFragment().show(
                    parentFragmentManager,
                    ContentTypeSelectionDialogFragment.TAG
                )
            }
        }

        setInsets()
    }

    override fun onResume() {
        super.onResume()
        addonViewModel.onAddonsViewStarted(args.game)
    }

    override fun onDestroy() {
        if (activity?.isChangingConfigurations != true) {
            addonViewModel.onCloseAddons()
        }
        super.onDestroy()
    }

    private val installAddon =
        registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { result ->
            if (result == null) {
                return@registerForActivityResult
            }

            val externalAddonDirectory = DocumentFile.fromTreeUri(requireContext(), result)
            if (externalAddonDirectory == null) {
                MessageDialogFragment.newInstance(
                    requireActivity(),
                    titleId = R.string.invalid_directory,
                    descriptionId = R.string.invalid_directory_description
                ).show(parentFragmentManager, MessageDialogFragment.TAG)
                return@registerForActivityResult
            }

            val isValid = externalAddonDirectory.listFiles()
                .any { AddonUtil.validAddonDirectories.contains(it.name?.lowercase()) }
            val errorMessage = MessageDialogFragment.newInstance(
                requireActivity(),
                titleId = R.string.invalid_directory,
                descriptionId = R.string.invalid_directory_description
            )
            if (isValid) {
                ProgressDialogFragment.newInstance(
                    requireActivity(),
                    R.string.installing_game_content,
                    false
                ) { progressCallback, _ ->
                    val parentDirectoryName = externalAddonDirectory.name
                    val internalAddonDirectory =
                        File(args.game.addonDir + parentDirectoryName)
                    try {
                        externalAddonDirectory.copyFilesTo(internalAddonDirectory, progressCallback)
                    } catch (_: Exception) {
                        return@newInstance errorMessage
                    }
                    addonViewModel.refreshAddons(force = true)
                    return@newInstance getString(R.string.addon_installed_successfully)
                }.show(parentFragmentManager, ProgressDialogFragment.TAG)
            } else {
                errorMessage.show(parentFragmentManager, MessageDialogFragment.TAG)
            }
        }

    private val installGameUpdate =
        registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { documents ->
            InstallableActions.verifyAndInstallContent(
                activity = requireActivity(),
                fragmentManager = parentFragmentManager,
                addonViewModel = addonViewModel,
                documents = documents,
                programId = args.game.programId
            )
        }

    private val importCheatDocument =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) parseImportedCheats {
                CheatImportManager(requireContext()).fromDocument(uri, args.game.programIdHex)
            }
        }

    private val importCheatTree =
        registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
            if (uri != null) parseImportedCheats {
                CheatImportManager(requireContext()).fromTree(uri, args.game.programIdHex)
            }
        }

    private fun showCheatImportPicker() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.cheat_import)
            .setItems(arrayOf(getString(R.string.cheat_import_file), getString(R.string.cheat_import_tree))) {
                    _, index ->
                if (index == 0) importCheatDocument.launch(arrayOf("text/plain", "application/zip", "*/*"))
                else importCheatTree.launch(null)
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun parseImportedCheats(loader: () -> List<CatalogCheat>) {
        viewLifecycleOwner.lifecycleScope.launch {
            val result = runCatching { withContext(Dispatchers.IO) { loader() } }
            result.onSuccess { cheats ->
                if (cheats.isEmpty()) {
                    showCatalogMessage(R.string.cheat_import_empty)
                } else {
                    confirmInstall(cheats)
                }
            }.onFailure { showCatalogError(it) }
        }
    }

    private fun loadCatalog(force: Boolean) {
        val buildId = runCatching {
            CheatTextValidator.requireBuildId(binding.catalogBuildId.text?.toString().orEmpty())
        }.getOrElse {
            showCatalogMessage(R.string.cheat_build_id_invalid)
            return
        }
        binding.catalogState.setText(R.string.cheat_catalog_loading)
        binding.catalogState.visibility = View.VISIBLE
        binding.catalogResults.removeAllViews()
        binding.catalogRefresh.isEnabled = false
        viewLifecycleOwner.lifecycleScope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    CheatCatalogClient(requireContext()).fetch(
                        args.game.programIdHex,
                        buildId,
                        force
                    )
                }
            }
            binding.catalogRefresh.isEnabled = true
            result.onSuccess { cheats -> renderCatalog(cheats) }
                .onFailure { showCatalogError(it) }
        }
    }

    private fun renderCatalog(cheats: List<CatalogCheat>) {
        binding.catalogResults.removeAllViews()
        if (cheats.isEmpty()) {
            showCatalogMessage(R.string.cheat_catalog_empty)
            return
        }
        binding.catalogState.visibility = View.GONE
        cheats.forEach { cheat ->
            val item = ListItemCatalogCheatBinding.inflate(layoutInflater, binding.catalogResults, false)
            item.catalogSource.text = cheat.source + if (cheat.fromOfflineCache) " · offline" else ""
            item.catalogMetadata.text = getString(
                R.string.cheat_catalog_metadata,
                cheat.author,
                cheat.date,
                cheat.sha256
            )
            item.catalogInstall.setOnClickListener { confirmInstall(listOf(cheat)) }
            binding.catalogResults.addView(item.root)
        }
    }

    private fun confirmInstall(cheats: List<CatalogCheat>) {
        val conflicts = cheats.mapNotNull { cheat ->
            val destination = CheatInstaller.destination(cheat)
            if (!destination.exists()) return@mapNotNull null
            val diff = runCatching { CheatInstaller.diff(destination.readText(), cheat.text) }.getOrNull()
            cheat.buildId to diff
        }
        val message = buildString {
            cheats.forEach { appendLine("${it.buildId} · ${it.source} · SHA-256 ${it.sha256}") }
            conflicts.forEach { (buildId, diff) ->
                appendLine()
                appendLine(getString(R.string.cheat_conflict, buildId))
                if (diff != null) {
                    appendLine("+ ${diff.added.joinToString()}")
                    appendLine("- ${diff.removed.joinToString()}")
                    appendLine("~ ${diff.modified.joinToString()}")
                }
            }
        }.trim()
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(if (conflicts.isEmpty()) R.string.cheat_install_title else R.string.cheat_replace_title)
            .setMessage(message)
            .setPositiveButton(if (conflicts.isEmpty()) R.string.install else R.string.replace) { _, _ ->
                runCatching { cheats.forEach { CheatInstaller.install(it, overwrite = conflicts.isNotEmpty()) } }
                    .onSuccess {
                        showCatalogMessage(R.string.cheat_install_success)
                        addonViewModel.refreshAddons(force = true)
                    }
                    .onFailure { showCatalogError(it) }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun findInstalledBuildId(): String? = File(args.game.addonDir)
        .walkTopDown()
        .firstOrNull { file ->
            file.isFile && file.extension.equals("txt", ignoreCase = true) &&
                file.parentFile?.name.equals("cheats", ignoreCase = true) &&
                runCatching { CheatTextValidator.requireBuildId(file.nameWithoutExtension) }.isSuccess
        }
        ?.nameWithoutExtension
        ?.uppercase()

    private fun showCatalogError(error: Throwable) {
        binding.catalogState.text = error.message ?: getString(R.string.error)
        binding.catalogState.visibility = View.VISIBLE
    }

    private fun showCatalogMessage(message: Int) {
        binding.catalogState.setText(message)
        binding.catalogState.visibility = View.VISIBLE
    }

    private fun setInsets() =
        ViewCompat.setOnApplyWindowInsetsListener(
            binding.root
        ) { _: View, windowInsets: WindowInsetsCompat ->
            val barInsets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            val cutoutInsets = windowInsets.getInsets(WindowInsetsCompat.Type.displayCutout())

            val leftInsets = barInsets.left + cutoutInsets.left
            val rightInsets = barInsets.right + cutoutInsets.right

            binding.toolbarAddons.updateMargins(left = leftInsets, right = rightInsets)
            binding.listAddons.updateMargins(left = leftInsets, right = rightInsets)
            binding.listAddons.updatePadding(
                bottom = barInsets.bottom +
                    resources.getDimensionPixelSize(R.dimen.spacing_bottom_list_fab)
            )

            val fabSpacing = resources.getDimensionPixelSize(R.dimen.spacing_fab)
            binding.buttonInstall.updateMargins(
                left = leftInsets + fabSpacing,
                right = rightInsets + fabSpacing,
                bottom = barInsets.bottom + fabSpacing
            )

            windowInsets
        }
}
