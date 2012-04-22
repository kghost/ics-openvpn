package info.kghost.android.openvpn;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;

import org.spongycastle.openssl.PEMReader;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Parcelable;
import android.preference.CheckBoxPreference;
import android.preference.EditTextPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceClickListener;
import android.preference.PreferenceActivity;
import android.preference.PreferenceGroup;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast;

/**
 * The class for editing {@link OpenvpnProfile}.
 */
class OpenvpnEditor extends VpnProfileEditor {
	private static final String KEY_PROFILE = "openvpn_profile";

	private static final int FILE_SELECT_CODE = 0;
	private static final int REQUEST_ADVANCED = 1;

	private static final String TAG = OpenvpnEditor.class.getSimpleName();

	private int MENU_ID_ADVANCED;

	private CheckBoxPreference mUserAuth;

	private CertChoosePreference mCert;
	private Preference mUserCert;

	public OpenvpnEditor(OpenvpnProfile p) {
		super(p);
	}

	private static abstract class RunnableEx<T> implements Runnable {
		private T m;

		public RunnableEx(T o) {
			m = o;
		}

		@Override
		public void run() {
			run(m);
		}

		protected abstract void run(T o);
	}

	private class FileChooseClickListener implements OnPreferenceClickListener {
		final private Activity mActivity;

		public FileChooseClickListener(Activity activity) {
			mActivity = activity;
		}

		@Override
		public boolean onPreferenceClick(Preference preference) {
			Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
			intent.setType("*/*");
			intent.addCategory(Intent.CATEGORY_OPENABLE);

			try {
				mActivity.startActivityForResult(Intent.createChooser(intent,
						"Select certification file"), FILE_SELECT_CODE);
			} catch (android.content.ActivityNotFoundException ex) {
				// Potentially direct the user to the Market with a Dialog
				Toast.makeText(mActivity, "Please install a File Manager.",
						Toast.LENGTH_SHORT).show();
			}
			return false;
		}
	}

	private Certificate parseCert(byte[] cert) {
		try {
			KeyStore ks = KeyStore.getInstance("BKS");
			ks.load(new ByteArrayInputStream(cert), null);
			return (Certificate) ks.getCertificate("c");
		} catch (NoSuchAlgorithmException e) {
		} catch (CertificateException e) {
		} catch (IOException e) {
		} catch (KeyStoreException e) {
		}
		return null;
	}

	@Override
	protected void loadExtraPreferencesTo(Activity activity,
			PreferenceGroup subpanel) {
		final Context c = subpanel.getContext();
		final OpenvpnProfile profile = (OpenvpnProfile) getProfile();
		mUserAuth = new CheckBoxPreference(c);
		mUserAuth.setTitle(R.string.vpn_openvpn_userauth);
		mUserAuth.setSummary(R.string.vpn_openvpn_userauth_summary);
		mUserAuth.setChecked(profile.getUserAuth());
		mUserAuth
				.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
					public boolean onPreferenceChange(Preference pref,
							Object newValue) {
						boolean enabled = (Boolean) newValue;
						profile.setUserAuth(enabled);
						mUserAuth.setChecked(enabled);
						return true;
					}
				});
		subpanel.addPreference(mUserAuth);

		mCert = new CertChoosePreference(c);
		mCert.setTitle(R.string.vpn_ca_certificate);
		if (profile.getCertName() != null) {
			Certificate cert = parseCert(profile.getCertName());
			if (cert != null)
				mCert.setSummary(cert.toString());
		} else {
			mCert.setSummary(R.string.vpn_ca_certificate_title);
		}
		mCert.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
			@Override
			public boolean onPreferenceChange(Preference pref, Object newValue) {
				Certificate cert = (Certificate) newValue;

				try {
					KeyStore ks = KeyStore.getInstance("BKS");
					ks.load(null, null);
					ks.setCertificateEntry("c", cert);
					ByteArrayOutputStream out = new ByteArrayOutputStream();
					ks.store(out, null);
					profile.setCertName(out.toByteArray());
					((Activity) c).runOnUiThread(new RunnableEx<String>(cert
							.toString()) {
						@Override
						public void run(String cert) {
							mCert.setSummary(cert);
						}
					});
				} catch (Exception e) {
					throw new RuntimeException(e);
				}

				return true;
			}
		});
		mCert.setOnPreferenceClickListener(new FileChooseClickListener(activity));
		subpanel.addPreference(mCert);

		mUserCert = new KeyChoosePreference(c);
		mUserCert.setTitle(R.string.vpn_user_certificate);
		if (profile.getUserCertName() == null) {
			mUserCert.setSummary(R.string.vpn_user_certificate_title);
		} else {
			mUserCert.setSummary(profile.getUserCertName());
		}
		mUserCert
				.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
					@Override
					public boolean onPreferenceChange(Preference pref,
							Object newValue) {
						String alias = (String) newValue;
						profile.setUserCertName(alias);
						((Activity) c).runOnUiThread(new RunnableEx<String>(
								alias) {
							@Override
							public void run(String alias) {
								mUserCert.setSummary(alias);
							}
						});
						return true;
					}
				});
		subpanel.addPreference(mUserCert);
	}

	@Override
	public String validate() {
		String result = super.validate();
		if (result != null)
			return result;

		// if (!mUserAuth.isChecked()) {
		// result = validate(mCert, R.string.vpn_a_user_certificate);
		// if (result != null)
		// return result;
		// }

		return null;
	}

	@Override
	protected void onCreateOptionsMenu(Menu menu, int last_item) {
		MENU_ID_ADVANCED = last_item + 1;

		menu.add(0, MENU_ID_ADVANCED, 0, R.string.wifi_menu_advanced).setIcon(
				android.R.drawable.ic_menu_manage);
	}

	@Override
	protected boolean onOptionsItemSelected(PreferenceActivity p, MenuItem item) {
		if (item.getItemId() == MENU_ID_ADVANCED) {
			Intent intent = new Intent(p, AdvancedSettings.class);
			intent.putExtra(KEY_PROFILE, (Parcelable) getProfile());
			p.startActivityForResult(intent, REQUEST_ADVANCED);
			return true;
		} else {
			return false;
		}
	}

	@Override
	protected void onActivityResult(final int requestCode,
			final int resultCode, final Intent data) {
		if (requestCode == FILE_SELECT_CODE) {
			String path = data.getData().getPath();

			try {
				PEMReader r = new PEMReader(new FileReader(path));
				try {
					Certificate k = (Certificate) r.readObject();
					if (k != null)
						mCert.change(k);
				} finally {
					r.close();
				}
			} catch (FileNotFoundException e1) {
			} catch (IOException e) {
			}
		} else if (requestCode == REQUEST_ADVANCED) {
			OpenvpnProfile p = (OpenvpnProfile) getProfile();
			OpenvpnProfile newP = data.getParcelableExtra(KEY_PROFILE);
			if (newP == null) {
				Log.e(TAG, "no profile from advanced settings");
				return;
			}
			// manually copy across all advanced settings
			p.setPort(newP.getPort());
			p.setProto(newP.getProto());
			p.setUseCompLzo(newP.getUseCompLzo());
			p.setRedirectGateway(newP.getRedirectGateway());
			p.setSupplyAddr(newP.getSupplyAddr());
			p.setLocalAddr(newP.getLocalAddr());
			p.setRemoteAddr(newP.getRemoteAddr());
			p.setCipher(newP.getCipher());
			p.setKeySize(newP.getKeySize());
			p.setExtra(newP.getExtra());
			p.setUseTlsAuth(newP.getUseTlsAuth());
			p.setTlsAuthKey(newP.getTlsAuthKey());
			p.setTlsAuthKeyDirection(newP.getTlsAuthKeyDirection());
		}
	}

	public static class AdvancedSettings extends PreferenceActivity {
		private static final String KEY_PORT = "set_port";

		private static final String KEY_PROTO = "set_protocol";

		private static final String KEY_COMP_LZO = "set_comp_lzo";

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

		private ListPreference mTlsAuthKeyDirection;

		private CheckBoxPreference mCompLzo;

		private CheckBoxPreference mRedirectGateway;

		private CheckBoxPreference mSetAddr;

		private CheckBoxPreference mUseTlsAuth;

		private EditTextPreference mLocalAddr;

		private EditTextPreference mRemoteAddr;

		private EditTextPreference mCipher;

		private EditTextPreference mKeySize;

		private EditTextPreference mExtra;

		private EditTextPreference mTlsAuthKey;

		private OpenvpnProfile profile;

		@Override
		protected void onCreate(Bundle savedInstanceState) {
			super.onCreate(savedInstanceState);

			profile = getIntent().getParcelableExtra(KEY_PROFILE);

			addPreferencesFromResource(R.xml.openvpn_advanced_settings);

			mPort = (EditTextPreference) findPreference(KEY_PORT);
			mProto = (ListPreference) findPreference(KEY_PROTO);
			mCompLzo = (CheckBoxPreference) findPreference(KEY_COMP_LZO);
			mRedirectGateway = (CheckBoxPreference) findPreference(KEY_REDIRECT_GATEWAY);
			mSetAddr = (CheckBoxPreference) findPreference(KEY_SET_ADDR);
			mLocalAddr = (EditTextPreference) findPreference(KEY_LOCAL_ADDR);
			mRemoteAddr = (EditTextPreference) findPreference(KEY_REMOTE_ADDR);
			mCipher = (EditTextPreference) findPreference(KEY_CIPHER);
			mKeySize = (EditTextPreference) findPreference(KEY_KEYSIZE);
			mExtra = (EditTextPreference) findPreference(KEY_EXTRA);
			mUseTlsAuth = (CheckBoxPreference) findPreference(KEY_USE_TLS_AUTH);
			mTlsAuthKey = (EditTextPreference) findPreference(KEY_TLS_KEY);
			mTlsAuthKeyDirection = (ListPreference) findPreference(KEY_TLS_AUTH_KEY_DIRECTION);

			mPort.setSummary(profile.getPort());
			mPort.setText(profile.getPort());
			mPort.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
				public boolean onPreferenceChange(Preference pref,
						Object newValue) {
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
				public boolean onPreferenceChange(Preference pref,
						Object newValue) {
					String name = (String) newValue;
					name.trim();
					profile.setProto(name);
					mProto.setSummary(profile.getProto());

					return true;
				}
			});

			mCompLzo.setChecked(profile.getUseCompLzo());
			mCompLzo.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
				public boolean onPreferenceChange(Preference pref,
						Object newValue) {
					Boolean b = (Boolean) newValue;
					profile.setUseCompLzo(b);

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
				public boolean onPreferenceChange(Preference pref,
						Object newValue) {
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
				public boolean onPreferenceChange(Preference pref,
						Object newValue) {
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

			if (profile.getKeySize() == null
					|| profile.getKeySize().equals("0")) {
				mKeySize.setSummary(R.string.vpn_openvpn_set_keysize_default);
				mKeySize.setText("");
			} else {
				mKeySize.setSummary(profile.getKeySize());
				mKeySize.setText(profile.getKeySize());
			}
			mKeySize.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
				public boolean onPreferenceChange(Preference pref,
						Object newValue) {
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
			mTlsAuthKey.setText(profile.getTlsAuthKey());
			mTlsAuthKey
					.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
						public boolean onPreferenceChange(Preference pref,
								Object newValue) {
							String name = (String) newValue;
							name.trim();
							profile.setTlsAuthKey(name);
							mTlsAuthKey.setSummary(profile.getTlsAuthKey());

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
							mTlsAuthKeyDirection
									.setSummary(mTlsAuthKeyDirection.getEntry());

							return true;
						}
					});

			mExtra.setSummary(profile.getExtra());
			mExtra.setText(profile.getExtra());
			mExtra.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
				public boolean onPreferenceChange(Preference pref,
						Object newValue) {
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
				intent.putExtra(KEY_PROFILE, (Parcelable) profile);
				setResult(RESULT_OK, intent);

				finish();
				return true;
			}
			return super.onKeyDown(keyCode, event);
		}
	}
}
