package info.kghost.android.openvpn;

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.os.Bundle;
import android.text.method.ScrollingMovementMethod;
import android.widget.TextView;

public class LogDialog extends DialogFragment {
	private LogQueue log;

	public void setLog(LogQueue log) {
		this.log = log;
	}

	@Override
	public void onSaveInstanceState(Bundle outState) {
		super.onSaveInstanceState(outState);
		outState.putParcelable("log", log);
	}

	@Override
	public Dialog onCreateDialog(Bundle savedInstanceState) {
		if (savedInstanceState != null)
			log = savedInstanceState.getParcelable("log");
		final TextView text = new TextView(getActivity());
		text.setPadding(10, 10, 10, 10);
		text.setMovementMethod(new ScrollingMovementMethod());
		text.setTextIsSelectable(true);
		if (log != null)
			for (String s : log) {
				text.append(s + "\n");
			}
		else
			text.append("No Log");
		return new AlertDialog.Builder(getActivity())
				.setTitle(R.string.openvpn_log_dialog_title)
				.setIcon(android.R.drawable.ic_dialog_info).setView(text)
				.create();
	}
}
