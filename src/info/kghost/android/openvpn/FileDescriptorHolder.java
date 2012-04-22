package info.kghost.android.openvpn;

class FileDescriptorHolder {
	private int descriptor = -1;

	public FileDescriptorHolder() {
		this(-1);
	}

	public FileDescriptorHolder(int fd) {
		descriptor = fd;
	}

	public int get() {
		return descriptor;
	}

	public boolean valid() {
		return descriptor >= 0;
	}

	public void close() {
		close(descriptor);
		descriptor = -1;
	}

	private static native void close(int fd);
}