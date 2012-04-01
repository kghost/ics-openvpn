package info.kghost.android.openvpn;

import android.content.Context;
import android.preference.Preference;

public class CertChoosePreference extends Preference {
	public CertChoosePreference(Context context) {
		super(context);
	}

	public void change(Object v) {
		callChangeListener(v);
	}
}
