package nya.kitsunyan.foxydroid.content

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import io.reactivex.rxjava3.schedulers.Schedulers
import io.reactivex.rxjava3.subjects.PublishSubject
import nya.kitsunyan.foxydroid.database.Database
import nya.kitsunyan.foxydroid.entity.ProductPreference
import nya.kitsunyan.foxydroid.utility.extension.json.*
import java.io.ByteArrayOutputStream
import java.nio.charset.Charset

object ProductPreferences {
  private val defaultProductPreference = ProductPreference(false, 0L)
  private lateinit var preferences: SharedPreferences
  private val subject = PublishSubject.create<Pair<String, Long?>>()

  @SuppressLint("CheckResult")
  fun init(context: Context) {
    preferences = context.getSharedPreferences("product_preferences", Context.MODE_PRIVATE)
    Database.LockAdapter.putAll(preferences.all.keys
      .mapNotNull { packageName -> this[packageName].databaseVersionCode?.let { packageName to it } })
    subject
      .observeOn(Schedulers.io())
      .subscribe { (packageName, versionCode) ->
        if (versionCode != null) {
          Database.LockAdapter.put(packageName to versionCode)
        } else {
          Database.LockAdapter.delete(packageName)
        }
      }
  }

  private val ProductPreference.databaseVersionCode: Long?
    get() = when {
      ignoreUpdates -> 0L
      ignoreVersionCode > 0L -> ignoreVersionCode
      else -> null
    }

  operator fun get(packageName: String): ProductPreference {
    return if (preferences.contains(packageName)) {
      try {
        Json.factory.createParser(preferences.getString(packageName, "{}"))
          .use { it.parseDictionary(ProductPreference.Companion::deserialize) }
      } catch (e: Exception) {
        e.printStackTrace()
        defaultProductPreference
      }
    } else {
      defaultProductPreference
    }
  }

  operator fun set(packageName: String, productPreference: ProductPreference) {
    val oldProductPreference = this[packageName]
    preferences.edit().putString(packageName, ByteArrayOutputStream()
      .apply { Json.factory.createGenerator(this).use { it.writeDictionary(productPreference::serialize) } }
      .toByteArray().toString(Charset.defaultCharset())).apply()
    if (oldProductPreference.ignoreUpdates != productPreference.ignoreUpdates ||
      oldProductPreference.ignoreVersionCode != productPreference.ignoreVersionCode) {
      subject.onNext(packageName to productPreference.databaseVersionCode)
    }
  }
}
