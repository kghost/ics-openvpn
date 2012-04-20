package info.kghost.android.openvpn;

import info.kghost.android.openvpn.VpnStatus.VpnState;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.net.Inet4Address;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.CoderResult;
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
	class Task extends AsyncTask<Object, VpnStatus.VpnState, Object> {
		private Process process;
		private ManagementSocket sock;
		private OpenvpnProfile profile;
		private String username;
		private String password;
		private Charset charset = Charset.forName("UTF-8");

		public Task(OpenvpnProfile profile, String username, String password) {
			this.profile = profile;
			this.username = username;
			this.password = password;
		}

		private String[] prepare(OpenvpnProfile profile) {
			ArrayList<String> config = new ArrayList<String>();
			config.add(new File(OpenVpnService.this.getCacheDir(), "openvpn")
					.getAbsolutePath());
			config.add("--client");
			config.add("--tls-client");

			config.add("--script-security");
			config.add("0");

			config.add("--management");
			config.add(managementPath.getAbsolutePath());
			config.add("unixseq");
			config.add("--management-query-passwords");
			config.add("--management-hold");
			config.add("--management-signal");
			config.add("--remap-usr1");
			config.add("SIGTERM");
			config.add("--route-noexec");
			config.add("--ifconfig-noexec");
			config.add("--verb");
			config.add("3");

			config.add("--dev");
			config.add("[[ANDROID]]");
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
				f.close();
				config.add(Base64.encodeToString(bytes, Base64.DEFAULT));
			} catch (Exception e) {
				Log.w(OpenVpnService.class.getName(), "Error generate pkcs12",
						e);
			}

			return config.toArray(new String[0]);
		}

		private boolean isProcessAlive(Process process) {
			try {
				process.exitValue();
				return false;
			} catch (IllegalThreadStateException e) {
				return true;
			}
		}

		private ByteBuffer str_to_bb(String msg)
				throws CharacterCodingException {
			CharsetEncoder encoder = charset.newEncoder();
			ByteBuffer buffer = ByteBuffer
					.allocateDirect(((int) (msg.length() * encoder
							.maxBytesPerChar())) + 1);
			CoderResult result = encoder.encode(CharBuffer.wrap(msg), buffer,
					true);
			if (!result.isUnderflow())
				result.throwException();
			result = encoder.flush(buffer);
			if (!result.isUnderflow())
				result.throwException();
			buffer.flip();
			return buffer;
		}

		private String bb_to_str(ByteBuffer buffer)
				throws CharacterCodingException {
			CharsetDecoder decoder = charset.newDecoder();
			return decoder.decode(buffer).toString();
		}

		private int netmaskToPrefixLength(String netmask)
				throws UnknownHostException {
			byte[] mask = Inet4Address.getByName(netmask).getAddress();
			if (mask.length != 4) {
				throw new IllegalArgumentException("Not an IPv4 address");
			}
			int mask_int = ((mask[3] & 0xff) << 24) | ((mask[2] & 0xff) << 16)
					| ((mask[1] & 0xff) << 8) | (mask[0] & 0xff);
			return Integer.bitCount(mask_int);
		}

		private void doCommands() throws CharacterCodingException,
				UnknownHostException {
			ByteBuffer buffer = ByteBuffer.allocateDirect(2000);
			VpnService.Builder builder = new VpnService.Builder();
			while (true) {
				buffer.limit(0);
				FileDescriptorHolder fd = new FileDescriptorHolder();
				try {
					int read = sock.read(buffer, fd);
					if (read <= 0)
						break;
					String lines[] = bb_to_str(buffer).split("\\r?\\n");
					for (int i = 0; i < lines.length; ++i) {
						String cmd = lines[i];
						if (cmd.startsWith(">INFO:")) {
							Log.i(this.getClass().getName(), cmd);
						} else if (cmd.equals(">HOLD:Waiting for hold release")) {
							sock.write(str_to_bb("echo on all\n"
									+ "log on all\n" + "state on all\n"
									+ "hold release\n"));
						} else if (cmd.startsWith(">ECHO:")) {
							String c = cmd.substring(cmd.indexOf(',') + 1);
							if (c.startsWith("tun-protect")) {
								protect(fd.get());
								fd.close();
							} else if (c.startsWith("tun-ip ")) {
								String[] ip = c.substring("tun-ip ".length())
										.split(" ");
								builder.addAddress(ip[0], 32);
							} else if (c.startsWith("tun-mtu ")) {
								builder.setMtu(Integer.parseInt(c
										.substring("tun-mtu ".length())));
							} else if (c.startsWith("tun-route ")) {
								String[] route = c.substring(
										"tun-route ".length()).split(" ");
								builder.addRoute(route[0],
										netmaskToPrefixLength(route[1]));
							} else if (c.startsWith("tun-redirect-gateway")) {
								builder.addRoute("0.0.0.0", 0);
							} else if (c.startsWith("tun-dns ")) {
								String dns = c.substring("tun-dns ".length());
								builder.addDnsServer(dns);
							} else {
								Log.i(this.getClass().getName(),
										"Ignore ECHO: " + cmd);
							}
						} else if (cmd
								.equals(">NEED-TUN:Need 'TUN' confirmation")) {
							FileDescriptorHolder tun = new FileDescriptorHolder(
									builder.establish().detachFd());
							sock.write(str_to_bb("tun TUN ok\n"), tun);
							tun.close();
						} else {
							if (fd.valid())
								Log.w(this.getClass().getName(),
										"Unknown Command: " + cmd + " (fd: "
												+ fd.get() + ")");
							else
								Log.w(this.getClass().getName(),
										"Unknown Command: " + cmd);
						}
					}
				} finally {
					if (fd.valid())
						throw new RuntimeException("Unexpected fd");
				}
			}

		}

		@Override
		protected Object doInBackground(Object... params) {
			this.publishProgress(VpnState.PREPARING);

			try {
				String[] args = prepare(profile);

				this.publishProgress(VpnState.CONNECTING);
				process = Runtime.getRuntime().exec(args);

				for (int i = 0; i < 30 && isProcessAlive(process)
						&& sock == null; ++i)
					try { // Wait openvpn to create management socket
						sock = new ManagementSocket(managementPath);
					} catch (Exception e) {
						Thread.sleep(1000);
					}
				if (sock == null) {
					if (isProcessAlive(process))
						process.destroy();
					return null;
				}
				try {
					doCommands();
				} finally {
					synchronized (this) {
						sock.shutdownAll();
						sock.close();
						sock = null;
					}
				}
			} catch (Exception e) {
				this.publishProgress(VpnState.UNUSABLE);
				Log.wtf(this.getClass().getName(), e);
			} finally {
				this.publishProgress(VpnState.DISCONNECTING);
				try {
					process.waitFor();
				} catch (InterruptedException e) {
					throw new RuntimeException(e);
				}
				this.publishProgress(VpnState.IDLE);
			}
			return null;
		}

		public synchronized void interrupt() {
			if (sock != null)
				sock.shutdownAll();
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
			if (profile != null)
				s.name = profile.getName();
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
				update(R.string.vpn_unusable);
				break;
			case UNKNOWN:
				break;
			}
		}
	};

	private String mName = null;
	private VpnStatus.VpnState mState = null;
	private ExecutorService executor;
	private Task mTask = null;
	private File managementPath = null;

	static {
		System.loadLibrary("jni_openvpn");
	}

	@Override
	public IBinder onBind(Intent intent) {
		return mBinder;
	}

	private final IVpnService.Stub mBinder = new IVpnService.Stub() {
		@Override
		public boolean connect(VpnProfile profile, String username,
				String password) throws RemoteException {
			if (profile == null)
				return false;
			if (mTask != null && mTask.getStatus() == AsyncTask.Status.FINISHED)
				mTask = null;
			if (mTask == null)
				mTask = new Task((OpenvpnProfile) profile, username, password);
			if (mTask.getStatus() == AsyncTask.Status.PENDING) {
				mName = profile.getName();
				mTask.executeOnExecutor(executor);
				return true;
			}
			return false;
		}

		@Override
		public void disconnect() throws RemoteException {
			if (mTask != null)
				mTask.interrupt();
			mName = null;
		}

		@Override
		public VpnStatus checkStatus() throws RemoteException {
			VpnStatus s = new VpnStatus();
			s.name = mName;
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
		managementPath = new File(getCacheDir(), "manage");
		executor = Executors.newSingleThreadExecutor();
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
