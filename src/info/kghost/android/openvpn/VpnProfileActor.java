package info.kghost.android.openvpn;

import android.app.Dialog;
import android.view.View;

/**
 * The interface to act on a {@link VpnProfile}.
 */
public interface VpnProfileActor {
    VpnProfile getProfile();

    /**
     * Returns true if a connect dialog is needed before establishing a
     * connection.
     */
    boolean isConnectDialogNeeded();

    /**
     * Creates the view in the connect dialog.
     */
    View createConnectView();

    /**
     * Validates the inputs in the dialog.
     * @param dialog the connect dialog
     * @return an error message if the inputs are not valid
     */
    String validateInputs(Dialog dialog);

    /**
     * Establishes a VPN connection.
     * @param dialog the connect dialog
     */
    void connect(Dialog dialog);

    /**
     * Tears down the connection.
     */
    void disconnect();

    /**
     * Checks the current status. The result is expected to be broadcast.
     * Use {@link VpnManager#registerConnectivityReceiver()} to register a
     * broadcast receiver and to receives the broadcast events.
     */
    void checkStatus();
}
