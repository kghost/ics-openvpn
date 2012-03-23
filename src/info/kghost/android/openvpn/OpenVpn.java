package info.kghost.android.openvpn;

import java.io.IOException;
import java.nio.Buffer;
import java.nio.channels.spi.AbstractInterruptibleChannel;

public class OpenVpn {

	private ControlChannel control;
	private int pid = -1;

	private static native int start(String[] options,
			FileDescriptorHolder control) throws IOException;

	private static native void stop(int pid);

	public synchronized void start(String[] options) throws IOException {
		if (pid != -1) {
			throw new RuntimeException("Already started");
		}
		if (options == null) {
			throw new RuntimeException("Options is wrong");
		}
		FileDescriptorHolder out = new FileDescriptorHolder();
		pid = start(options, out);
		control = new ControlChannel(out);
	}

	public int recv(Buffer data, FileDescriptorHolder fd)
			throws InterruptedException, IOException {
		return control.recv(data, fd);
	}

	public int send(Buffer data) throws InterruptedException, IOException {
		return control.send(data);
	}

	public int send(Buffer data, FileDescriptorHolder fd)
			throws InterruptedException, IOException {
		return control.send(data, fd);
	}

	public synchronized void stop() {
		if (pid == -1) {
			throw new RuntimeException("Not start");
		}
		stop(pid);
		try {
			control.close();
		} catch (IOException e) {
		}
		pid = -1;
	}

	public boolean isStarted() {
		return pid != -1;
	}

	private static class ControlChannel extends AbstractInterruptibleChannel {
		private FileDescriptorHolder socket;

		private ControlChannel(FileDescriptorHolder socket) {
			super();
			this.socket = socket;
		}

		public int recv(Buffer data, FileDescriptorHolder fd)
				throws InterruptedException, IOException {
			begin();
			int count = -1;
			try {
				count = recv(socket, data, data.limit(),
						data.capacity() - data.limit(), fd);
			} finally {
				end(count >= 0);
			}
			if (count > 0) {
				data.limit(data.limit() + count);
			}
			return count;
		}

		public int send(Buffer data) throws InterruptedException, IOException {
			begin();
			int count = -1;
			try {
				count = send(socket, data, data.position(), data.remaining());
			} finally {
				end(count >= 0);
			}
			if (count > 0)
				data.position(data.position() + count);
			return count;
		}

		public int send(Buffer data, FileDescriptorHolder fd)
				throws InterruptedException, IOException {
			begin();
			int count = -1;
			try {
				count = send(socket, data, data.position(), data.remaining(),
						fd);
			} finally {
				end(count >= 0);
			}
			if (count > 0)
				data.position(data.position() + count);
			return count;
		}

		private static native int recv(FileDescriptorHolder socket,
				Buffer data, int offset, int length, FileDescriptorHolder fd)
				throws InterruptedException, IOException;

		private static native int send(FileDescriptorHolder socket,
				Buffer data, int offset, int length)
				throws InterruptedException, IOException;

		private static native int send(FileDescriptorHolder socket,
				Buffer data, int offset, int length, FileDescriptorHolder fd)
				throws InterruptedException, IOException;

		@Override
		protected void implCloseChannel() throws IOException {
			socket.shutdown();
		}
	}
}
