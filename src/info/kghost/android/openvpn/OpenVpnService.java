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

import java.io.IOException;
import java.net.InetAddress;
import java.nio.ByteBuffer;

import android.content.Intent;
import android.net.VpnService;

public class OpenVpnService extends VpnService {
	private Thread mThread = new Thread(new Runnable() {
		@Override
		public void run() {
			if (vpn != null && vpn.isStarted()) {
				vpn.stop();
				vpn = null;
			}

			VpnService.Builder builder = new VpnService.Builder();
			vpn = new OpenVpn();
			try {
				String[] config = { "--config", "/sdcard/openvpn/test.conf", };
				vpn.start(config);

				ByteBuffer buffer = ByteBuffer.allocateDirect(2000);
				while (!stop) {
					try {
						buffer.position(0);
						buffer.limit(0);
						FileDescriptorHolder fd = new FileDescriptorHolder();
						vpn.recv(buffer, fd);
						switch (buffer.get()) {
						case 'T': {
							buffer.clear();
							buffer.put((byte) 0x74); // 't'
							buffer.flip();
							if (!fd.valid())
								throw new RuntimeException(
										"remote fd not valid !!!");
							OpenVpnService.this.protect(fd.get());
							fd.close();
							FileDescriptorHolder tun = new FileDescriptorHolder(
									builder.establish().detachFd());
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
							builder.addAddress(InetAddress.getByAddress(ip),
									mask);
							break;
						}
						case 'M': {
							int mtu = buffer.getInt();
							builder.setMtu(mtu);
							break;
						}
						}
					} catch (InterruptedException e) {

					}
				}

				if (vpn != null && vpn.isStarted()) {
					vpn.stop();
					vpn = null;
				}
			} catch (IOException e) {
				throw new RuntimeException(e);
			}

		}
	});
	private boolean stop = false;
	private OpenVpn vpn;

	static {
		System.loadLibrary("jni_openvpn");
	}

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		mThread.start();
		return START_STICKY;
	}

	@Override
	public void onDestroy() {
		stop = true;
		mThread.interrupt();
		try {
			mThread.join();
		} catch (InterruptedException e) {
			throw new RuntimeException(e);
		}
	}
}
