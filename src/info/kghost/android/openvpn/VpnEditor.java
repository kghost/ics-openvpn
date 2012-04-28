package info.kghost.android.openvpn;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.net.URISyntaxException;
import java.security.KeyStore;
import java.security.cert.Certificate;

import org.spongycastle.openssl.PEMReader;

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;
import android.preference.CheckBoxPreference;
import android.preference.EditTextPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceGroup;
import android.text.TextUtils;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;

/**
 * The activity class for editing a new or existing VPN profile.
 */
public class VpnEditor extends PreferenceActivity {
	private static final int MENU_SAVE = Menu.FIRST;
	private static final int MENU_CANCEL = Menu.FIRST + 1;
	private static final int MENU_ID_ADVANCED = Menu.FIRST + 2;

	private static final int REQUEST_ADVANCED = 1;

	static final String KEY_PROFILE = "openvpn_profile";
	private static final String TAG = VpnEditor.class.getSimpleName();

	private OpenvpnProfile mProfile;
	private boolean mAddingProfile;
	private byte[] mOriginalProfileData;

	private static final String KEY_VPN_NAME = "vpn_name";
	private static final String KEY_VPN_SERVER_NAME = "vpn_server_name";
	private static final String KEY_VPN_USERAUTH = "vpn_userauth";
	private static final String KEY_VPN_CERT = "vpn_cert";
	private static final String KEY_VPN_USER_CERT = "vpn_user_cert";

	private EditTextPreference mName;
	private Preference mServerName;
	private CheckBoxPreference mUserAuth;
	private FilePickPreference mCert;
	private Preference mUserCert;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		mProfile = (OpenvpnProfile) ((savedInstanceState == null) ? getIntent()
				.getParcelableExtra(VpnSettings.KEY_VPN_PROFILE)
				: savedInstanceState.getParcelable(KEY_PROFILE));
		mAddingProfile = TextUtils.isEmpty(mProfile.getName());
		Parcel parcel = Parcel.obtain();
		mProfile.writeToParcel(parcel, 0);
		mOriginalProfileData = parcel.marshall();

		// Loads the XML preferences file
		addPreferencesFromResource(R.xml.vpn_edit);

		String formatString = mAddingProfile ? getString(R.string.vpn_edit_title_add)
				: getString(R.string.vpn_edit_title_edit);
		setTitle(String.format(formatString, "OpenVPN"));

		PreferenceGroup subpanel = getPreferenceScreen();
		mName = (EditTextPreference) subpanel.findPreference(KEY_VPN_NAME);
		mName.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
			@Override
			public boolean onPreferenceChange(Preference pref, Object newValue) {
				String v = ((String) newValue).trim();
				mProfile.setName(v);
				mName.setSummary(v);
				return true;
			}
		});
		String newName = mProfile.getName();
		newName = (newName == null) ? "" : newName.trim();
		mName.setSummary(newName);

		mServerName = subpanel.findPreference(KEY_VPN_SERVER_NAME);
		mServerName
				.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
					@Override
					public boolean onPreferenceChange(Preference pref,
							Object newValue) {
						String v = ((String) newValue).trim();
						mProfile.setServerName(v);
						mServerName.setSummary(v);
						return true;
					}
				});
		mServerName.setSummary(mProfile.getServerName());

		mUserAuth = (CheckBoxPreference) subpanel
				.findPreference(KEY_VPN_USERAUTH);
		mUserAuth.setChecked(mProfile.getUserAuth());
		mUserAuth
				.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
					public boolean onPreferenceChange(Preference pref,
							Object newValue) {
						boolean enabled = (Boolean) newValue;
						mProfile.setUserAuth(enabled);
						mUserAuth.setChecked(enabled);
						return true;
					}
				});

		mCert = (FilePickPreference) subpanel.findPreference(KEY_VPN_CERT);
		if (mProfile.getCertName() != null) {
			try {
				KeyStore ks = KeyStore.getInstance("BKS");
				ks.load(new ByteArrayInputStream(mProfile.getCertName()), null);
				Certificate cert = (Certificate) ks.getCertificate("c");
				mCert.setSummary(cert.toString());
			} catch (Exception e) {
				throw new RuntimeException(e);
			}
		}
		mCert.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
			@Override
			public boolean onPreferenceChange(Preference pref, Object data) {
				try {
					String path = Util.getPath(VpnEditor.this,
							Uri.parse((String) data));
					if (path == null)
						throw new FileNotFoundException(data.toString());
					PEMReader r = new PEMReader(new FileReader(path));
					try {
						Certificate cert = (Certificate) r.readObject();
						if (cert != null)
							try {
								KeyStore ks = KeyStore.getInstance("BKS");
								ks.load(null, null);
								ks.setCertificateEntry("c", cert);
								ByteArrayOutputStream out = new ByteArrayOutputStream();
								ks.store(out, null);
								mProfile.setCertName(out.toByteArray());
								mCert.setSummary(cert.toString());
								return true;
							} catch (Exception e) {
								throw new RuntimeException(e);
							}
						else
							Util.showLongToastMessage(VpnEditor.this,
									R.string.openvpn_error_cert_file_error);
					} finally {
						r.close();
					}
				} catch (IOException e) {
					Util.showLongToastMessage(VpnEditor.this,
							e.getLocalizedMessage());
				} catch (URISyntaxException e) {
					Util.showLongToastMessage(VpnEditor.this,
							e.getLocalizedMessage());
				}
				return false;
			}
		});

		mUserCert = subpanel.findPreference(KEY_VPN_USER_CERT);
		if (mProfile.getUserCertName() != null) {
			mUserCert.setSummary(mProfile.getUserCertName());
		}
		mUserCert
				.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
					@Override
					public boolean onPreferenceChange(Preference pref,
							Object newValue) {
						String alias = (String) newValue;
						mProfile.setUserCertName(alias);
						runOnUiThread(new RunnableEx<String>(alias) {
							@Override
							public void run(String alias) {
								mUserCert.setSummary(alias);
							}
						});
						return true;
					}
				});
	}

	@Override
	protected synchronized void onSaveInstanceState(Bundle outState) {
		outState.putParcelable(KEY_PROFILE, mProfile);
	}

	@Override
	protected void onActivityResult(final int requestCode,
			final int resultCode, final Intent data) {
		super.onActivityResult(requestCode, resultCode, data);

		if ((resultCode == RESULT_CANCELED) || (data == null)) {
			Log.d(TAG, "no result returned by editor");
			return;
		}
		if (requestCode == REQUEST_ADVANCED) {
			OpenvpnProfile newP = data.getParcelableExtra(KEY_PROFILE);
			if (newP == null) {
				Log.e(TAG, "no profile from advanced settings");
				return;
			}
			mProfile = newP;
		}
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		super.onCreateOptionsMenu(menu);
		menu.add(0, MENU_SAVE, 0, R.string.vpn_menu_done).setIcon(
				android.R.drawable.ic_menu_save);
		menu.add(
				0,
				MENU_CANCEL,
				0,
				mAddingProfile ? R.string.vpn_menu_cancel
						: R.string.vpn_menu_revert).setIcon(
				android.R.drawable.ic_menu_close_clear_cancel);
		menu.add(0, MENU_ID_ADVANCED, 0, R.string.wifi_menu_advanced).setIcon(
				android.R.drawable.ic_menu_manage);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		case MENU_SAVE:
			if (validateAndSetResult())
				finish();
			return true;

		case MENU_CANCEL:
			if (profileChanged()) {
				DialogFragment dialog = new DialogFragment() {
					@Override
					public Dialog onCreateDialog(Bundle savedInstanceState) {
						return new AlertDialog.Builder(VpnEditor.this)
								.setTitle(android.R.string.dialog_alert_title)
								.setIcon(android.R.drawable.ic_dialog_alert)
								.setMessage(
										mAddingProfile ? R.string.vpn_confirm_add_profile_cancellation
												: R.string.vpn_confirm_edit_profile_cancellation)
								.setPositiveButton(R.string.vpn_yes_button,
										new DialogInterface.OnClickListener() {
											public void onClick(
													DialogInterface dialog,
													int w) {
												finish();
											}
										})
								.setNegativeButton(R.string.vpn_mistake_button,
										null).create();
					}
				};
				dialog.show(getFragmentManager(), null);
			} else {
				finish();
			}
			return true;
		case MENU_ID_ADVANCED:
			Intent intent = new Intent(this, AdvancedSettings.class);
			intent.putExtra(KEY_PROFILE, (Parcelable) mProfile);
			startActivityForResult(intent, REQUEST_ADVANCED);
			return true;
		default:
			return super.onOptionsItemSelected(item);
		}
	}

	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event) {
		switch (keyCode) {
		case KeyEvent.KEYCODE_BACK:
			if (validateAndSetResult())
				finish();
			return true;
		default:
			return super.onKeyDown(keyCode, event);
		}
	}

	/**
	 * Checks the validity of the inputs and set the profile as result if valid.
	 * 
	 * @return true if the result is successfully set
	 */
	private boolean validateAndSetResult() {
		String errorMsg = validate();

		if (errorMsg != null) {
			Util.showErrorMessage(this, errorMsg);
			return false;
		}

		if (profileChanged()) {
			Intent intent = new Intent(this, VpnSettings.class);
			intent.putExtra(VpnSettings.KEY_VPN_PROFILE, (Parcelable) mProfile);
			setResult(RESULT_OK, intent);
		}
		return true;
	}

	private boolean profileChanged() {
		Parcel newParcel = Parcel.obtain();
		mProfile.writeToParcel(newParcel, 0);
		byte[] newData = newParcel.marshall();
		if (mOriginalProfileData.length == newData.length) {
			for (int i = 0, n = mOriginalProfileData.length; i < n; i++) {
				if (mOriginalProfileData[i] != newData[i])
					return true;
			}
			return false;
		}
		return true;
	}

	public String validate() {
		if (TextUtils.isEmpty(mProfile.getName()))
			return String.format(getString(R.string.vpn_error_miss_entering),
					getString(R.string.vpn_a_name));

		if (TextUtils.isEmpty(mProfile.getServerName()))
			return String.format(getString(R.string.vpn_error_miss_entering),
					getString(R.string.vpn_a_vpn_server));

		if (!mProfile.getUserAuth() && mProfile.getCertName() == null)
			return String.format(getString(R.string.vpn_error_miss_entering),
					getString(R.string.vpn_a_user_certificate));

		return null;
	}
}
