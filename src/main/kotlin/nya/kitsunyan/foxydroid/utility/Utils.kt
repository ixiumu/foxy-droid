package nya.kitsunyan.foxydroid.utility

import android.animation.ValueAnimator
import android.app.Activity
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.Signature
import android.content.res.Configuration
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.os.LocaleList
import android.provider.Settings
import android.util.AndroidRuntimeException
import android.util.Log
import android.widget.Toast
import com.topjohnwu.superuser.Shell
import nya.kitsunyan.foxydroid.BuildConfig
import nya.kitsunyan.foxydroid.R
import nya.kitsunyan.foxydroid.content.Cache
import nya.kitsunyan.foxydroid.content.Preferences
import nya.kitsunyan.foxydroid.entity.InstalledItem
import nya.kitsunyan.foxydroid.entity.Product
import nya.kitsunyan.foxydroid.entity.Repository
import nya.kitsunyan.foxydroid.service.Connection
import nya.kitsunyan.foxydroid.service.DownloadService
import nya.kitsunyan.foxydroid.utility.extension.android.*
import nya.kitsunyan.foxydroid.utility.extension.resources.*
import nya.kitsunyan.foxydroid.utility.extension.text.*
import java.io.File
import java.security.MessageDigest
import java.security.cert.Certificate
import java.security.cert.CertificateEncodingException
import java.util.Locale

object Utils {
  private fun createDefaultApplicationIcon(context: Context, tintAttrResId: Int): Drawable {
    return context.getDrawableCompat(R.drawable.ic_application_default).mutate()
      .apply { setTintList(context.getColorFromAttr(tintAttrResId)) }
  }

  fun getDefaultApplicationIcons(context: Context): Pair<Drawable, Drawable> {
    val progressIcon: Drawable = createDefaultApplicationIcon(context, android.R.attr.textColorSecondary)
    val defaultIcon: Drawable = createDefaultApplicationIcon(context, android.R.attr.colorAccent)
    return progressIcon to defaultIcon
  }

  fun getToolbarIcon(context: Context, resId: Int): Drawable {
    val drawable = context.getDrawableCompat(resId).mutate()
    drawable.setTintList(context.getColorFromAttr(android.R.attr.textColorPrimary))
    return drawable
  }

  fun calculateHash(signature: Signature): String? {
    return MessageDigest.getInstance("MD5").digest(signature.toCharsString().toByteArray()).hex()
  }

  fun calculateFingerprint(certificate: Certificate): String {
    val encoded = try {
      certificate.encoded
    } catch (e: CertificateEncodingException) {
      null
    }
    return encoded?.let(::calculateFingerprint).orEmpty()
  }

  fun calculateFingerprint(key: ByteArray): String {
    return if (key.size >= 256) {
      try {
        val fingerprint = MessageDigest.getInstance("SHA-256").digest(key)
        val builder = StringBuilder()
        for (byte in fingerprint) {
          builder.append("%02X".format(Locale.US, byte.toInt() and 0xff))
        }
        builder.toString()
      } catch (e: Exception) {
        e.printStackTrace()
        ""
      }
    } else {
      ""
    }
  }

  fun configureLocale(context: Context): Context {
    val supportedLanguages = BuildConfig.LANGUAGES.toSet()
    val configuration = context.resources.configuration
    val currentLocales = if (Android.sdk(24)) {
      val localesList = configuration.locales
      (0 until localesList.size()).map(localesList::get)
    } else {
      @Suppress("DEPRECATION")
      listOf(configuration.locale)
    }
    val compatibleLocales = currentLocales
      .filter { it.language in supportedLanguages }
      .let { if (it.isEmpty()) listOf(Locale.US) else it }
    Locale.setDefault(compatibleLocales.first())
    val newConfiguration = Configuration(configuration)
    if (Android.sdk(24)) {
      newConfiguration.setLocales(LocaleList(*compatibleLocales.toTypedArray()))
    } else {
      @Suppress("DEPRECATION")
      newConfiguration.locale = compatibleLocales.first()
    }
    return context.createConfigurationContext(newConfiguration)
  }

  fun areAnimationsEnabled(context: Context): Boolean {
    return if (Android.sdk(26)) {
      ValueAnimator.areAnimatorsEnabled()
    } else {
      Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) != 0f
    }
  }

  fun quote(string: String) = "\"${string.replace(Regex("""[\\$"`]""")) { c -> "\\${c.value}" }}\""

  @Suppress("DEPRECATION")
  internal fun Activity.startPackageInstaller(cacheFileName: String) {
    if (Preferences[Preferences.Key.RootInstallation] && Shell.getShell().isRoot) {
      val releaseFile = Cache.getReleaseFile(this, cacheFileName)
      val commandBuilder = StringBuilder()
      // disable verify apps over usb
      commandBuilder.append("settings put global verifier_verify_adb_installs 0 ; ")
      // Install main package
      commandBuilder.append(getPackageInstallCommand(releaseFile))
      // re-enable verify apps over usb after install
      commandBuilder.append(" ; settings put global verifier_verify_adb_installs 1")
      val result = Shell.cmd(commandBuilder.toString()).exec()
      if (result.isSuccess) Shell.cmd("${getUtilBoxPath()} rm ${quote(releaseFile.absolutePath)}")
    } else {
      val (uri, flags) = if (Android.sdk(24)) {
        Cache.getReleaseUri(this, cacheFileName) to Intent.FLAG_GRANT_READ_URI_PERMISSION
      } else {
        Uri.fromFile(Cache.getReleaseFile(this, cacheFileName)) to 0
      }

      try {
        startActivity(
          Intent(Intent.ACTION_INSTALL_PACKAGE)
            .setDataAndType(uri, "application/vnd.android.package-archive").setFlags(flags)
        )
      } catch (e: AndroidRuntimeException) {
        startActivity(
          Intent(Intent.ACTION_INSTALL_PACKAGE)
            .setDataAndType(uri, "application/vnd.android.package-archive").setFlags(flags or Intent.FLAG_ACTIVITY_NEW_TASK)
        )
      } catch (e: Exception) {
        Toast.makeText(this, getString(R.string.invalid_permissions_error_DESC), Toast.LENGTH_SHORT).show()
      }
    }
  }

  @Suppress("DEPRECATION")
  fun Context.getPackageInfo(i: Int): PackageInfo {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      packageManager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(i.toLong()))
    } else {
      packageManager.getPackageInfo(packageName, i)
    }
  }

  private fun getPackageInstallCommand(apkPath: File): String =
    "cat \"${apkPath.absolutePath}\" | pm install -t -r -S ${apkPath.length()}"

  private fun getUtilBoxPath(): String {
    listOf("toybox", "busybox").forEach {
      var shellResult = Shell.cmd("which $it").exec()
      if (shellResult.out.isNotEmpty()) {
        val utilBoxPath = shellResult.out.joinToString("")
        if (utilBoxPath.isNotEmpty()) {
          val utilBoxQuoted = quote(utilBoxPath)
          shellResult = Shell.cmd("$utilBoxQuoted --version").exec()
          if (shellResult.out.isNotEmpty()) {
            val utilBoxVersion = shellResult.out.joinToString("")
            Log.i(this.javaClass.canonicalName,"Using Utilbox $it : $utilBoxQuoted $utilBoxVersion")
          }
          return utilBoxQuoted
        }
      }
    }
    return ""
  }

  fun PackageInfo.toInstalledItem(): InstalledItem {
    val signatureString = singleSignature?.let(Utils::calculateHash).orEmpty()
    return InstalledItem(packageName, versionName.orEmpty(), versionCodeCompat, signatureString)
  }

  fun startInstallUpdateAction(
    packageName: String,
    installedItem: InstalledItem?,
    products: List<Pair<Product, Repository>>,
    downloadConnection: Connection<DownloadService.Binder, DownloadService>
  ) {
    val pairProductRepository = Product.findSuggested(products, installedItem) { it.first }
    val compatibleReleases = pairProductRepository?.first?.selectedReleases.orEmpty()
      .filter { installedItem == null || installedItem.signature == it.signature }
    val release = if (compatibleReleases.size >= 2) {
      compatibleReleases
        .filter { it.platforms.contains(Android.primaryPlatform) }
        .minByOrNull { it.platforms.size }
        ?: compatibleReleases.minByOrNull { it.platforms.size }
        ?: compatibleReleases.firstOrNull()
    } else {
      compatibleReleases.firstOrNull()
    }
    val binder = downloadConnection.binder
    if (pairProductRepository != null && release != null && binder != null) {
      binder.enqueue(packageName, pairProductRepository.first.name, pairProductRepository.second, release)
    }
  }

  // fix android 13 crash
  fun getPendingIntentFlag(): Int {
    return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
      PendingIntent.FLAG_IMMUTABLE
    } else {
      PendingIntent.FLAG_UPDATE_CURRENT
    }
  }
}
