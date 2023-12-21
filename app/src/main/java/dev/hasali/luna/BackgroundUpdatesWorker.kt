package dev.hasali.luna

import android.content.Context
import android.content.pm.PackageManager.NameNotFoundException
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

@OptIn(ExperimentalSerializationApi::class)
class BackgroundUpdatesWorker(context: Context, params: WorkerParameters) :
    CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        logcat { "Starting background updates worker" }

        val db = LunaDatabase.open(applicationContext)
        val client = HttpClient {
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

            if (manifest.info.versionCode > info.longVersionCodeCompat) {
                AppInstaller(applicationContext).install(manifest)
            }
        }

        return Result.success()
    }
}
