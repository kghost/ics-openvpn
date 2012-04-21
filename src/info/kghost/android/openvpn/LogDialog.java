package info.kghost.android.openvpn;

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.content.Context;
import android.os.Bundle;
import android.text.method.ScrollingMovementMethod;
import android.widget.TextView;

public class LogDialog extends DialogFragment {

	private final Context context;
	private final LogQueue log;

	public LogDialog(Context c, LogQueue l) {
		context = c;
		log = l;
	}

	@Override
	public Dialog onCreateDialog(Bundle savedInstanceState) {
		final TextView text = new TextView(context);
		text.setPadding(10, 10, 10, 10);
		text.setMovementMethod(new ScrollingMovementMethod());
		text.setTextIsSelectable(true);
		if (log != null)
			for (String s : log) {
				text.append(s + "\n");
			}
		else
			text.append("No Log");
		return new AlertDialog.Builder(context)
				.setTitle(R.string.openvpn_log_dialog_title)
				.setIcon(android.R.drawable.ic_dialog_info).setView(text)
				.create();
	}
}
