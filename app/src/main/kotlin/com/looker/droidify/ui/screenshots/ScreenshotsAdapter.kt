package com.looker.droidify.ui.screenshots

import android.content.Context
import android.graphics.drawable.Drawable
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.core.graphics.drawable.toBitmap
import androidx.core.graphics.drawable.toDrawable
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.google.android.material.imageview.ShapeableImageView
import com.looker.core.common.extension.getColorFromAttr
import com.looker.core.common.extension.getDrawableCompat
import com.looker.core.model.Product
import com.looker.core.model.Repository
import com.looker.droidify.graphics.PaddingDrawable
import com.looker.droidify.utility.extension.resources.sizeScaled
import com.looker.droidify.utility.extension.url
import com.looker.droidify.widget.StableRecyclerAdapter
import com.google.android.material.R as MaterialR
import com.looker.core.common.R as CommonR
import com.looker.core.common.R.dimen as dimenRes

class ScreenshotsAdapter(private val onClick: (Product.Screenshot) -> Unit) :
	StableRecyclerAdapter<ScreenshotsAdapter.ViewType, RecyclerView.ViewHolder>() {
	enum class ViewType { SCREENSHOT }

	private val items = mutableListOf<Item.ScreenshotItem>()

	private class ViewHolder(context: Context) :
		RecyclerView.ViewHolder(FrameLayout(context)) {
		val image: ShapeableImageView
		val placeholder: Drawable

		init {
			itemView as FrameLayout
			val surfaceColor =
				itemView.context.getColorFromAttr(MaterialR.attr.colorSurface).defaultColor

			image = object : ShapeableImageView(context) {
				override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
					super.onMeasure(widthMeasureSpec, heightMeasureSpec)
					setMeasuredDimension(measuredWidth, measuredHeight)
				}
			}

			val radius = image.context.resources.getDimension(dimenRes.shape_small_corner)
			val shapeAppearanceModel = image.shapeAppearanceModel.toBuilder()
				.setAllCornerSizes(radius)
				.build()
			image.shapeAppearanceModel = shapeAppearanceModel
			itemView.addView(image)
			itemView.layoutParams = RecyclerView.LayoutParams(
				RecyclerView.LayoutParams.WRAP_CONTENT,
				RecyclerView.LayoutParams.MATCH_PARENT
			).apply {
				marginStart =
					image.context.resources.getDimension(dimenRes.shape_small_corner).toInt()
				marginEnd =
					image.context.resources.getDimension(dimenRes.shape_small_corner).toInt()
			}

			val placeholder =
				image.context.getDrawableCompat(CommonR.drawable.ic_screenshot_placeholder).mutate()
			placeholder.setTint(surfaceColor)
			this.placeholder = PaddingDrawable(placeholder, 2f)
		}
	}

	fun setScreenshots(
		repository: Repository,
		packageName: String,
		screenshots: List<Product.Screenshot>
	) {
		items.clear()
		items += screenshots.map { Item.ScreenshotItem(repository, packageName, it) }
		notifyItemRangeInserted(0, screenshots.size)
	}

	override val viewTypeClass: Class<ViewType>
		get() = ViewType::class.java

	override fun getItemEnumViewType(position: Int): ViewType {
		return ViewType.SCREENSHOT
	}

	override fun onCreateViewHolder(
		parent: ViewGroup,
		viewType: ViewType
	): RecyclerView.ViewHolder {
		return ViewHolder(parent.context).apply {
			itemView.setOnClickListener { onClick(items[absoluteAdapterPosition].screenshot) }
		}
	}

	override fun getItemDescriptor(position: Int): String = items[position].descriptor
	override fun getItemCount(): Int = items.size

	override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
		val context = holder.itemView.context
		holder as ViewHolder
		val item = items[position]
		val screenWidth = context.resources.displayMetrics.widthPixels
		val outer = context.resources.sizeScaled(GRID_SPACING_OUTER_DP)
		val inner = context.resources.sizeScaled(GRID_SPACING_INNER_DP)
		val cellSize = (screenWidth - outer - inner) / 1.5
		holder.image.load(
			item.screenshot.url(item.repository, item.packageName)
		) {
			placeholder(
				PaddingDrawable(
					holder.placeholder.mutate()
						.toBitmap(height = cellSize.toInt(), width = cellSize.toInt() / 4)
						.toDrawable(context.resources), 1f
				)
			)
			error(holder.placeholder)
			size(cellSize.toInt())
		}
	}

	companion object {
		private const val GRID_SPACING_OUTER_DP = 16
		private const val GRID_SPACING_INNER_DP = 8
	}

	private sealed class Item {
		abstract val descriptor: String

		class ScreenshotItem(
			val repository: Repository,
			val packageName: String,
			val screenshot: Product.Screenshot,
		) : Item() {
			override val descriptor: String
				get() = "screenshot.${repository.id}.${screenshot.identifier}"
		}
	}
}
