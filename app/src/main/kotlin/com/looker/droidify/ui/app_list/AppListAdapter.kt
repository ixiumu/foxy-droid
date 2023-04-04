package com.looker.droidify.ui.app_list

import android.content.Context
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.google.android.material.imageview.ShapeableImageView
import com.google.android.material.progressindicator.CircularProgressIndicator
import com.looker.core.common.extension.getColorFromAttr
import com.looker.core.common.extension.getDrawableCompat
import com.looker.core.common.extension.inflate
import com.looker.core.common.extension.setTextSizeScaled
import com.looker.core.common.nullIfEmpty
import com.looker.core.model.ProductItem
import com.looker.core.model.Repository
import com.looker.droidify.R
import com.looker.droidify.database.Database
import com.looker.droidify.utility.extension.icon
import com.looker.droidify.utility.extension.resources.TypefaceExtra
import com.looker.droidify.utility.extension.resources.sizeScaled
import com.looker.droidify.widget.CursorRecyclerAdapter
import com.google.android.material.R as MaterialR

class AppListAdapter(
	private val source: AppListFragment.Source,
	private val onClick: (ProductItem) -> Unit
) :
	CursorRecyclerAdapter<AppListAdapter.ViewType, RecyclerView.ViewHolder>() {

	enum class ViewType { PRODUCT, LOADING, EMPTY }

	private class ProductViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
		val name = itemView.findViewById<TextView>(R.id.name)!!
		val status = itemView.findViewById<TextView>(R.id.status)!!
		val summary = itemView.findViewById<TextView>(R.id.summary)!!
		val icon = itemView.findViewById<ShapeableImageView>(R.id.icon)!!
	}

	private class LoadingViewHolder(context: Context) :
		RecyclerView.ViewHolder(FrameLayout(context)) {
		init {
			itemView as FrameLayout
			val progressBar = CircularProgressIndicator(itemView.context)
			itemView.addView(progressBar)
			itemView.layoutParams = RecyclerView.LayoutParams(
				RecyclerView.LayoutParams.MATCH_PARENT,
				RecyclerView.LayoutParams.MATCH_PARENT
			)
		}
	}

	private class EmptyViewHolder(context: Context) :
		RecyclerView.ViewHolder(TextView(context)) {
		val text: TextView
			get() = itemView as TextView

		init {
			itemView as TextView
			itemView.gravity = Gravity.CENTER
			itemView.resources.sizeScaled(20).let { itemView.setPadding(it, it, it, it) }
			itemView.typeface = TypefaceExtra.light
			itemView.setTextColor(context.getColorFromAttr(android.R.attr.colorPrimary))
			itemView.setTextSizeScaled(20)
			itemView.layoutParams = RecyclerView.LayoutParams(
				RecyclerView.LayoutParams.MATCH_PARENT,
				RecyclerView.LayoutParams.MATCH_PARENT
			)
		}
	}

	var repositories: Map<Long, Repository> = emptyMap()
		set(value) {
			field = value
			notifyDataSetChanged()
		}

	var emptyText: String = ""
		set(value) {
			if (field != value) {
				field = value
				if (isEmpty) {
					notifyDataSetChanged()
				}
			}
		}

	override val viewTypeClass: Class<ViewType>
		get() = ViewType::class.java

	private val isEmpty: Boolean
		get() = super.getItemCount() == 0

	override fun getItemCount(): Int = if (isEmpty) 1 else super.getItemCount()
	override fun getItemId(position: Int): Long = if (isEmpty) -1 else super.getItemId(position)

	override fun getItemEnumViewType(position: Int): ViewType {
		return when {
			!isEmpty -> ViewType.PRODUCT
			cursor == null -> ViewType.LOADING
			else -> ViewType.EMPTY
		}
	}

	private fun getProductItem(position: Int): ProductItem {
		return Database.ProductAdapter.transformItem(moveTo(position))
	}

	override fun onCreateViewHolder(
		parent: ViewGroup,
		viewType: ViewType,
	): RecyclerView.ViewHolder {
		return when (viewType) {
			ViewType.PRODUCT -> ProductViewHolder(parent.inflate(R.layout.product_item)).apply {
				itemView.setOnClickListener { onClick(getProductItem(absoluteAdapterPosition)) }
			}
			ViewType.LOADING -> LoadingViewHolder(parent.context)
			ViewType.EMPTY -> EmptyViewHolder(parent.context)
		}
	}

	override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
		when (getItemEnumViewType(position)) {
			ViewType.PRODUCT -> {
				holder as ProductViewHolder
				val productItem = getProductItem(if (position > -1) position else 0)
				holder.name.text = productItem.name
				holder.summary.text = productItem.summary
				holder.summary.isVisible =
					productItem.summary.isNotEmpty() && productItem.name != productItem.summary
				val repository: Repository? = repositories[productItem.repositoryId]
				repository?.let {
					holder.icon.load(
						productItem.packageName.icon(
							view = holder.icon,
							icon = productItem.icon,
							metadataIcon = productItem.metadataIcon,
							repository = it
						)
					)
				}
				holder.status.apply {
					val versionText = if (source == AppListFragment.Source.UPDATES) {
						productItem.version
					} else productItem.installedVersion.nullIfEmpty() ?: productItem.version
					text = versionText
					val isInstalled = productItem.installedVersion.nullIfEmpty() != null
					when {
						productItem.canUpdate -> {
							backgroundTintList =
								context.getColorFromAttr(MaterialR.attr.colorSecondaryContainer)
							setTextColor(context.getColorFromAttr(MaterialR.attr.colorOnSecondaryContainer))
						}
						isInstalled -> {
							backgroundTintList =
								context.getColorFromAttr(MaterialR.attr.colorPrimaryContainer)
							setTextColor(context.getColorFromAttr(MaterialR.attr.colorOnPrimaryContainer))
						}
						else -> {
							setPadding(0, 0, 0, 0)
							setTextColor(holder.status.context.getColorFromAttr(MaterialR.attr.colorOnBackground))
							background = null
							return@apply
						}
					}
					background = context.getDrawableCompat()
					resources.sizeScaled(6).let { setPadding(it, it, it, it) }
				}
				val enabled = productItem.compatible || productItem.installedVersion.isNotEmpty()
				sequenceOf(holder.name, holder.status, holder.summary).forEach {
					it.isEnabled = enabled
				}
			}
			ViewType.LOADING -> {
				// Do nothing
			}
			ViewType.EMPTY -> {
				holder as EmptyViewHolder
				holder.text.text = emptyText
			}
		}::class
	}
}