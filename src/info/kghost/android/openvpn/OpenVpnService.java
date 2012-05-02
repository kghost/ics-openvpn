package info.kghost.android.openvpn;

import info.kghost.android.openvpn.VpnStatus.VpnState;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
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

import org.spongycastle.openssl.PEMWriter;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.net.VpnService;
import android.os.AsyncTask;
import android.os.IBinder;
import android.os.Parcel;
import android.os.Parcelable;
import android.os.RemoteException;
import android.security.KeyChain;
import android.util.Base64;
import android.util.Log;

public class OpenVpnService extends VpnService {
	class Task extends AsyncTask<Object, VpnStatus.VpnState, String> {
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

		private String[] prepare(OpenvpnProfile profile) throws Exception {
			ArrayList<String> config = new ArrayList<String>();
			config.add(new File(getCacheDir(), "openvpn").getAbsolutePath());
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

			if (profile.getUserAuth()) {
				config.add("--auth-user-pass");
			}

			if (profile.getLocalAddr() != null) {
				config.add("--local");
				config.add(profile.getLocalAddr());
			} else {
				config.add("--nobind");
			}

			config.add("--proto");
			config.add(profile.getProto());

			config.add("--remote");
			config.add(profile.getServerName());
			config.add(profile.getPort());

			if (profile.getUseCompLzo())
				config.add("--comp-lzo");

			if (!"None".equals(profile.getNsCertType())) {
				config.add("--ns-cert-type");
				config.add(profile.getNsCertType());
			}

			if (profile.getRedirectGateway()) {
				config.add("--redirect-gateway");
			}

			if (profile.getCipher() != null) {
				config.add("--cipher");
				config.add(profile.getCipher());
			}

			if (!profile.getKeySize().equals("0")) {
				config.add("--keysize");
				config.add(profile.getKeySize());
			}

			try {
				if (profile.getUserCertName() != null) {
					KeyStore pkcs12Store = KeyStore.getInstance("PKCS12");
					pkcs12Store.load(null, null);

					PrivateKey pk = KeyChain.getPrivateKey(OpenVpnService.this,
							profile.getUserCertName());
					X509Certificate[] chain = KeyChain.getCertificateChain(
							OpenVpnService.this, profile.getUserCertName());
					pkcs12Store.setKeyEntry("key", pk, null, chain);

					if (profile.getCertName() != null) {
						KeyStore localTrustStore = KeyStore.getInstance("BKS");
						localTrustStore
								.load(new ByteArrayInputStream(profile
										.getCertName()), null);
						Certificate root = localTrustStore.getCertificate("c");
						if (root != null)
							pkcs12Store.setCertificateEntry("root", root);
					}

					ByteArrayOutputStream f = new ByteArrayOutputStream();
					pkcs12Store.store(f, "".toCharArray());

					config.add("--pkcs12");
					config.add("[[INLINE]]");
					config.add(Base64.encodeToString(f.toByteArray(),
							Base64.DEFAULT));
					f.close();
				} else if (profile.getCertName() != null) {
					KeyStore localTrustStore = KeyStore.getInstance("BKS");
					localTrustStore.load(
							new ByteArrayInputStream(profile.getCertName()),
							null);
					Certificate root = localTrustStore.getCertificate("c");

					if (root == null)
						throw new RuntimeException(
								"Certificate authority error");
					StringWriter s = new StringWriter();
					PEMWriter w = new PEMWriter(s);
					w.writeObject(root);
					w.flush();
					config.add("--ca");
					config.add("[[INLINE]]");
					config.add("# CA cert below\n" + s.toString());
					w.close();
				}
			} catch (Exception e) {
				Log.w(OpenVpnService.class.getName(),
						"Error passing certifications", e);
				throw e;
			}

			if (profile.getUseTlsAuth()) {
				config.add("--tls-auth");
				config.add(profile.getTlsAuthKey());
				if (!"None".equals(profile.getTlsAuthKeyDirection()))
					config.add(profile.getTlsAuthKeyDirection());
			}

			if (profile.getExtra() != null)
				for (String s : profile.getExtra().trim()
						.split(" +(?=([^\"]*\"[^\"]*\")*[^\"]*$)"))
					config.add(s);

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
			VpnService.Builder builder = null;
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
						if (cmd.startsWith(">LOG:")) {
							log.add(cmd.substring(">LOG:".length()));
							Log.i(getClass().getName(), cmd);
						} else if (cmd.startsWith(">INFO:")) {
							Log.i(getClass().getName(), cmd);
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
								Log.i(getClass().getName(), "Ignore ECHO: "
										+ cmd);
							}
						} else if (cmd
								.equals(">NEED-TUN:Need 'TUN' confirmation")) {
							FileDescriptorHolder tun = new FileDescriptorHolder(
									builder.establish().detachFd());
							sock.write(str_to_bb("tun TUN ok\n"), tun);
							tun.close();
						} else if (cmd.startsWith(">STATE:")) {
							int start = cmd.indexOf(',');
							int end = cmd.indexOf(',', start + 1);
							String state = cmd.substring(start + 1, end);
							if (state.equals("GET_CONFIG")) {
								builder = new VpnService.Builder();
							} else if (state.equals("CONNECTED")) {
								builder = null;
								publishProgress(VpnState.CONNECTED);
							}
						} else if (cmd.startsWith(">PASSWORD:")) {
							String c = cmd.substring(">PASSWORD:".length());
							int first = c.indexOf('\'');
							int second = c.indexOf('\'', first + 1);
							final String authType = c.substring(first + 1,
									second);

							if (c.startsWith("Need")) {
								sock.write(str_to_bb("username '"
										+ authType
										+ "' \""
										+ username.replace("\"", "\\\"")
												.replace("\\", "\\\\")
										+ "\"\n"
										+ "password '"
										+ authType
										+ "' '"
										+ password.replace("\"", "\\\"")
												.replace("\\", "\\\\") + "'\n"));
							} else {
								throw new RuntimeException("Password Error");
							}
						} else {
							if (fd.valid())
								Log.w(getClass().getName(), "Unknown Command: "
										+ cmd + " (fd: " + fd.get() + ")");
							else
								Log.w(getClass().getName(), "Unknown Command: "
										+ cmd);
						}
					}
				} finally {
					if (fd.valid())
						throw new RuntimeException("Unexpected fd");
				}
			}

		}

		@Override
		protected String doInBackground(Object... params) {
			publishProgress(VpnState.PREPARING);

			try {
				process = Runtime.getRuntime().exec(prepare(profile));
				for (int i = 0; i < 30 && isProcessAlive(process)
						&& sock == null; ++i)
					try { // Wait openvpn to create management socket
						sock = new ManagementSocket(managementPath);
					} catch (Exception e) {
						Thread.sleep(1000);
					}
				if (sock == null) {
					InputStream stdout = process.getInputStream();
					byte[] buffer = new byte[stdout.available()];
					stdout.read(buffer);
					for (String s : new String(buffer, "UTF-8")
							.split("\\r?\\n")) {
						log.add(s);
					}
					if (isProcessAlive(process))
						process.destroy();
					return "Failed to start openvpn process";
				}
				publishProgress(VpnState.CONNECTING);
				try {
					doCommands();
				} finally {
					synchronized (this) {
						sock.shutdownAll();
						sock.close();
						sock = null;
					}
				}
				return null;
			} catch (Exception e) {
				publishProgress(VpnState.UNUSABLE);
				Log.wtf(getClass().getName(), e);
				return e.getLocalizedMessage();
			} finally {
				publishProgress(VpnState.DISCONNECTING);
				try {
					if (process != null)
						process.waitFor();
				} catch (InterruptedException e) {
					Log.wtf(getClass().getName(), e);
					return e.getLocalizedMessage();
				}
				publishProgress(VpnState.IDLE);
			}
		}

		public synchronized void interrupt() {
			if (sock != null)
				try {
					sock.write(str_to_bb("exit\n"));
				} catch (CharacterCodingException e) {
					Log.wtf(getClass().getName(), "WTF", e);
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
			log = new LogQueue(63);

			update(R.string.vpn_preparing);
		}

		@Override
		protected void onPostExecute(String result) {
			stopForeground(true);
			if (result == null)
				return;

			Intent intent = new Intent(OpenVpnService.this, VpnSettings.class);
			intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP
					| Intent.FLAG_ACTIVITY_SINGLE_TOP);
			PendingIntent pendIntent = PendingIntent.getActivity(
					OpenVpnService.this, 0, intent, 0);

			Notification notice = new Notification.Builder(OpenVpnService.this)
					.setSmallIcon(R.drawable.openvpn_icon).setTicker("OpenVPN")
					.setWhen(System.currentTimeMillis())
					.setContentTitle(getString(R.string.vpn_error))
					.setContentText(result).setContentIntent(pendIntent)
					.getNotification();

			((NotificationManager) getSystemService(NOTIFICATION_SERVICE))
					.notify(91684, notice);
			Util.showLongToastMessage(OpenVpnService.this, result);
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
			sendBroadcast(intent);

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
	private LogQueue log = null;

	static {
		System.loadLibrary("jni_openvpn");
	}

	@Override
	public IBinder onBind(Intent intent) {
		return mBinder;
	}

	private final IVpnService.Stub mBinder = new IVpnService.Stub() {
		@Override
		public boolean connect(OpenvpnProfile profile, String username,
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

		@Override
		public LogQueue getLog() throws RemoteException {
			return log;
		}
	};

	private void restoreLog() {
		File logfile = new File(getCacheDir(), "log.2");
		if (logfile.exists()) {
			int length;
			InputStream is = null;
			try {
				is = new FileInputStream(logfile);
				length = (int) logfile.length();
				byte[] bytes = new byte[(int) length];
				int offset = 0;
				int numRead = 0;
				while (offset < length
						&& (numRead = is.read(bytes, offset, length - offset)) >= 0) {
					offset += numRead;
				}
				if (offset == length) {
					Parcel parcel = Parcel.obtain();
					parcel.unmarshall(bytes, 0, length);
					parcel.setDataPosition(0);
					Parcelable o = parcel.readParcelable(LogQueue.class
							.getClassLoader());
					if (o instanceof LogQueue) {
						log = (LogQueue) o;
					}
				}
			} catch (IOException e) {
			} finally {
				if (is != null)
					try {
						is.close();
					} catch (IOException e) {
					}
			}
		}
	}

	private void saveLog() {
		if (log != null) {
			File logfile = new File(getCacheDir(), "log.2");
			Parcel parcel = Parcel.obtain();
			parcel.writeParcelable(log, 0);
			byte[] bytes = parcel.marshall();
			FileOutputStream os = null;
			try {
				os = new FileOutputStream(logfile);
				os.write(bytes);
			} catch (IOException e) {
			} finally {
				if (os != null)
					try {
						os.close();
					} catch (IOException e) {
					}
			}
		}
	}

	@Override
	public void onCreate() {
		super.onCreate();
		restoreLog();
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
		saveLog();
		super.onDestroy();
	}
}
