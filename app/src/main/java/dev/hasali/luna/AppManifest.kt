package dev.hasali.luna

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class AppManifest(
    val version: Version,
    val info: AppInfo,
    val packages: List<Package>,
) {
    @Serializable
    enum class Version {
        @SerialName("1")
        V1
    }

    @Serializable
    data class AppInfo(
        val name: String,
        val author: String,
        val icon: String?,
        val url: String?,
        val version: String,
        val versionCode: Long,
        val packageName: String,
    )

    @Serializable
    data class Package(
        val name: String,
        val uri: String,
        val abi: String?,
    )
}
