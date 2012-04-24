package info.kghost.android.openvpn;

import info.kghost.android.openvpn.LogQueue;
import info.kghost.android.openvpn.OpenvpnProfile;
import info.kghost.android.openvpn.VpnStatus;

/**
 * Interface to access a VPN service.
 * {@hide}
 */
interface IVpnService {
    /**
     * Sets up the VPN connection.
     * @param profile the profile object
     */
    boolean connect(in OpenvpnProfile profile, in String username, in String password);

    /**
     * Tears down the VPN connection.
     */
    void disconnect();

    /**
     * Makes the service broadcast the connectivity state.
     */
    VpnStatus checkStatus();

    /**
     * Get current/last connection log
     */
    LogQueue getLog();
}
