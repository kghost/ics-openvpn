package info.kghost.android.openvpn;

abstract class RunnableEx<T> implements Runnable {
	private T m;

	public RunnableEx(T o) {
		m = o;
	}

	@Override
	public void run() {
		run(m);
	}

	protected abstract void run(T o);
}