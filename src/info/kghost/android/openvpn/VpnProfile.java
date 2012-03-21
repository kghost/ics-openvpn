/*
 * Copyright (C) 2009, The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package info.kghost.android.openvpn;

import java.io.Serializable;

import android.os.Parcel;
import android.os.Parcelable;

/**
 * A VPN profile. {@hide}
 */
public abstract class VpnProfile implements Parcelable, Serializable {
	private static final long serialVersionUID = 1L;
	private String mName; // unique display name
	private String mId; // unique identifier
	private String mServerName; // VPN server name
	private String mDomainSuffices; // space separated list
	private String mRouteList; // space separated list
	private String mSavedUsername;

	/** Sets a user-friendly name for this profile. */
	public void setName(String name) {
		mName = name;
	}

	public String getName() {
		return mName;
	}

	/**
	 * Sets an ID for this profile. The caller should make sure the uniqueness
	 * of the ID.
	 */
	public void setId(String id) {
		mId = id;
	}

	public String getId() {
		return mId;
	}

	/**
	 * Sets the name of the VPN server. Used for DNS lookup.
	 */
	public void setServerName(String name) {
		mServerName = name;
	}

	public String getServerName() {
		return mServerName;
	}

	/**
	 * Sets the domain suffices for DNS resolution.
	 * 
	 * @param entries
	 *            a comma-separated list of domain suffices
	 */
	public void setDomainSuffices(String entries) {
		mDomainSuffices = entries;
	}

	public String getDomainSuffices() {
		return mDomainSuffices;
	}

	/**
	 * Sets the routing info for this VPN connection.
	 * 
	 * @param entries
	 *            a comma-separated list of routes; each entry is in the format
	 *            of "(network address)/(network mask)"
	 */
	public void setRouteList(String entries) {
		mRouteList = entries;
	}

	public String getRouteList() {
		return mRouteList;
	}

	public void setSavedUsername(String name) {
		mSavedUsername = name;
	}

	public String getSavedUsername() {
		return mSavedUsername;
	}

	protected void readFromParcel(Parcel in) {
		mName = in.readString();
		mId = in.readString();
		mServerName = in.readString();
		mDomainSuffices = in.readString();
		mRouteList = in.readString();
		mSavedUsername = in.readString();
	}

	public static final Parcelable.Creator<VpnProfile> CREATOR = new Parcelable.Creator<VpnProfile>() {
		public VpnProfile createFromParcel(Parcel in) {
			VpnProfile p = new OpenvpnProfile();
			p.readFromParcel(in);
			return p;
		}

		public VpnProfile[] newArray(int size) {
			return new VpnProfile[size];
		}
	};

	public void writeToParcel(Parcel parcel, int flags) {
		parcel.writeString(mName);
		parcel.writeString(mId);
		parcel.writeString(mServerName);
		parcel.writeString(mDomainSuffices);
		parcel.writeString(mRouteList);
		parcel.writeString(mSavedUsername);
	}

	public int describeContents() {
		return 0;
	}
}
