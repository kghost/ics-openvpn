package info.kghost.android.openvpn;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;

class ManagementSocket {
	protected int socket;

	protected native static int open(String socketFile);

	protected native static int read(int socket, ByteBuffer data, int offset,
			int length);

	protected native static int read(int socket, ByteBuffer data, int offset,
			int length, FileDescriptorHolder fd);

	protected native static int write(int socket, ByteBuffer data, int offset,
			int length);

	protected native static int write(int socket, ByteBuffer data, int offset,
			int length, FileDescriptorHolder fd);

	protected native static int close(int socket);

	protected native static int shutdown(int socket, int how);

	public ManagementSocket(File file) throws IOException {
		socket = open(file.getAbsolutePath());
	}

	public void close() {
		close(socket);
	}

	public int shutdownInput() {
		return shutdown(socket, 0);
	}

	public int shutdownOutput() {
		return shutdown(socket, 1);
	}

	public int shutdownAll() {
		return shutdown(socket, 2);
	}

	public int read(ByteBuffer data) {
		int read = read(socket, data, data.limit(),
				data.capacity() - data.limit());
		if (read > 0) {
			data.limit(data.limit() + read);
		}
		return read;
	}

	public int read(ByteBuffer data, FileDescriptorHolder fd) {
		int read = read(socket, data, data.limit(),
				data.capacity() - data.limit(), fd);
		if (read > 0) {
			data.limit(data.limit() + read);
		}
		return read;
	}

	public int write(ByteBuffer data) {
		int write = write(socket, data, data.position(), data.remaining());
		if (write > 0) {
			data.position(data.position() + write);
		}
		return write;
	}

	public int write(ByteBuffer data, FileDescriptorHolder fd) {
		int write = write(socket, data, data.position(), data.remaining(), fd);
		if (write > 0) {
			data.position(data.position() + write);
		}
		return write;
	}
}
