package info.kghost.android.openvpn;

import android.app.Activity;
import android.content.Intent;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceClickListener;
import android.widget.Toast;

class FileChooseClickListener implements OnPreferenceClickListener {
	final private Activity mActivity;
	final private int mCode;

	public FileChooseClickListener(Activity activity, int code) {
		mActivity = activity;
		mCode = code;
	}

	@Override
	public boolean onPreferenceClick(Preference preference) {
		Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
		intent.setType("*/*");
		intent.addCategory(Intent.CATEGORY_OPENABLE);

		try {
			mActivity.startActivityForResult(
					Intent.createChooser(intent, "Select certification file"),
					mCode);
		} catch (android.content.ActivityNotFoundException ex) {
			// Potentially direct the user to the Market with a Dialog
			Toast.makeText(mActivity, "Please install a File Manager.",
					Toast.LENGTH_SHORT).show();
		}
		return false;
	}
}