package com.looker.core.common.extension

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.drawable.Drawable
import androidx.annotation.AttrRes
import androidx.annotation.DrawableRes
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import com.looker.core.common.R
import com.looker.core.common.nullIfEmpty

inline val Context.notificationManager: NotificationManager
	get() = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

fun Context.getColorFromAttr(@AttrRes attrResId: Int): ColorStateList {
	val typedArray = obtainStyledAttributes(intArrayOf(attrResId))
	val (colorStateList, resId) = try {
		Pair(typedArray.getColorStateList(0), typedArray.getResourceId(0, 0))
	} finally {
		typedArray.recycle()
	}
	return colorStateList ?: ContextCompat.getColorStateList(this, resId)!!
}

fun Context.getDrawableFromAttr(attrResId: Int): Drawable {
	val typedArray = obtainStyledAttributes(intArrayOf(attrResId))
	val resId = try {
		typedArray.getResourceId(0, 0)
	} finally {
		typedArray.recycle()
	}
	return getDrawableCompat(resId)
}

fun Context.getDrawableCompat(@DrawableRes resId: Int = R.drawable.background_border): Drawable =
	ResourcesCompat.getDrawable(resources, resId, theme) ?: ContextCompat.getDrawable(this, resId)!!

fun Intent.getPackageName(): String? {
	val uri = data
	return when {
		uri?.scheme == "package" || uri?.scheme == "fdroid.app" -> {
			uri.schemeSpecificPart?.nullIfEmpty()
		}
		uri?.scheme == "market" && uri.host == "details" -> {
			uri.getQueryParameter("id")?.nullIfEmpty()
		}
		uri != null && uri.scheme in setOf("http", "https") -> {
			val host = uri.host.orEmpty()
			if (host == "f-droid.org"
				|| host.endsWith(".f-droid.org")
				|| host == "apt.izzysoft.de"
			) {
				uri.lastPathSegment?.nullIfEmpty()
			} else {
				null
			}
		}
		else -> {
			null
		}
	}
}