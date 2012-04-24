package info.kghost.android.openvpn;

import android.os.Parcel;
import android.os.Parcelable;

/**
 * Enumeration of all VPN states.
 * 
 * A normal VPN connection lifetime starts in {@link IDLE}. When a new
 * connection is about to be set up, it goes to {@link CONNECTING} and then
 * {@link CONNECTED} if successful; back to {@link IDLE} if failed. When the
 * connection is about to be torn down, it goes to {@link DISCONNECTING} and
 * then {@link IDLE}. {@link CANCELLED} is a state when a VPN connection attempt
 * is aborted, and is in transition to {@link IDLE}. The {@link UNUSABLE} state
 * indicates that the profile is not in a state for connecting due to possibly
 * the integrity of the fields or another profile is connecting etc. The
 * {@link UNKNOWN} state indicates that the profile state is to be determined.
 * {@hide}
 */
public class VpnStatus implements Parcelable {
	public enum VpnState {
		PREPARING, CONNECTING, DISCONNECTING, CANCELLED, CONNECTED, IDLE, UNUSABLE, UNKNOWN
	}

	public String name;
	public VpnState state;
	public String error;

	public static final Parcelable.Creator<VpnStatus> CREATOR = new Parcelable.Creator<VpnStatus>() {
		public VpnStatus createFromParcel(Parcel in) {
			VpnStatus p = new VpnStatus();
			p.readFromParcel(in);
			return p;
		}

		public VpnStatus[] newArray(int size) {
			return new VpnStatus[size];
		}
	};

	public void writeToParcel(Parcel parcel, int flags) {
		parcel.writeString(name);
		parcel.writeInt(state.ordinal());
		parcel.writeString(error);
	}

	protected void readFromParcel(Parcel parcel) {
		name = parcel.readString();
		state = VpnState.values()[parcel.readInt()];
		error = parcel.readString();
	}

	public int describeContents() {
		return 0;
	}
}
