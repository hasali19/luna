package dev.hasali.luna

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.pm.PackageManager.NameNotFoundException
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dev.hasali.luna.data.LunaDatabase
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.observer.ResponseObserver
import io.ktor.client.request.get
import io.ktor.client.statement.request
import io.ktor.http.ContentType
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import io.ktor.serialization.kotlinx.serialization
import kotlinx.coroutines.flow.first
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import logcat.logcat
import timber.log.Timber

@OptIn(ExperimentalSerializationApi::class)
class BackgroundUpdatesWorker(context: Context, params: WorkerParameters) :
    CoroutineWorker(context, params) {

    private lateinit var client: HttpClient
    private lateinit var db: LunaDatabase
    private lateinit var installer: AppInstaller

    override suspend fun doWork(): Result {
        Timber.i("Starting background updates worker")

        client = HttpClient {
            install(ContentNegotiation) {
                json(Json {
                    ignoreUnknownKeys = true
                    explicitNulls = false
                })

                serialization(ContentType.Application.OctetStream, Json {
                    ignoreUnknownKeys = true
                    explicitNulls = false
                })
            }

            ResponseObserver {
                this@BackgroundUpdatesWorker.logcat { "method=${it.request.method}, url=${it.request.url}, status=${it.status}" }
            }
        }

        db = LunaDatabase.open(applicationContext)
        installer = AppInstaller(applicationContext)

        if (applicationContext.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE == 0) {
            updateSelf()
        }

        updateInstalledPackages()

        return Result.success()
    }

    private suspend fun updateSelf() {
        val info = try {
            applicationContext.packageManager.getPackageInfo("dev.hasali.luna", 0)
        } catch (e: NameNotFoundException) {
            return
        }

        val res =
            client.get("https://github.com/hasali19/luna/releases/download/latest/luna.apk.json")
        val manifest = if (res.status.isSuccess()) {
            res.body<AppManifest>()
        } else {
            return
        }

        if (manifest.info.versionCode > info.longVersionCodeCompat) {
            Timber.i("Updating luna to ${manifest.info.version}+${manifest.info.versionCode}")
            installer.install(manifest)
        }
    }

    private suspend fun updateInstalledPackages() {
        val packages = db.packageDao().getAll().first()
        for (pkg in packages) {
            val info = try {
                applicationContext.packageManager.getPackageInfo(pkg.packageName, 0)
            } catch (e: NameNotFoundException) {
                continue
            }

            val res = client.get(pkg.manifestUrl)
            val manifest = if (res.status.isSuccess()) {
                res.body<AppManifest>()
            } else {
                continue
            }

            db.packageDao().updateLatestVersion(
                manifest.info.packageName,
                manifest.info.version,
                manifest.info.versionCode,
            )

            if (manifest.info.versionCode > info.longVersionCodeCompat) {
                if (!installer.shouldSilentlyUpdatePackage(manifest.info.packageName)) {
                    Timber.i("Skipping background update for ${manifest.info.packageName}")
                    continue
                }

                Timber.i("Updating ${manifest.info.packageName} to ${manifest.info.version}+${manifest.info.versionCode}")

                val result = installer.install(manifest)
                val displayVersion = "${manifest.info.version}+${manifest.info.versionCode}"
                val message = when (result) {
                    is AppInstaller.InstallationResult.Failure -> "Failed to update to $displayVersion"
                    AppInstaller.InstallationResult.NoCompatiblePackage -> "No compatible package found for version $displayVersion"
                    AppInstaller.InstallationResult.Success -> "App was updated to $displayVersion"
                    AppInstaller.InstallationResult.UserCanceled -> continue
                }

                val intent = Intent(applicationContext, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                }

                val pendingIntent = PendingIntent.getActivity(
                    applicationContext,
                    0,
                    intent,
                    PendingIntent.FLAG_IMMUTABLE
                )

                val notification =
                    NotificationCompat.Builder(applicationContext, NotificationChannels.APP_UPDATES)
                        .setContentTitle(manifest.info.name)
                        .setContentText(message)
                        .setSmallIcon(R.drawable.ic_notification_small)
                        .setPriority(NotificationCompat.PRIORITY_LOW)
                        .setContentIntent(pendingIntent)
                        .setAutoCancel(true)
                        .build()

                with(NotificationManagerCompat.from(applicationContext)) {
                    if (ContextCompat.checkSelfPermission(
                            applicationContext,
                            Manifest.permission.POST_NOTIFICATIONS
                        ) == PackageManager.PERMISSION_GRANTED
                    ) {
                        notify(pkg.id, notification)
                    }
                }
            }
        }
    }
}
