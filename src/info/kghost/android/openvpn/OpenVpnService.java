package info.kghost.android.openvpn;

import info.kghost.android.openvpn.VpnStatus.VpnState;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousCloseException;
import java.nio.channels.ClosedByInterruptException;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import android.app.Notification;
import android.app.PendingIntent;
import android.content.Intent;
import android.net.VpnService;
import android.os.AsyncTask;
import android.os.IBinder;
import android.os.RemoteException;
import android.security.KeyChain;
import android.util.Base64;
import android.util.Log;

public class OpenVpnService extends VpnService {
	class Task extends AsyncTask<OpenvpnProfile, VpnStatus.VpnState, Object> {
		private OpenVpn vpn;
		private boolean stop = false;

		private String[] prepare(OpenvpnProfile profile) {
			ArrayList<String> config = new ArrayList<String>();
			config.add("--client");
			config.add("--tls-client");

			config.add("--dev-type");
			config.add("tun");

			if (profile.getLocalAddr() != null) {
				config.add("--local");
				config.add(profile.getLocalAddr());
			} else {
				config.add("--nobind");
			}

			config.add("--ns-cert-type");
			config.add("server");

			config.add("--proto");
			config.add(profile.getProto());

			config.add("--remote");
			config.add(profile.getServerName());
			config.add(profile.getPort());

			if (profile.getUseCompLzo())
				config.add("--comp-lzo");

			if (profile.getCipher() != null) {
				config.add("--cipher");
				config.add(profile.getCipher());
			}

			if (!profile.getKeySize().equals("0")) {
				config.add("--keysize");
				config.add(profile.getKeySize());
			}

			try {
				KeyStore pkcs12Store = KeyStore.getInstance("PKCS12");
				pkcs12Store.load(null, null);

				PrivateKey pk = KeyChain.getPrivateKey(OpenVpnService.this,
						profile.getUserCertName());
				X509Certificate[] chain = KeyChain.getCertificateChain(
						OpenVpnService.this, profile.getUserCertName());
				pkcs12Store.setKeyEntry("key", pk, null, chain);

				if (profile.getCertName() != null) {
					KeyStore localTrustStore = KeyStore.getInstance("BKS");
					localTrustStore.load(
							new ByteArrayInputStream(profile.getCertName()),
							null);
					Certificate root = localTrustStore.getCertificate("c");
					if (root != null)
						pkcs12Store.setCertificateEntry("root", root);
				}

				ByteArrayOutputStream f = new ByteArrayOutputStream();
				pkcs12Store.store(f, "".toCharArray());

				config.add("--pkcs12");
				config.add("[[INLINE]]");
				byte[] bytes = f.toByteArray();
				config.add(Base64.encodeToString(bytes, Base64.NO_PADDING
						| Base64.NO_WRAP));
				f.close();
			} catch (Exception e) {
				Log.w(OpenVpnService.class.getName(), "Error generate pkcs12",
						e);
			}

			return config.toArray(new String[0]);
		}

		@Override
		protected Object doInBackground(OpenvpnProfile... params) {
			this.publishProgress(VpnState.PREPARING);
			OpenvpnProfile profile = params[0];

			try {
				VpnService.Builder builder = new VpnService.Builder();
				String[] args = prepare(profile);

				this.publishProgress(VpnState.CONNECTING);
				vpn = new OpenVpn();
				vpn.start(
						new File(OpenVpnService.this.getCacheDir(), "openvpn")
								.getAbsolutePath(), args);

				ByteBuffer buffer = ByteBuffer.allocateDirect(2000);
				while (!stop) {
					try {
						buffer.position(0);
						buffer.limit(0);
						FileDescriptorHolder fd = new FileDescriptorHolder();
						if (vpn.recv(buffer, fd) <= 0) {
							stop = true;
							break;
						}
						switch (buffer.get()) {
						case 'T': {
							if (!fd.valid())
								throw new RuntimeException(
										"remote fd not valid !!!");
							OpenVpnService.this.protect(fd.get());
							fd.close();
							FileDescriptorHolder tun = new FileDescriptorHolder(
									builder.establish().detachFd());

							buffer.clear();
							buffer.put((byte) 0x74); // 't'
							buffer.flip();
							vpn.send(buffer, tun);
							tun.close();
							this.publishProgress(VpnState.CONNECTED);
							break;
						}
						case 'R': {
							byte[] ip = new byte[4];
							buffer.get(ip);
							int mask = buffer.getInt();
							builder.addRoute(InetAddress.getByAddress(ip), mask);
							break;
						}
						case 'A': {
							byte[] ip = new byte[4];
							buffer.get(ip);
							int mask = buffer.getInt();
							builder.addAddress(InetAddress.getByAddress(ip),
									mask);
							break;
						}
						case 'D': {
							byte[] ip = new byte[4];
							buffer.get(ip);
							builder.addDnsServer(InetAddress.getByAddress(ip));
							break;
						}
						case 'M': {
							int mtu = buffer.getInt();
							builder.setMtu(mtu);
							break;
						}
						}
					} catch (InterruptedException e) {
						stop = true;
						break;
					} catch (ClosedByInterruptException e) {
						stop = true;
						break;
					} catch (AsynchronousCloseException e) {
						stop = true;
						break;
					}
				}
			} catch (IOException e) {
				Log.wtf(this.getClass().getName(), e);
			} finally {
				if (vpn != null) {
					if (vpn.isStarted())
						vpn.stop();
					vpn = null;
				}
				this.publishProgress(VpnState.IDLE);
			}
			return null;
		}

		public void interrupt() {
			stop = true;
			if (vpn != null) {
				if (vpn.isStarted())
					vpn.stop();
				vpn = null;
			}
		}

		private void update(int resId) {
			// The intent to launch when the user clicks the expanded
			// notification
			Intent intent = new Intent(OpenVpnService.this, VpnSettings.class);
			intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP
					| Intent.FLAG_ACTIVITY_SINGLE_TOP);
			PendingIntent pendIntent = PendingIntent.getActivity(
					OpenVpnService.this, 0, intent, 0);

			Notification notice = new Notification.Builder(OpenVpnService.this)
					.setSmallIcon(R.drawable.openvpn_icon).setTicker("OpenVPN")
					.setWhen(System.currentTimeMillis())
					.setContentTitle(getString(resId))
					.setContentText(getString(resId))
					.setContentIntent(pendIntent).setOngoing(true)
					.getNotification();

			startForeground(48998, notice);
		}

		@Override
		protected void onPreExecute() {
			update(R.string.vpn_preparing);
		}

		@Override
		protected void onPostExecute(Object result) {
			stopForeground(true);
		}

		@Override
		protected void onCancelled() {
			stopForeground(true);
		}

		@Override
		protected void onProgressUpdate(VpnStatus.VpnState... state) {
			mState = state[0];

			Intent intent = new Intent(
					"info.kghost.android.openvpn.connectivity");
			VpnStatus s = new VpnStatus();
			if (mProfile != null)
				s.name = mProfile.getName();
			s.state = mState;
			intent.putExtra("connection_state", s);
			OpenVpnService.this.sendBroadcast(intent);

			switch (mState) {
			case PREPARING:
				update(R.string.vpn_preparing);
				break;
			case CONNECTING:
				update(R.string.vpn_connecting);
				break;
			case DISCONNECTING:
			case CANCELLED:
				update(R.string.vpn_disconnecting);
				break;
			case CONNECTED:
				update(R.string.vpn_connected);
				break;
			case IDLE:
				update(R.string.vpn_disconnected);
				break;
			case UNUSABLE:
				break;
			case UNKNOWN:
				break;
			}
		}
	};

	private OpenvpnProfile mProfile = null;
	private VpnStatus.VpnState mState = null;
	private ExecutorService executor;
	private Task mTask = null;

	static {
		System.loadLibrary("jni_openvpn");
	}

	@Override
	public IBinder onBind(Intent intent) {
		return mBinder;
	}

	private final IVpnService.Stub mBinder = new IVpnService.Stub() {
		@Override
		public boolean connect(VpnProfile profile) throws RemoteException {
			mProfile = (OpenvpnProfile) profile;
			if (mProfile == null)
				return false;
			if (mTask != null && mTask.getStatus() == AsyncTask.Status.FINISHED)
				mTask = null;
			if (mTask == null)
				mTask = new Task();
			if (mTask.getStatus() == AsyncTask.Status.PENDING) {
				mTask.executeOnExecutor(executor, mProfile);
				return true;
			}
			return false;
		}

		@Override
		public void disconnect() throws RemoteException {
			if (mTask != null)
				mTask.interrupt();
			mProfile = null;
		}

		@Override
		public VpnStatus checkStatus() throws RemoteException {
			VpnStatus s = new VpnStatus();
			if (mProfile != null)
				s.name = mProfile.getName();
			if (mState != null)
				s.state = mState;
			else
				s.state = VpnStatus.VpnState.IDLE;
			return s;
		}
	};

	@Override
	public void onCreate() {
		super.onCreate();
		executor = Executors.newSingleThreadExecutor();
	}

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		return START_STICKY;
	}

	@Override
	public void onRevoke() {
		if (mTask != null)
			mTask.interrupt();
	}

	@Override
	public void onDestroy() {
		if (mTask != null)
			mTask.interrupt();
		executor = null;
		super.onDestroy();
	}
}
