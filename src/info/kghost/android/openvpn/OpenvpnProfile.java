package info.kghost.android.openvpn;

import java.io.Serializable;

import android.os.Parcel;
import android.os.Parcelable;

/**
 * A VPN profile. {@hide}
 */
public class OpenvpnProfile implements Parcelable, Serializable {
	private static final long serialVersionUID = 1L;

	private static final String PROTO_UDP = "udp";
	private static final String PROTO_TCP = "tcp";

	private String mName; // unique display name
	private String mId; // unique identifier
	private String mDomainSuffices; // space separated list
	private String mRouteList; // space separated list
	private String mSavedUsername;

	// Standard Settings
	private String mServerName; // VPN server name
	private boolean mUserAuth = false;
	private byte[] mCert;
	private String mUserCert;

	// Advanced Settings
	private int mPort = 1194;
	private String mProto = PROTO_UDP;
	private boolean mUseCompLzo = false;
	private String mNsCertType = "server";
	private boolean mRedirectGateway = false;
	private boolean mSupplyAddr = false;
	private String mLocalAddr;
	private String mRemoteAddr;
	private String mCipher;
	private int mKeySize;
	private boolean mUseTlsAuth;
	private String mTlsAuthKey;
	private String mTlsAuthKeyDirection;
	private String mExtra;

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

	public static final Parcelable.Creator<OpenvpnProfile> CREATOR = new Parcelable.Creator<OpenvpnProfile>() {
		public OpenvpnProfile createFromParcel(Parcel in) {
			OpenvpnProfile p = new OpenvpnProfile();
			p.readFromParcel(in);
			return p;
		}

		public OpenvpnProfile[] newArray(int size) {
			return new OpenvpnProfile[size];
		}
	};

	public int describeContents() {
		return 0;
	}

	public void setPort(String port) {
		try {
			mPort = Integer.parseInt(port);
		} catch (NumberFormatException e) {
			// no update
		}
	}

	public String getPort() {
		return Integer.toString(mPort);
	}

	public String getProto() {
		return mProto;
	}

	public CharSequence[] getProtoList() {
		String[] s = new String[2];
		s[0] = PROTO_UDP;
		s[1] = PROTO_TCP;
		return s;
	}

	public void setProto(String p) {
		if (p.contains(PROTO_TCP))
			mProto = PROTO_TCP;
		else if (p.contains(PROTO_UDP))
			mProto = PROTO_UDP;
	}

	public boolean getUserAuth() {
		return mUserAuth;
	}

	public void setUserAuth(boolean auth) {
		mUserAuth = auth;
	}

	public byte[] getCertName() {
		if (mCert != null && mCert.length == 0)
			return null;
		return mCert;
	}

	public void setCertName(byte[] name) {
		mCert = name;
	}

	public String getUserCertName() {
		return mUserCert;
	}

	public void setUserCertName(String name) {
		mUserCert = name;
	}

	public void setUseCompLzo(boolean b) {
		mUseCompLzo = b;
	}

	public boolean getUseCompLzo() {
		return mUseCompLzo;
	}

	public String getNsCertType() {
		return mNsCertType;
	}

	public void setNsCertType(String type) {
		this.mNsCertType = type;
	}

	public void setRedirectGateway(boolean b) {
		mRedirectGateway = b;
	}

	public boolean getRedirectGateway() {
		return mRedirectGateway;
	}

	public void setSupplyAddr(boolean b) {
		mSupplyAddr = b;
	}

	public boolean getSupplyAddr() {
		return mSupplyAddr;
	}

	public void setLocalAddr(String addr) {
		mLocalAddr = addr;
	}

	public String getLocalAddr() {
		return mLocalAddr;
	}

	public void setRemoteAddr(String addr) {
		mRemoteAddr = addr;
	}

	public String getRemoteAddr() {
		return mRemoteAddr;
	}

	public void setCipher(String cipher) {
		mCipher = cipher;
	}

	public String getCipher() {
		return mCipher;
	}

	public void setKeySize(String keysize) {
		try {
			if (keysize.equals("0"))
				mKeySize = 0;
			else
				mKeySize = Integer.parseInt(keysize);
		} catch (NumberFormatException e) {
			// no update
		}
	}

	public String getKeySize() {
		return Integer.toString(mKeySize);
	}

	public void setUseTlsAuth(boolean t) {
		mUseTlsAuth = t;
	}

	public boolean getUseTlsAuth() {
		return mUseTlsAuth;
	}

	public void setTlsAuthKey(String k) {
		mTlsAuthKey = k;
	}

	public String getTlsAuthKey() {
		return mTlsAuthKey;
	}

	public void setTlsAuthKeyDirection(String d) {
		mTlsAuthKeyDirection = d;
	}

	public String getTlsAuthKeyDirection() {
		return mTlsAuthKeyDirection;
	}

	public void setExtra(String extra) {
		mExtra = extra;
	}

	public String getExtra() {
		return mExtra;
	}

	protected void readFromParcel(Parcel in) {
		mName = in.readString();
		mId = in.readString();
		mServerName = in.readString();
		mDomainSuffices = in.readString();
		mRouteList = in.readString();
		mSavedUsername = in.readString();
		mPort = in.readInt();
		mProto = in.readString();
		mUserAuth = in.readInt() == 1;
		mCert = new byte[in.readInt()];
		in.readByteArray(mCert);
		mUserCert = in.readString();
		mUseCompLzo = in.readInt() == 1;
		mNsCertType = in.readString();
		mRedirectGateway = in.readInt() == 1;
		mSupplyAddr = in.readInt() == 1;
		mLocalAddr = in.readString();
		mRemoteAddr = in.readString();
		mCipher = in.readString();
		mKeySize = in.readInt();
		mExtra = in.readString();
		mUseTlsAuth = in.readInt() == 1;
		mTlsAuthKey = in.readString();
		mTlsAuthKeyDirection = in.readString();
	}

	@Override
	public void writeToParcel(Parcel parcel, int flags) {
		parcel.writeString(mName);
		parcel.writeString(mId);
		parcel.writeString(mServerName);
		parcel.writeString(mDomainSuffices);
		parcel.writeString(mRouteList);
		parcel.writeString(mSavedUsername);
		parcel.writeInt(mPort);
		parcel.writeString(mProto);
		parcel.writeInt(mUserAuth ? 1 : 0);
		if (mCert != null) {
			parcel.writeInt(mCert.length);
			parcel.writeByteArray(mCert);
		} else {
			parcel.writeInt(0);
			parcel.writeByteArray(new byte[0]);
		}
		parcel.writeString(mUserCert);
		parcel.writeInt(mUseCompLzo ? 1 : 0);
		parcel.writeString(mNsCertType);
		parcel.writeInt(mRedirectGateway ? 1 : 0);
		parcel.writeInt(mSupplyAddr ? 1 : 0);
		parcel.writeString(mLocalAddr);
		parcel.writeString(mRemoteAddr);
		parcel.writeString(mCipher);
		parcel.writeInt(mKeySize);
		parcel.writeString(mExtra);
		parcel.writeInt(mUseTlsAuth ? 1 : 0);
		parcel.writeString(mTlsAuthKey);
		parcel.writeString(mTlsAuthKeyDirection);
	}
}
