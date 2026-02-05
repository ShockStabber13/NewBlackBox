package top.niunaijun.blackboxa.view.workspace

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import top.niunaijun.blackboxa.R

class WorkspacePickerActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_workspace_picker)

        if (savedInstanceState == null) {
            supportFragmentManager
                .beginTransaction()
                .replace(R.id.fragment_container, WorkspacePickerFragment())
                .commit()
        }
    }
}
