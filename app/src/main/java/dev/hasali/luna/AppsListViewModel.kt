package dev.hasali.luna

import android.app.Application
import android.content.pm.PackageInfo
import android.graphics.drawable.Drawable
import android.os.Build
import android.widget.Toast
import androidx.compose.runtime.Composable
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
    val manifest: AppManifest?,
)

private val DEFAULT_PACKAGES = listOf(
    Package(
        id = -1,
        label = "Luna",
        packageName = "dev.hasali.luna",
        manifestUrl = "https://github.com/hasali19/luna",
    ),
)

class AppsListViewModel(
    private val application: Application,
    private val client: HttpClient,
    private val db: LunaDatabase,
) : AndroidViewModel(application) {
    private val _packages = MutableStateFlow<List<Package>?>(null)
    private val _packageInfos = MutableStateFlow<Map<String, PackageInfo>?>(null)
    private val _manifests = MutableStateFlow<Map<String, AppManifest>?>(null)

    val packages = combine(
        _packages.filterNotNull(),
        _packageInfos.filterNotNull(),
        _manifests
    ) { packages, packageInfos, manifests ->
        packages.map {
            val info = packageInfos.get(it.packageName)
            PackageModel(
                it,
                info,
                info?.applicationInfo?.loadIcon(application.packageManager),
                manifests?.get(it.packageName),
            )
        }
    }

    val updatedManifests
        get() = combine(
            _manifests.filterNotNull(),
            _packageInfos.filterNotNull()
        ) { manifests, packageInfos ->
            manifests
                .filter {
                    val packageInfo = packageInfos[it.key] ?: return@filter false
                    val installedVersionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        packageInfo.longVersionCode
                    } else {
                        @Suppress("DEPRECATION")
                        packageInfo.versionCode.toLong()
                    }
                    installedVersionCode < it.value.info.versionCode
                }
                .toMap()
        }

    private var _isCheckingForUpdates by mutableStateOf(false)
    val isCheckingForUpdates @Composable get() = _isCheckingForUpdates

    private var _isUpdating by mutableStateOf(false)
    val isUpdating @Composable get() = _isUpdating

    init {
        viewModelScope.launch {
            launch {
                db.packageDao().getAll().collect {
                    _packages.value = DEFAULT_PACKAGES + it
                }
            }

            launch {
                _packages.filterNotNull().collect { packages ->
                    _updatePackageInfos(packages)
                }
            }
        }
    }

    fun refreshInstallStatuses() {
        _packages.value?.let { packages -> _updatePackageInfos(packages) }
    }

    fun checkForUpdates() {
        _isCheckingForUpdates = true
        viewModelScope.launch {
            val packageInfos = _packageInfos.value
            val packages =
                _packages.value?.filter { packageInfos?.containsKey(it.packageName) ?: false }

            _manifests.value = packages!!
                .mapNotNull { pkg ->
                    val res = client.get(pkg.manifestUrl)
                    if (res.status.isSuccess()) {
                        res.body<AppManifest>()
                    } else {
                        null
                    }
                }
                .associateBy { it.info.packageName }

            _isCheckingForUpdates = false
        }
    }

    fun updateAll() {
        _isUpdating = true
        viewModelScope.launch {
            for ((_, manifest) in updatedManifests.first()) {
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

    private fun _updatePackageInfos(packages: List<Package>) {
        _packageInfos.value = packages
            .map {
                runCatching {
                    application.packageManager.getPackageInfo(it.packageName, 0)
                }
            }
            .mapNotNull { it.getOrNull() }
            .associateBy { it.packageName }
    }
}
