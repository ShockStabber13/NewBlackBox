package top.niunaijun.blackboxa.view.setting

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import top.niunaijun.blackboxa.R

class InstanceRestorePickerFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        // Use the SAME layout as your workspace picker UI
        return inflater.inflate(R.layout.fragment_instance_picker, container, false)
    }
}
