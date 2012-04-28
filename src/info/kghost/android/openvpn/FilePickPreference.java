package info.kghost.android.openvpn;

import java.lang.reflect.Field;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.preference.PreferenceManager;
import android.preference.RingtonePreference;
import android.util.AttributeSet;

public class FilePickPreference extends RingtonePreference implements
		PreferenceManager.OnActivityResultListener {
	public FilePickPreference(Context context, AttributeSet attrs, int defStyle) {
		super(context, attrs, defStyle);
	}

	public FilePickPreference(Context context, AttributeSet attrs) {
		super(context, attrs);
	}

	public FilePickPreference(Context context) {
		super(context);
	}

	@Override
	protected void onPrepareRingtonePickerIntent(Intent intent) {
		Intent target = new Intent(Intent.ACTION_GET_CONTENT);
		target.setType("*/*");
		target.addCategory(Intent.CATEGORY_OPENABLE);

		intent.setAction(Intent.ACTION_CHOOSER);
		intent.putExtra(Intent.EXTRA_INTENT, target);
		if (getTitle() != null) {
			intent.putExtra(Intent.EXTRA_TITLE, getTitle());
		}
	}

	@Override
	public boolean onActivityResult(int requestCode, int resultCode, Intent data) {
		try {
			Field[] fs = RingtonePreference.class.getDeclaredFields();
			Field f = null;
			for (int i = 0; i < fs.length; ++i) {
				if ("mRequestCode".equals(fs[i].getName())) {
					f = fs[i];
					f.setAccessible(true);
				}
			}

			int code = (Integer) f.get(this);
			if (requestCode == code) {
				if (data != null) {
					Uri uri = data.getData();
					if (callChangeListener(uri != null ? uri.toString() : "")) {
						onSaveRingtone(uri);
					}
				}
				return true;
			}
		} catch (IllegalArgumentException e) {
		} catch (IllegalAccessException e) {
		}

		return false;
	}
}
