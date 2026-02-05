package top.niunaijun.blackboxa.view.workspace

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import top.niunaijun.blackboxa.databinding.ItemFolderBinding
import java.io.File
import java.text.Collator

class FolderAdapter(
    private val onClick: (File) -> Unit
) : ListAdapter<File, FolderAdapter.VH>(DIFF) {

    private val collator: Collator = Collator.getInstance().apply {
        strength = Collator.PRIMARY
    }

    fun sortFolders(folders: List<File>): List<File> {
        return folders.sortedWith { a, b -> collator.compare(a.name, b.name) }
    }

    fun submit(folders: List<File>) {
        submitList(folders)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemFolderBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.bind(getItem(position))
    }

    inner class VH(private val binding: ItemFolderBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(file: File) {
            binding.txtName.text = file.name
            binding.root.setOnClickListener { onClick(file) }
        }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<File>() {
            override fun areItemsTheSame(oldItem: File, newItem: File): Boolean =
                oldItem.absolutePath == newItem.absolutePath

            override fun areContentsTheSame(oldItem: File, newItem: File): Boolean =
                oldItem.name == newItem.name
        }
    }
}
