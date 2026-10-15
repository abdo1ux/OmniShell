package io.github.abdo1ux.adapter

import android.graphics.drawable.Drawable
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import io.github.abdo1ux.databinding.ItemPackageBinding

data class AppModel(
    val appName: String,
    val packageName: String,
    val icon: Drawable?,
    val isSystemApp: Boolean
)

class AppAdapter(
    private val onItemClick: (AppModel) -> Unit,
    private val onForceStopClick: (AppModel) -> Unit
) : ListAdapter<AppModel, AppAdapter.AppViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AppViewHolder {
        val binding = ItemPackageBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return AppViewHolder(binding)
    }

    override fun onBindViewHolder(holder: AppViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class AppViewHolder(private val binding: ItemPackageBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: AppModel) {
            binding.tvAppName.text = item.appName
            binding.tvPackageName.text = item.packageName
            if (item.icon != null) {
                binding.ivAppIcon.setImageDrawable(item.icon)
            }
            binding.tvAppType.text = if (item.isSystemApp) "SYSTEM" else "USER"

            binding.root.setOnClickListener { onItemClick(item) }
            binding.btnForceStop.setOnClickListener { onForceStopClick(item) }
        }
    }

    companion object DiffCallback : DiffUtil.ItemCallback<AppModel>() {
        override fun areItemsTheSame(oldItem: AppModel, newItem: AppModel): Boolean {
            return oldItem.packageName == newItem.packageName
        }

        override fun areContentsTheSame(oldItem: AppModel, newItem: AppModel): Boolean {
            return oldItem == newItem
        }
    }
}
