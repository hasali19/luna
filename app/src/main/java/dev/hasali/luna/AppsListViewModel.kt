package dev.hasali.luna

import android.app.Application
import android.content.pm.PackageInfo
import android.graphics.drawable.Drawable
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.hasali.luna.data.LunaDatabase
import dev.hasali.luna.data.Package
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.http.isSuccess
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import logcat.logcat

data class PackageModel(
    val pkg: Package,
    val info: PackageInfo?,
    val icon: Drawable?,
)

data class LunaPackageState(
    val name: String,
    val packageName: String,
    val icon: Drawable,
    val installedVersionName: String,
    val installedVersionCode: Long,
    val latestVersionName: String?,
    val latestVersionCode: Long?,
    val isUpdating: Boolean = false,
    val updateProgress: MutableState<Float?> = mutableStateOf(null),
) {
    val isUpdateAvailable
        get() = latestVersionCode != null && installedVersionCode < latestVersionCode
}

class AppsListViewModel(
    private val application: Application,
    private val client: HttpClient,
    private val db: LunaDatabase,
) : AndroidViewModel(application) {
    private val _packages = MutableStateFlow<List<Package>?>(null)
    private val _packageInfos = MutableStateFlow<Map<String, PackageInfo>?>(null)

    var lunaPackage: LunaPackageState by mutableStateOf(getInitialLunaPackageState())
        private set

    val packages = combine(
        _packages.filterNotNull(),
        _packageInfos.filterNotNull()
    ) { packages, packageInfos ->
        packages.map {
            val info = packageInfos.get(it.packageName)
            PackageModel(
                it,
                info,
                info?.applicationInfo?.loadIcon(application.packageManager),
            )
        }
    }

    val updatablePackages
        get() = combine(
            _packages.filterNotNull(),
            _packageInfos.filterNotNull()
        ) { packages, packageInfos ->
            packages.filter {
                val packageInfo = packageInfos[it.packageName] ?: return@filter false
                val installedVersionCode = packageInfo.longVersionCodeCompat
                it.latestVersionCode != null && installedVersionCode < it.latestVersionCode
            }
        }

    private var _isCheckingForUpdates by mutableStateOf(false)
    val isCheckingForUpdates @Composable get() = _isCheckingForUpdates

    private var _isUpdating by mutableStateOf(false)
    val isUpdating @Composable get() = _isUpdating

    init {
        viewModelScope.launch {
            launch {
                db.packageDao().getAll().collect {
                    _packages.value = it
                }
            }

            launch {
                _packages.filterNotNull().collect { packages ->
                    updatePackageInfos(packages)
                }
            }

            launch inner@{
                val lunaManifest =
                    getPackageManifest("https://github.com/hasali19/luna/releases/download/latest/luna.apk.json")
                        ?: return@inner
                lunaPackage = lunaPackage.copy(
                    latestVersionName = lunaManifest.info.version,
                    latestVersionCode = lunaManifest.info.versionCode,
                )
            }
        }
    }

    fun refreshInstallStatuses() {
        _packages.value?.let { packages -> updatePackageInfos(packages) }
    }

    fun checkForUpdates() {
        _isCheckingForUpdates = true
        viewModelScope.launch {
            val packageInfos = _packageInfos.value
            val packages =
                _packages.value?.filter { packageInfos?.containsKey(it.packageName) ?: false }
                    ?: return@launch

            packages
                .mapNotNull { pkg -> getPackageManifest(pkg.manifestUrl) }
                .forEach { manifest ->
                    db.packageDao().updateLatestVersion(
                        manifest.info.packageName,
                        manifest.info.version,
                        manifest.info.versionCode,
                    )
                }

            _isCheckingForUpdates = false
        }
    }

    fun updateLuna() {
        lunaPackage = lunaPackage.copy(isUpdating = true, updateProgress = mutableStateOf(null))
        viewModelScope.launch {
            val manifest =
                getPackageManifest("https://github.com/hasali19/luna/releases/download/latest/luna.apk.json")
                    ?: return@launch

            val result = AppInstaller(application).install(manifest) {
                lunaPackage.updateProgress.value = it
            }

            if (result != AppInstaller.InstallationResult.Success) {
                Toast.makeText(application, "Failed to update Luna", Toast.LENGTH_SHORT).show()
            }

            lunaPackage = lunaPackage.copy(isUpdating = false)
        }
    }

    fun updateAll() {
        _isUpdating = true
        viewModelScope.launch {
            for (pkg in updatablePackages.first()) {
                val manifest = getPackageManifest(pkg.manifestUrl) ?: continue
                when (val result = AppInstaller(application).install(manifest) {}) {
                    is AppInstaller.InstallationResult.Success -> {
                        Toast.makeText(
                            application,
                            "Package installed successfully: ${manifest.info.packageName}",
                            Toast.LENGTH_SHORT
                        ).show()
                    }

                    is AppInstaller.InstallationResult.NoCompatiblePackage -> {
                        Toast.makeText(
                            application,
                            "No compatible package found",
                            Toast.LENGTH_SHORT
                        ).show()
                    }

                    is AppInstaller.InstallationResult.UserCanceled -> {
                        Toast.makeText(
                            application,
                            "Installation was canceled",
                            Toast.LENGTH_SHORT
                        ).show()
                    }

                    is AppInstaller.InstallationResult.Failure -> {
                        Toast.makeText(
                            application,
                            "Installation failed: ${result.message}",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }

                refreshInstallStatuses()
            }
            _isUpdating = false
        }
    }

    fun install(pkg: PackageModel) {
        viewModelScope.launch {
            logcat { "Retrieving manifest for ${pkg.pkg.packageName} from ${pkg.pkg.manifestUrl}" }

            val res = client.get(pkg.pkg.manifestUrl)
            val manifest =
                if (res.status.isSuccess()) {
                    res.body<AppManifest>()
                } else {
                    Toast.makeText(
                        application,
                        "Failed to get manifest: ${pkg.pkg.label}",
                        Toast.LENGTH_SHORT
                    ).show()
                    return@launch
                }

            val result = AppInstaller(application).install(manifest) { }

            if (result is AppInstaller.InstallationResult.NoCompatiblePackage) {
                Toast.makeText(
                    application,
                    "No compatible package found",
                    Toast.LENGTH_SHORT
                ).show()
            }

            refreshInstallStatuses()
        }
    }

    private fun getInitialLunaPackageState(): LunaPackageState {
        val packageInfo = application.packageManager.getPackageInfo("dev.hasali.luna", 0)
        return LunaPackageState(
            name = "Luna",
            packageName = "dev.hasali.luna",
            icon = application.packageManager.getApplicationIcon("dev.hasali.luna"),
            installedVersionName = packageInfo.versionName,
            installedVersionCode = packageInfo.longVersionCodeCompat,
            latestVersionName = null,
            latestVersionCode = null,
        )
    }

    private fun updatePackageInfos(packages: List<Package>) {
        _packageInfos.value = packages
            .map {
                runCatching {
                    application.packageManager.getPackageInfo(it.packageName, 0)
                }
            }
            .mapNotNull { it.getOrNull() }
            .associateBy { it.packageName }
    }

    private suspend fun getPackageManifest(url: String): AppManifest? {
        val res = client.get(url)
        return if (res.status.isSuccess()) {
            res.body<AppManifest>()
        } else {
            null
        }
    }
}
