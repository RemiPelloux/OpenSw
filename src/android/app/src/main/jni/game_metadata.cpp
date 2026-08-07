// SPDX-FileCopyrightText: Copyright 2026 Eden Emulator Project
// SPDX-License-Identifier: GPL-3.0-or-later

// SPDX-FileCopyrightText: Copyright 2023 yuzu Emulator Project
// SPDX-License-Identifier: GPL-2.0-or-later

#include <memory>
#include <mutex>
#include "common/android/android_common.h"
#include "common/android/id_cache.h"
#include "core/core.h"
#include "core/file_sys/fs_filesystem.h"
#include "core/file_sys/patch_manager.h"
#include "core/loader/loader.h"
#include "core/loader/nro.h"
#include "native.h"

struct RomMetadata {
    std::string title;
    u64 programId{};
    std::string developer;
    std::string version;
    std::vector<u8> icon;
    bool isHomebrew{};
};
using RomMetadataPtr = std::shared_ptr<const RomMetadata>;

static std::mutex m_rom_metadata_cache_mutex;
static ankerl::unordered_dense::map<std::string, RomMetadataPtr> m_rom_metadata_cache;

static RomMetadataPtr CacheRomMetadata(const std::string& path) {
    auto& instance = EmulationSession::GetInstance();
    const auto file = Core::GetGameFileFromPath(instance.System().GetFilesystem(), path);
    if (auto loader = Loader::GetLoader(instance.System(), file, 0, 0); loader) {
        const auto file_type = loader->GetFileType();
        if (file_type == Loader::FileType::Unknown || file_type == Loader::FileType::Error ||
            ((file_type == Loader::FileType::NSP || file_type == Loader::FileType::XCI) &&
             !Loader::IsBootableGameContainer(file, file_type))) {
            return nullptr;
        }

        RomMetadata entry;
        if (loader->ReadProgramId(entry.programId) != Loader::ResultStatus::Success) {
            return nullptr;
        }
        loader->ReadTitle(entry.title);
        loader->ReadIcon(entry.icon);

        const FileSys::PatchManager pm{entry.programId, instance.System().GetFileSystemController(),
                                       instance.System().GetContentProvider()};
        const auto control = pm.GetControlMetadata();

        if (control.first != nullptr) {
            entry.developer = control.first->GetDeveloperName();
            entry.version = control.first->GetVersionString();
        } else {
            FileSys::NACP nacp{};
            entry.developer = loader->ReadControlData(nacp) == Loader::ResultStatus::Success
                                  ? nacp.GetDeveloperName()
                                  : "";
            entry.version = "1.0.0";
        }

        FileSys::VirtualFile packed_update;
        loader->ReadUpdateRaw(packed_update);
        const auto patches = pm.GetPatches(packed_update);
        const FileSys::Patch* selected_update = nullptr;
        for (const auto& patch : patches) {
            if (!patch.enabled || patch.type != FileSys::PatchType::Update ||
                patch.version.empty()) {
                continue;
            }

            const bool installed = patch.source == FileSys::PatchSource::NAND ||
                                   patch.source == FileSys::PatchSource::SDMC;
            if (selected_update == nullptr || installed) {
                selected_update = &patch;
            }
            if (installed) {
                break;
            }
        }
        if (selected_update != nullptr && selected_update->version != "PACKED") {
            entry.version = selected_update->version;
        }

        if (loader->GetFileType() == Loader::FileType::NRO) {
            auto loader_nro = reinterpret_cast<Loader::AppLoader_NRO*>(loader.get());
            entry.isHomebrew = loader_nro->IsHomebrew();
        } else {
            entry.isHomebrew = false;
        }
        auto metadata = std::make_shared<const RomMetadata>(std::move(entry));
        m_rom_metadata_cache.insert_or_assign(path, metadata);
        return metadata;
    }
    return nullptr;
}

static RomMetadataPtr GetRomMetadata(const std::string& path, bool reload = false) {
    std::scoped_lock lock{m_rom_metadata_cache_mutex};
    if (reload) {
        m_rom_metadata_cache.erase(path);
        return CacheRomMetadata(path);
    }
    if (auto it = m_rom_metadata_cache.find(path); it != m_rom_metadata_cache.end())
        return it->second;
    return CacheRomMetadata(path);
}

static RomMetadataPtr GetRomMetadataOrDefault(const std::string& path, bool reload = false) {
    static const auto empty_metadata = std::make_shared<const RomMetadata>();
    const auto metadata = GetRomMetadata(path, reload);
    return metadata != nullptr ? metadata : empty_metadata;
}

extern "C" {

jobject Java_org_yuzu_yuzu_1emu_utils_GameMetadata_getGame(JNIEnv* env, jobject obj,
                                                           jstring jpath) {
    const std::string path = Common::Android::GetJString(env, jpath);
    const auto metadata = GetRomMetadata(path);
    if (metadata == nullptr) {
        return nullptr;
    }

    return env->NewObject(Common::Android::GetGameClass(), Common::Android::GetGameConstructor(),
                          Common::Android::ToJString(env, metadata->title),
                          Common::Android::ToJString(env, path),
                          Common::Android::ToJString(env, std::to_string(metadata->programId)),
                          Common::Android::ToJString(env, metadata->developer),
                          Common::Android::ToJString(env, metadata->version),
                          static_cast<jboolean>(metadata->isHomebrew));
}

jstring Java_org_yuzu_yuzu_1emu_utils_GameMetadata_getTitle(JNIEnv* env, jobject obj,
                                                            jstring jpath) {
    return Common::Android::ToJString(
        env, GetRomMetadataOrDefault(Common::Android::GetJString(env, jpath))->title);
}

jstring Java_org_yuzu_yuzu_1emu_utils_GameMetadata_getProgramId(JNIEnv* env, jobject obj,
                                                                jstring jpath) {
    return Common::Android::ToJString(
        env, std::to_string(
                 GetRomMetadataOrDefault(Common::Android::GetJString(env, jpath))->programId));
}

jstring Java_org_yuzu_yuzu_1emu_utils_GameMetadata_getDeveloper(JNIEnv* env, jobject obj,
                                                                jstring jpath) {
    return Common::Android::ToJString(
        env, GetRomMetadataOrDefault(Common::Android::GetJString(env, jpath))->developer);
}

jstring Java_org_yuzu_yuzu_1emu_utils_GameMetadata_getVersion(JNIEnv* env, jobject obj,
                                                              jstring jpath, jboolean jreload) {
    return Common::Android::ToJString(
        env, GetRomMetadataOrDefault(Common::Android::GetJString(env, jpath), jreload)->version);
}

jbyteArray Java_org_yuzu_yuzu_1emu_utils_GameMetadata_getIcon(JNIEnv* env, jobject obj,
                                                              jstring jpath) {
    const auto metadata = GetRomMetadataOrDefault(Common::Android::GetJString(env, jpath));
    const auto& icon_data = metadata->icon;
    jbyteArray icon = env->NewByteArray(jsize(icon_data.size()));
    env->SetByteArrayRegion(icon, 0, env->GetArrayLength(icon),
                            reinterpret_cast<const jbyte*>(icon_data.data()));
    return icon;
}

jboolean Java_org_yuzu_yuzu_1emu_utils_GameMetadata_getIsHomebrew(JNIEnv* env, jobject obj,
                                                                  jstring jpath) {
    return jboolean(GetRomMetadataOrDefault(Common::Android::GetJString(env, jpath))->isHomebrew);
}

void Java_org_yuzu_yuzu_1emu_utils_GameMetadata_removeMetadata(JNIEnv* env, jobject obj,
                                                               jstring jpath) {
    const std::scoped_lock lock{m_rom_metadata_cache_mutex};
    m_rom_metadata_cache.erase(Common::Android::GetJString(env, jpath));
}

void Java_org_yuzu_yuzu_1emu_utils_GameMetadata_resetMetadata(JNIEnv* env, jobject obj) {
    const std::scoped_lock lock{m_rom_metadata_cache_mutex};
    m_rom_metadata_cache.clear();
}

} // extern "C"
