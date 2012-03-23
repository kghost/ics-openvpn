/*
 * Copyright (C) 2011 The Android Open Source Project
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

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousCloseException;
import java.nio.channels.ClosedByInterruptException;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.ArrayList;

import android.content.Intent;
import android.net.VpnService;
import android.security.KeyChain;
import android.util.Log;

public class OpenVpnService extends VpnService implements Runnable {
	private Thread mThread = new Thread(this);

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
			PrivateKey pk = KeyChain.getPrivateKey(OpenVpnService.this,
					profile.getUserCertName());
			X509Certificate[] chain = KeyChain.getCertificateChain(
					OpenVpnService.this, profile.getUserCertName());

			KeyStore pkcs12Store = KeyStore.getInstance("PKCS12");
			pkcs12Store.load(null, null);
			pkcs12Store.setKeyEntry("cert", pk, null, chain);
			File tmp = new File(OpenVpnService.this.getCacheDir(), "tmp.pfx");
			FileOutputStream f = new FileOutputStream(tmp);
			pkcs12Store.store(f, "".toCharArray());
			f.close();

			config.add("--pkcs12");
			config.add(tmp.getAbsolutePath());
		} catch (Exception e) {
			Log.w(OpenVpnService.class.getName(), "Error generate pkcs12", e);
		}

		return config.toArray(new String[0]);
	}

	@Override
	public void run() {
		if (vpn != null) {
			if (vpn.isStarted())
				vpn.stop();
			vpn = null;
		}

		try {
			VpnService.Builder builder = new VpnService.Builder();
			vpn = new OpenVpn();
			OpenvpnProfile profile = intent.getParcelableExtra(getPackageName()
					+ ".CONF");

			vpn.start(prepare(profile));

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
						builder.addAddress(InetAddress.getByAddress(ip), mask);
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
			this.stopSelf();
		}
	}

	private boolean stop = false;
	private Intent intent = null;
	private OpenVpn vpn;

	static {
		System.loadLibrary("jni_openvpn");
	}

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		this.intent = intent;
		if (mThread.isAlive()) {
			return START_STICKY;
		} else {
			mThread.start();
			return START_STICKY;
		}
	}

	@Override
	public void onDestroy() {
		stop = true;
		if (vpn != null) {
			if (vpn.isStarted())
				vpn.stop();
			vpn = null;
		}
		mThread.interrupt();
		try {
			mThread.join();
		} catch (InterruptedException e) {
			throw new RuntimeException(e);
		}
		intent = null;
	}
}
