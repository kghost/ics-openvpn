package info.kghost.android.openvpn;

import java.net.URISyntaxException;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Parcelable;
import android.preference.CheckBoxPreference;
import android.preference.EditTextPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.view.KeyEvent;

public class AdvancedSettings extends PreferenceActivity {
	private static final String KEY_PORT = "set_port";
	private static final String KEY_PROTO = "set_protocol";
	private static final String KEY_COMP_LZO = "set_comp_lzo";
	private static final String KEY_NS_CERT_TYPE = "set_ns_cert_type";
	private static final String KEY_REDIRECT_GATEWAY = "set_redirect_gateway";
	private static final String KEY_SET_ADDR = "set_addr";
	private static final String KEY_LOCAL_ADDR = "set_local_addr";
	private static final String KEY_REMOTE_ADDR = "set_remote_addr";
	private static final String KEY_CIPHER = "set_cipher";
	private static final String KEY_KEYSIZE = "set_keysize";
	private static final String KEY_USE_TLS_AUTH = "set_use_tls_auth";
	private static final String KEY_TLS_KEY = "set_tls_auth_key";
	private static final String KEY_TLS_AUTH_KEY_DIRECTION = "set_tls_auth_key_direction";
	private static final String KEY_EXTRA = "set_extra";

	private EditTextPreference mPort;
	private ListPreference mProto;
	private CheckBoxPreference mCompLzo;
	private ListPreference mNsCertType;
	private CheckBoxPreference mRedirectGateway;
	private CheckBoxPreference mSetAddr;
	private EditTextPreference mLocalAddr;
	private EditTextPreference mRemoteAddr;
	private EditTextPreference mCipher;
	private EditTextPreference mKeySize;
	private CheckBoxPreference mUseTlsAuth;
	private FilePickPreference mTlsAuthKey;
	private ListPreference mTlsAuthKeyDirection;
	private EditTextPreference mExtra;

	private OpenvpnProfile profile;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		profile = getIntent().getParcelableExtra(VpnEditor.KEY_PROFILE);

		addPreferencesFromResource(R.xml.openvpn_advanced_settings);

		mPort = (EditTextPreference) findPreference(KEY_PORT);
		mProto = (ListPreference) findPreference(KEY_PROTO);
		mCompLzo = (CheckBoxPreference) findPreference(KEY_COMP_LZO);
		mNsCertType = (ListPreference) findPreference(KEY_NS_CERT_TYPE);
		mRedirectGateway = (CheckBoxPreference) findPreference(KEY_REDIRECT_GATEWAY);
		mSetAddr = (CheckBoxPreference) findPreference(KEY_SET_ADDR);
		mLocalAddr = (EditTextPreference) findPreference(KEY_LOCAL_ADDR);
		mRemoteAddr = (EditTextPreference) findPreference(KEY_REMOTE_ADDR);
		mCipher = (EditTextPreference) findPreference(KEY_CIPHER);
		mKeySize = (EditTextPreference) findPreference(KEY_KEYSIZE);
		mExtra = (EditTextPreference) findPreference(KEY_EXTRA);
		mUseTlsAuth = (CheckBoxPreference) findPreference(KEY_USE_TLS_AUTH);
		mTlsAuthKey = (FilePickPreference) findPreference(KEY_TLS_KEY);
		mTlsAuthKeyDirection = (ListPreference) findPreference(KEY_TLS_AUTH_KEY_DIRECTION);

		mPort.setSummary(profile.getPort());
		mPort.setText(profile.getPort());
		mPort.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
			public boolean onPreferenceChange(Preference pref, Object newValue) {
				String name = (String) newValue;
				name.trim();
				profile.setPort(name);
				mPort.setSummary(profile.getPort());

				return true;
			}
		});

		mProto.setSummary(profile.getProto());
		mProto.setValue(profile.getProto());
		mProto.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
			public boolean onPreferenceChange(Preference pref, Object newValue) {
				String name = (String) newValue;
				name.trim();
				profile.setProto(name);
				mProto.setSummary(profile.getProto());

				return true;
			}
		});

		mCompLzo.setChecked(profile.getUseCompLzo());
		mCompLzo.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
			public boolean onPreferenceChange(Preference pref, Object newValue) {
				Boolean b = (Boolean) newValue;
				profile.setUseCompLzo(b);

				return true;
			}
		});

		mNsCertType.setValue(profile.getNsCertType());
		mNsCertType.setSummary(mNsCertType.getEntry());
		mNsCertType
				.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
					public boolean onPreferenceChange(Preference pref,
							Object newValue) {
						String name = (String) newValue;
						name.trim();
						profile.setNsCertType(name);
						mNsCertType.setValue(profile.getNsCertType());
						mNsCertType.setSummary(mNsCertType.getEntry());
						return true;
					}
				});

		mRedirectGateway.setChecked(profile.getRedirectGateway());
		mRedirectGateway
				.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
					public boolean onPreferenceChange(Preference pref,
							Object newValue) {
						Boolean b = (Boolean) newValue;
						profile.setRedirectGateway(b);

						return true;
					}
				});

		// This is inverted to cope with the way dependencies work
		mSetAddr.setChecked(!profile.getSupplyAddr());
		mSetAddr.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
			public boolean onPreferenceChange(Preference pref, Object newValue) {
				Boolean b = (Boolean) newValue;
				profile.setSupplyAddr(!b);

				return true;
			}
		});

		mLocalAddr.setSummary(profile.getLocalAddr());
		mLocalAddr.setText(profile.getLocalAddr());
		mLocalAddr
				.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
					public boolean onPreferenceChange(Preference pref,
							Object newValue) {
						String name = (String) newValue;
						name.trim();
						profile.setLocalAddr(name);
						mLocalAddr.setSummary(profile.getLocalAddr());

						return true;
					}
				});

		mRemoteAddr.setSummary(profile.getRemoteAddr());
		mRemoteAddr.setText(profile.getRemoteAddr());
		mRemoteAddr
				.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
					public boolean onPreferenceChange(Preference pref,
							Object newValue) {
						String name = (String) newValue;
						name.trim();
						profile.setRemoteAddr(name);
						mRemoteAddr.setSummary(profile.getRemoteAddr());

						return true;
					}
				});

		if (profile.getCipher() == null || profile.getCipher().equals(""))
			mCipher.setSummary(R.string.vpn_openvpn_set_cipher_default);
		else
			mCipher.setSummary(profile.getCipher());
		mCipher.setText(profile.getCipher());
		mCipher.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
			public boolean onPreferenceChange(Preference pref, Object newValue) {
				String name = (String) newValue;
				name.trim();
				profile.setCipher(name);
				if (profile.getCipher().equals(""))
					mCipher.setSummary(R.string.vpn_openvpn_set_cipher_default);
				else
					mCipher.setSummary(profile.getCipher());
				return true;
			}
		});

		if (profile.getKeySize() == null || profile.getKeySize().equals("0")) {
			mKeySize.setSummary(R.string.vpn_openvpn_set_keysize_default);
			mKeySize.setText("");
		} else {
			mKeySize.setSummary(profile.getKeySize());
			mKeySize.setText(profile.getKeySize());
		}
		mKeySize.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
			public boolean onPreferenceChange(Preference pref, Object newValue) {
				String name = (String) newValue;
				name.trim();
				if (name.equals(""))
					name = "0";
				profile.setKeySize(name);
				if (profile.getKeySize().equals("0"))
					mKeySize.setSummary(R.string.vpn_openvpn_set_keysize_default);
				else
					mKeySize.setSummary(profile.getKeySize());
				return true;
			}
		});

		mUseTlsAuth.setChecked(profile.getUseTlsAuth());
		mUseTlsAuth
				.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
					public boolean onPreferenceChange(Preference pref,
							Object newValue) {
						Boolean b = (Boolean) newValue;
						profile.setUseTlsAuth(b);

						return true;
					}
				});

		mTlsAuthKey.setSummary(profile.getTlsAuthKey());
		mTlsAuthKey
				.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
					public boolean onPreferenceChange(Preference pref,
							Object data) {
						try {
							String name = Util.getPath(AdvancedSettings.this,
									Uri.parse((String) data));
							profile.setTlsAuthKey(name);
							mTlsAuthKey.setSummary(profile.getTlsAuthKey());
						} catch (URISyntaxException e) {
							Util.showLongToastMessage(AdvancedSettings.this,
									e.getLocalizedMessage());
						}
						return true;
					}
				});

		mTlsAuthKeyDirection.setValue(profile.getTlsAuthKeyDirection());
		mTlsAuthKeyDirection.setSummary(mTlsAuthKeyDirection.getEntry());
		mTlsAuthKeyDirection
				.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
					public boolean onPreferenceChange(Preference pref,
							Object newValue) {
						String name = (String) newValue;
						name.trim();
						profile.setTlsAuthKeyDirection(name);
						mTlsAuthKeyDirection.setValue(profile
								.getTlsAuthKeyDirection());
						mTlsAuthKeyDirection.setSummary(mTlsAuthKeyDirection
								.getEntry());
						return true;
					}
				});

		mExtra.setSummary(profile.getExtra());
		mExtra.setText(profile.getExtra());
		mExtra.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
			public boolean onPreferenceChange(Preference pref, Object newValue) {
				String name = (String) newValue;
				name.trim();
				profile.setExtra(name);
				mExtra.setSummary(profile.getExtra());
				return true;
			}
		});
	}

	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event) {
		switch (keyCode) {
		case KeyEvent.KEYCODE_BACK:
			Intent intent = new Intent(this, VpnEditor.class);
			intent.putExtra(VpnEditor.KEY_PROFILE, (Parcelable) profile);
			setResult(RESULT_OK, intent);

			finish();
			return true;
		}
		return super.onKeyDown(keyCode, event);
	}
}