package top.niunaijun.blackboxa.util

import android.app.Activity
import android.app.Dialog
import android.view.LayoutInflater
import android.view.Window
import android.widget.TextView
import top.niunaijun.blackboxa.R

class GoldLoadingDialog(private val activity: Activity) {
    private var dialog: Dialog? = null

    fun show(message: String) {
        if (activity.isFinishing) return
        if (dialog?.isShowing == true) {
            dialog?.findViewById<TextView>(R.id.tvMessage)?.text = message
            return
        }

        val view = LayoutInflater.from(activity).inflate(R.layout.dialog_gold_loading, null, false)
        view.findViewById<TextView>(R.id.tvMessage).text = message

        dialog = Dialog(activity).apply {
            requestWindowFeature(Window.FEATURE_NO_TITLE)
            setContentView(view)
            setCancelable(false)
            setCanceledOnTouchOutside(false)
            window?.setBackgroundDrawableResource(android.R.color.transparent)
            show()
        }
    }

    fun dismiss() {
        dialog?.dismiss()
        dialog = null
    }
}
