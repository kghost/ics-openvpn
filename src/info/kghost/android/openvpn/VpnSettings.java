/*
 * Copyright (C) 2009 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package info.kghost.android.openvpn;

import info.kghost.android.openvpn.R;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.VpnService;
import android.os.Bundle;
import android.os.Parcelable;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceClickListener;
import android.preference.PreferenceActivity;
import android.preference.PreferenceCategory;
import android.preference.PreferenceScreen;
import android.util.Log;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView.AdapterContextMenuInfo;

/**
 * The preference activity for configuring VPN settings.
 */
public class VpnSettings extends PreferenceActivity implements
		DialogInterface.OnClickListener {
	// Key to the field exchanged for profile editing.
	static final String KEY_VPN_PROFILE = "vpn_profile";

	// Key to the field exchanged for VPN type selection.
	static final String KEY_VPN_TYPE = "vpn_type";

	private static final String TAG = VpnSettings.class.getSimpleName();

	private static final String PREF_ADD_VPN = "add_new_vpn";
	private static final String PREF_VPN_LIST = "vpn_list";

	private String PREFIX;
	private File PROFILES_ROOT;

	private static final String PROFILE_OBJ_FILE = ".pobj";

	private static final int REQUEST_ADD_OR_EDIT_PROFILE = 1;
	private static final int REQUEST_CONNECT = 2;

	private static final int CONTEXT_MENU_CONNECT_ID = ContextMenu.FIRST + 0;
	private static final int CONTEXT_MENU_DISCONNECT_ID = ContextMenu.FIRST + 1;
	private static final int CONTEXT_MENU_EDIT_ID = ContextMenu.FIRST + 2;
	private static final int CONTEXT_MENU_DELETE_ID = ContextMenu.FIRST + 3;

	private static final int CONNECT_BUTTON = DialogInterface.BUTTON_POSITIVE;
	private static final int OK_BUTTON = DialogInterface.BUTTON_POSITIVE;

	private PreferenceScreen mAddVpn;
	private PreferenceCategory mVpnListContainer;

	// profile name --> VpnPreference
	private Map<String, VpnPreference> mVpnPreferenceMap;
	private List<VpnProfile> mVpnProfileList;

	// profile engaged in a connection
	private VpnProfile mActiveProfile;

	// actor engaged in connecting
	private VpnProfileActor mConnectingActor;

	// states saved for unlocking keystore
	private Runnable mUnlockAction;

	private Dialog mShowingDialog;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		PREFIX = getPackageName();
		PROFILES_ROOT = this.getFilesDir();

		addPreferencesFromResource(R.xml.vpn_settings);

		// restore VpnProfile list and construct VpnPreference map
		mVpnListContainer = (PreferenceCategory) findPreference(PREF_VPN_LIST);

		// set up the "add vpn" preference
		mAddVpn = (PreferenceScreen) findPreference(PREF_ADD_VPN);
		mAddVpn.setOnPreferenceClickListener(new OnPreferenceClickListener() {
			public boolean onPreferenceClick(Preference preference) {
				startVpnEditor(createVpnProfile());
				return true;
			}
		});

		// for long-press gesture on a profile preference
		registerForContextMenu(getListView());

		retrieveVpnListFromStorage();
	}

	@Override
	public void onResume() {
		super.onResume();

		if (mUnlockAction != null) {
			Runnable action = mUnlockAction;
			mUnlockAction = null;
			runOnUiThread(action);
		}
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();
		unregisterForContextMenu(getListView());
		if ((mShowingDialog != null) && mShowingDialog.isShowing()) {
			mShowingDialog.dismiss();
		}
	}

	@Override
	public void onCreateContextMenu(ContextMenu menu, View v,
			ContextMenuInfo menuInfo) {
		super.onCreateContextMenu(menu, v, menuInfo);

		VpnProfile p = getProfile(getProfilePositionFrom((AdapterContextMenuInfo) menuInfo));
		if (p != null) {
			menu.setHeaderTitle(p.getName());

			menu.add(0, CONTEXT_MENU_CONNECT_ID, 0, R.string.vpn_menu_connect)
					.setEnabled(mActiveProfile == null);
			menu.add(0, CONTEXT_MENU_EDIT_ID, 0, R.string.vpn_menu_edit)
					.setEnabled(true);
			menu.add(0, CONTEXT_MENU_DELETE_ID, 0, R.string.vpn_menu_delete)
					.setEnabled(true);
		}
	}

	@Override
	public boolean onContextItemSelected(MenuItem item) {
		int position = getProfilePositionFrom((AdapterContextMenuInfo) item
				.getMenuInfo());
		VpnProfile p = getProfile(position);

		switch (item.getItemId()) {
		case CONTEXT_MENU_CONNECT_ID:
		case CONTEXT_MENU_DISCONNECT_ID:
			connect(p);
			return true;

		case CONTEXT_MENU_EDIT_ID:
			startVpnEditor(p);
			return true;

		case CONTEXT_MENU_DELETE_ID:
			deleteProfile(position);
			return true;
		}

		return super.onContextItemSelected(item);
	}

	@Override
	protected void onActivityResult(final int requestCode,
			final int resultCode, final Intent data) {
		if ((resultCode == RESULT_CANCELED) || (data == null)) {
			Log.d(TAG, "no result returned by editor");
			return;
		}

		if (requestCode == REQUEST_CONNECT) {
			Intent intent = new Intent(this, OpenVpnService.class);
			intent.putExtra(PREFIX + ".CONF",
					data.getParcelableExtra(PREFIX + ".CONF"));
			startService(intent);
		} else if (requestCode == REQUEST_ADD_OR_EDIT_PROFILE) {
			VpnProfile p = data.getParcelableExtra(KEY_VPN_PROFILE);
			if (p == null) {
				Log.e(TAG, "null object returned by editor");
				return;
			}

			int index = getProfileIndexFromId(p.getId());
			if (checkDuplicateName(p, index)) {
				final VpnProfile profile = p;
				Util.showErrorMessage(this, String.format(
						getString(R.string.vpn_error_duplicate_name),
						p.getName()), new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface dialog, int w) {
						startVpnEditor(profile);
					}
				});
				return;
			}

			try {
				if (index < 0) {
					addProfile(p);
					Util.showShortToastMessage(this, String.format(
							getString(R.string.vpn_profile_added), p.getName()));
				} else {
					replaceProfile(index, p);
					Util.showShortToastMessage(this, String.format(
							getString(R.string.vpn_profile_replaced),
							p.getName()));
				}
			} catch (IOException e) {
				final VpnProfile profile = p;
				Util.showErrorMessage(this, e + ": " + e.getMessage(),
						new DialogInterface.OnClickListener() {
							public void onClick(DialogInterface dialog, int w) {
								startVpnEditor(profile);
							}
						});
			}
		} else {
			throw new RuntimeException("unknown request code: " + requestCode);
		}
	}

	// Called when the buttons on the connect dialog are clicked.
	// @Override
	public synchronized void onClick(DialogInterface dialog, int which) {
		if (which == CONNECT_BUTTON) {
			Dialog d = (Dialog) dialog;
			String error = mConnectingActor.validateInputs(d);
			if (error == null) {
				mConnectingActor.connect(d);
				return;
			} else {
				// show error dialog
				mShowingDialog = new AlertDialog.Builder(this)
						.setTitle(android.R.string.dialog_alert_title)
						.setIcon(android.R.drawable.ic_dialog_alert)
						.setMessage(
								String.format(
										getString(R.string.vpn_error_miss_entering),
										error))
						.setPositiveButton(R.string.vpn_back_button,
								new DialogInterface.OnClickListener() {
									public void onClick(DialogInterface dialog,
											int which) {
									}
								}).create();
				mShowingDialog.show();
			}
		}
	}

	private int getProfileIndexFromId(String id) {
		int index = 0;
		for (VpnProfile p : mVpnProfileList) {
			if (p.getId().equals(id)) {
				return index;
			} else {
				index++;
			}
		}
		return -1;
	}

	// Replaces the profile at index in mVpnProfileList with p.
	// Returns true if p's name is a duplicate.
	private boolean checkDuplicateName(VpnProfile p, int index) {
		List<VpnProfile> list = mVpnProfileList;
		VpnPreference pref = mVpnPreferenceMap.get(p.getName());
		if ((pref != null) && (index >= 0) && (index < list.size())) {
			// not a duplicate if p is to replace the profile at index
			if (pref.mProfile == list.get(index))
				pref = null;
		}
		return (pref != null);
	}

	private int getProfilePositionFrom(AdapterContextMenuInfo menuInfo) {
		// excludes mVpnListContainer and the preferences above it
		return menuInfo.position - mVpnListContainer.getOrder() - 1;
	}

	// position: position in mVpnProfileList
	private VpnProfile getProfile(int position) {
		return ((position >= 0) ? mVpnProfileList.get(position) : null);
	}

	// position: position in mVpnProfileList
	private void deleteProfile(final int position) {
		if ((position < 0) || (position >= mVpnProfileList.size()))
			return;
		DialogInterface.OnClickListener onClickListener = new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialog, int which) {
				dialog.dismiss();
				if (which == OK_BUTTON) {
					VpnProfile p = mVpnProfileList.remove(position);
					VpnPreference pref = mVpnPreferenceMap.remove(p.getName());
					mVpnListContainer.removePreference(pref);
					removeProfileFromStorage(p);
				}
			}
		};
		mShowingDialog = new AlertDialog.Builder(this)
				.setTitle(android.R.string.dialog_alert_title)
				.setIcon(android.R.drawable.ic_dialog_alert)
				.setMessage(R.string.vpn_confirm_profile_deletion)
				.setPositiveButton(android.R.string.ok, onClickListener)
				.setNegativeButton(R.string.vpn_no_button, onClickListener)
				.create();
		mShowingDialog.show();
	}

	// Randomly generates an ID for the profile.
	// The ID is unique and only set once when the profile is created.
	private void setProfileId(VpnProfile profile) {
		String id;

		while (true) {
			id = String
					.valueOf(Math.abs(Double.doubleToLongBits(Math.random())));
			if (id.length() >= 8)
				break;
		}
		for (VpnProfile p : mVpnProfileList) {
			if (p.getId().equals(id)) {
				setProfileId(profile);
				return;
			}
		}
		profile.setId(id);
	}

	private void addProfile(VpnProfile p) throws IOException {
		setProfileId(p);
		saveProfileToStorage(p);

		mVpnProfileList.add(p);
		addPreferenceFor(p);
	}

	private VpnPreference addPreferenceFor(VpnProfile p) {
		return addPreferenceFor(p, true);
	}

	// Adds a preference in mVpnListContainer
	private VpnPreference addPreferenceFor(VpnProfile p, boolean addToContainer) {
		VpnPreference pref = new VpnPreference(this, p);
		mVpnPreferenceMap.put(p.getName(), pref);
		if (addToContainer)
			mVpnListContainer.addPreference(pref);

		pref.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
			public boolean onPreferenceClick(Preference pref) {
				connect(((VpnPreference) pref).mProfile);
				return true;
			}
		});
		return pref;
	}

	// index: index to mVpnProfileList
	private void replaceProfile(int index, VpnProfile p) throws IOException {
		Map<String, VpnPreference> map = mVpnPreferenceMap;
		VpnProfile oldProfile = mVpnProfileList.set(index, p);
		VpnPreference pref = map.remove(oldProfile.getName());
		if (pref.mProfile != oldProfile) {
			throw new RuntimeException("inconsistent state!");
		}

		p.setId(oldProfile.getId());

		// TODO: remove copyFiles once the setId() code propagates.
		// Copy config files and remove the old ones if they are in different
		// directories.
		if (Util.copyFiles(getProfileDir(oldProfile), getProfileDir(p))) {
			removeProfileFromStorage(oldProfile);
		}
		saveProfileToStorage(p);

		pref.setProfile(p);
		map.put(p.getName(), pref);
	}

	private void startVpnEditor(final VpnProfile profile) {
		Intent intent = new Intent(this, VpnEditor.class);
		intent.putExtra(KEY_VPN_PROFILE, (Parcelable) profile);
		startActivityForResult(intent, REQUEST_ADD_OR_EDIT_PROFILE);
	}

	private synchronized void connect(final VpnProfile p) {
		Intent intent = VpnService.prepare(this);
		if (intent != null) {
			intent.putExtra(PREFIX + ".CONF", (Parcelable) p);
			startActivityForResult(intent, REQUEST_CONNECT);
		} else {
			intent = new Intent();
			intent.putExtra(PREFIX + ".CONF", (Parcelable) p);
			onActivityResult(REQUEST_CONNECT, RESULT_OK, intent);
		}
	}

	private File getProfileDir(VpnProfile p) {
		return new File(PROFILES_ROOT, p.getId());
	}

	private void saveProfileToStorage(VpnProfile p) throws IOException {
		File f = getProfileDir(p);
		if (!f.exists())
			f.mkdirs();
		ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(
				new File(f, PROFILE_OBJ_FILE)));
		oos.writeObject(p);
		oos.close();
	}

	private void removeProfileFromStorage(VpnProfile p) {
		Util.deleteFile(getProfileDir(p));
	}

	private void retrieveVpnListFromStorage() {
		mVpnPreferenceMap = new LinkedHashMap<String, VpnPreference>();
		mVpnProfileList = Collections
				.synchronizedList(new ArrayList<VpnProfile>());
		mVpnListContainer.removeAll();

		File root = PROFILES_ROOT;
		String[] dirs = root.list();
		if (dirs == null)
			return;
		for (String dir : dirs) {
			File f = new File(new File(root, dir), PROFILE_OBJ_FILE);
			if (!f.exists())
				continue;
			try {
				VpnProfile p = deserialize(f);
				if (p == null)
					continue;
				if (!checkIdConsistency(dir, p))
					continue;

				mVpnProfileList.add(p);
			} catch (IOException e) {
				Log.e(TAG, "retrieveVpnListFromStorage()", e);
			}
		}
		Collections.sort(mVpnProfileList, new Comparator<VpnProfile>() {
			@Override
			public int compare(VpnProfile p1, VpnProfile p2) {
				return p1.getName().compareTo(p2.getName());
			}

			@Override
			public boolean equals(Object p) {
				// not used
				return false;
			}
		});
		for (VpnProfile p : mVpnProfileList) {
			addPreferenceFor(p, true);
		}
	}

	// A sanity check. Returns true if the profile directory name and profile ID
	// are consistent.
	private boolean checkIdConsistency(String dirName, VpnProfile p) {
		if (!dirName.equals(p.getId())) {
			Log.d(TAG, "ID inconsistent: " + dirName + " vs " + p.getId());
			return false;
		} else {
			return true;
		}
	}

	private VpnProfile deserialize(File profileObjectFile) throws IOException {
		try {
			ObjectInputStream ois = new ObjectInputStream(new FileInputStream(
					profileObjectFile));
			VpnProfile p = (VpnProfile) ois.readObject();
			ois.close();
			return p;
		} catch (ClassNotFoundException e) {
			Log.d(TAG, "deserialize a profile", e);
			return null;
		}
	}

	private VpnProfile createVpnProfile() {
		return new OpenvpnProfile();
	}

	private class VpnPreference extends Preference {
		VpnProfile mProfile;

		VpnPreference(Context c, VpnProfile p) {
			super(c);
			setProfile(p);
		}

		void setProfile(VpnProfile p) {
			mProfile = p;
			setTitle(p.getName());
		}
	}
}
