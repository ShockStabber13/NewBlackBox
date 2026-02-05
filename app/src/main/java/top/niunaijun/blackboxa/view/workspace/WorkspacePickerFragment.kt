package top.niunaijun.blackboxa.view.workspace

import android.app.AlertDialog
import android.os.Bundle
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import top.niunaijun.blackboxa.R
import top.niunaijun.blackboxa.databinding.FragmentWorkspacePickerBinding
import top.niunaijun.blackboxa.util.WorkspaceUtil
import top.niunaijun.blackboxa.util.toast
import java.io.File

class WorkspacePickerFragment : Fragment() {

    private var _binding: FragmentWorkspacePickerBinding? = null
    private val binding get() = _binding!!

    private lateinit var adapter: FolderAdapter
    private var currentDir: File = File("/storage/emulated/0")

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentWorkspacePickerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = FolderAdapter { file ->
            if (file.isDirectory) {
                setDir(file)
            }
        }

        binding.recycler.layoutManager = LinearLayoutManager(requireContext())
        binding.recycler.adapter = adapter

        binding.btnUp.setOnClickListener {
            val parent = currentDir.parentFile
            if (parent != null && parent.exists() && parent.canRead()) {
                setDir(parent)
            }
        }

        binding.btnSelect.setOnClickListener {
            val path = currentDir.absolutePath
            WorkspaceUtil.setWorkspacePath(path)
            requireContext().toast(getString(R.string.workspace_folder) + ": " + path)
            requireActivity().finish()
        }

        binding.btnNewFolder.setOnClickListener {
            showCreateFolderDialog()
        }

        // Start directory: previously selected workspace (if still valid), else internal storage root.
        val saved = WorkspaceUtil.getWorkspacePath()
        val start = if (saved.isNotBlank()) File(saved) else currentDir
        setDir(if (start.exists() && start.isDirectory && start.canRead()) start else currentDir)
    }

    private fun setDir(dir: File) {
        currentDir = dir
        binding.txtPath.text = dir.absolutePath
        val items = listFolders(dir)
        adapter.submit(items)
        binding.btnUp.isEnabled = dir.parentFile != null
    }

    private fun listFolders(dir: File): List<File> {
        return try {
            val raw = dir.listFiles { f -> f.isDirectory }?.toList().orEmpty()
            adapter.sortFolders(raw)
        } catch (_: Throwable) {
            toast(R.string.invalid_folder)
            emptyList()
        }
    }

    private fun showCreateFolderDialog() {
        val v = layoutInflater.inflate(R.layout.dialog_create_folder, null, false)
        val et = v.findViewById<EditText>(R.id.etName)
        val btnCancel = v.findViewById<View>(R.id.btnCancel)
        val btnCreate = v.findViewById<View>(R.id.btnCreate)

        val dialog = AlertDialog.Builder(requireContext())
            .setView(v)
            .create()

        dialog.setOnShowListener {
            // black background + gold border (your drawable)
            dialog.window?.setBackgroundDrawableResource(R.drawable.bg_dialog_gold)

            btnCancel.setOnClickListener { dialog.dismiss() }

            btnCreate.setOnClickListener {
                val name = et.text?.toString()?.trim().orEmpty()
                if (name.isNotEmpty()) {
                    try {
                        val f = File(currentDir, name)
                        if (!f.exists()) f.mkdirs()
                        setDir(currentDir)
                    } catch (_: Throwable) {
                        toast(R.string.invalid_folder)
                    }
                }
                dialog.dismiss()
            }
        }

        dialog.show()
    }


    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
