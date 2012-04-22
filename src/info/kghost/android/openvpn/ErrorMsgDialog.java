package info.kghost.android.openvpn;

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.os.Bundle;

public class ErrorMsgDialog extends DialogFragment {
	private String msg;

	public void setMessage(String message) {
		msg = message;
	}

	@Override
	public Dialog onCreateDialog(Bundle savedInstanceState) {
		this.setCancelable(false);
		return new AlertDialog.Builder(getActivity())
				.setTitle(R.string.openvpn_install_error_title).setMessage(msg)
				.setPositiveButton(android.R.string.ok, new OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int which) {
						getActivity().finish();
					}
				}).setCancelable(false).create();
	}
}
