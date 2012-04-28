package info.kghost.android.openvpn;

import java.util.Iterator;

import android.os.Parcel;
import android.os.Parcelable;

class LogQueue implements Parcelable, Iterable<String> {
	private String[] buffer = null;
	private int start = 0;
	private int end = 0;
	private final int size;

	public LogQueue(int size) {
		this.size = size + 1;
		buffer = new String[size+1];
	}

	private LogQueue(Parcel in) {
		size = in.readInt();
		start = in.readInt();
		end = in.readInt();
		buffer = new String[size];
		in.readStringArray(buffer);
	}

	private int advance(int n) {
		return (n + 1) % size;
	}

	private int advanceEnd() {
		int o = end;
		end = advance(end);
		if (start == end)
			start = advance(start);
		return o;
	}

	public boolean isEmpty() {
		return start == end;
	}

	public boolean isFull() {
		return start == advance(end);
	}

	public void add(String s) {
		buffer[advanceEnd()] = s;
	}

	private class IterImpl implements Iterator<String> {
		private int current;

		public IterImpl() {
			current = start;
		}

		@Override
		public boolean hasNext() {
			return current != end;
		}

		@Override
		public String next() {
			String s = buffer[current];
			current = advance(current);
			return s;
		}

		@Override
		public void remove() {
			throw new RuntimeException("Not Implemented");
		}
	}

	@Override
	public Iterator<String> iterator() {
		return new IterImpl();
	}

	@Override
	public int describeContents() {
		return 0;
	}

	@Override
	public void writeToParcel(Parcel dest, int flags) {
		dest.writeInt(size);
		dest.writeInt(start);
		dest.writeInt(end);
		dest.writeStringArray(buffer);
	}

	public static final Parcelable.Creator<LogQueue> CREATOR = new Parcelable.Creator<LogQueue>() {
		@Override
		public LogQueue createFromParcel(Parcel in) {
			return new LogQueue(in);
		}

		@Override
		public LogQueue[] newArray(int size) {
			return new LogQueue[size];
		}
	};

}
