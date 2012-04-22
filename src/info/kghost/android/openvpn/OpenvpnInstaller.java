package info.kghost.android.openvpn;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.util.Arrays;

import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.io.IOUtils;

import android.app.ProgressDialog;
import android.content.Context;
import android.os.AsyncTask;

class OpenvpnInstaller {
	public interface Callback {
		public void done(Result result);
	}

	public class Result {
		private boolean installed;
		private String text;

		public Result(boolean installed, String text) {
			setInstalled(installed);
			setText(text);
		}

		public String getText() {
			return text;
		}

		public void setText(String text) {
			this.text = text;
		}

		public boolean isInstalled() {
			return installed;
		}

		public void setInstalled(boolean installed) {
			this.installed = installed;
		}
	}

	private class InstallTask extends AsyncTask<Object, String, Result> {
		private final Context c;
		private final File path;
		private final Callback cb;
		private ProgressDialog dialog;

		public InstallTask(Context context, Callback callback) {
			c = context;
			cb = callback;
			path = new File(c.getCacheDir(), "openvpn");
		}

		private Result tryInstall() {
			publishProgress(c.getString(R.string.openvpn_installer_installing));
			try {
				InputStream in = c.getAssets().open("openvpn");
				FileOutputStream out = new FileOutputStream(path);
				IOUtils.copy(in, out);
				out.close();
				in.close();

				if (!path.setExecutable(true, true)) {
					throw new IOException("Can't set executable flag");
				}

				return check(false);
			} catch (IOException e) {
				return new Result(false, e.getLocalizedMessage());
			}
		}

		private Result check(boolean tryInstall) {
			try {
				byte[] embeded = DigestUtils.md5(c.getAssets().open("openvpn"));
				byte[] installed;
				try {
					installed = DigestUtils.md5(new FileInputStream(path));
				} catch (FileNotFoundException e) {
					if (tryInstall)
						return tryInstall();
					else
						throw e;
				}
				if (!Arrays.equals(embeded, installed)) {
					if (tryInstall)
						return tryInstall();
					else
						throw new RuntimeException(
								"OpenVpn binary not installed");
				}

				if (!path.setExecutable(true, true)) {
					throw new IOException("Can't set executable flag");
				}

				Process process;
				try {
					process = new ProcessBuilder()
							.command(path.getAbsolutePath(), "--version")
							.redirectErrorStream(true).start();
				} catch (IOException e) {
					if (tryInstall
							&& e.getCause().getMessage()
									.equals("No such file or directory")) {
						return tryInstall();
					} else {
						throw e;
					}
				}

				StringWriter writer = new StringWriter();
				IOUtils.copy(process.getInputStream(), writer, "UTF-8");
				String output = writer.toString();

				process.waitFor();
				return new Result(process.exitValue() == 1, output);
			} catch (InterruptedException e) {
				return new Result(false, e.getLocalizedMessage());
			} catch (IOException e) {
				return new Result(false, e.getLocalizedMessage());
			}
		}

		@Override
		protected Result doInBackground(Object... params) {
			return check(true);
		}

		@Override
		protected void onPreExecute() {
			super.onPreExecute();
			dialog = ProgressDialog.show(c,
					c.getString(R.string.openvpn_installer_title),
					c.getString(R.string.openvpn_installer_checking), true);
		}

		@Override
		protected void onPostExecute(Result result) {
			super.onPostExecute(result);
			if (dialog.isShowing())
				dialog.dismiss();
			cb.done(result);
		}

		@Override
		protected void onProgressUpdate(String... values) {
			dialog.setMessage(values[0]);
			super.onProgressUpdate(values);
		}

		@Override
		protected void onCancelled() {
			if (dialog.isShowing())
				dialog.dismiss();
			super.onCancelled();
		}
	}

	private InstallTask task;

	public void install(Context context, Callback callback) {
		task = new InstallTask(context, callback);
		task.execute(null, null);
	}

	public void cancel() {
		task.cancel(true);
		task = null;
	}
}
