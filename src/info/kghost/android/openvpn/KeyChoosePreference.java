package info.kghost.android.openvpn;

import android.app.Activity;
import android.content.Context;
import android.preference.Preference;
import android.security.KeyChain;
import android.security.KeyChainAliasCallback;
import android.util.AttributeSet;

class KeyChoosePreference extends Preference {
	private String mValue;

	public KeyChoosePreference(Context context, AttributeSet attrs, int defStyle) {
		super(context, attrs, defStyle);
	}

	public KeyChoosePreference(Context context, AttributeSet attrs) {
		super(context, attrs);
	}

	public KeyChoosePreference(Context context) {
		super(context);
	}

	protected void setValue(String value) {
		mValue = value;
	}

	public String getValue() {
		return mValue;
	}

	@Override
	protected void onClick() {
		KeyChain.choosePrivateKeyAlias((Activity) this.getContext(),
				new KeyChainAliasCallback() {
					@Override
					public void alias(String alias) {
						if (alias != null)
							if (callChangeListener(alias)) {
								setValue(alias);
							}
					}
				}, null, null, null, -1, null);
	}
}
