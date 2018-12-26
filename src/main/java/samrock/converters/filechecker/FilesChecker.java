package samrock.converters.filechecker;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.BitSet;
import java.util.stream.Stream;

import samrock.converters.app.main.ConvertConfig;
import samrock.converters.cleanupupdate.MangaDirs;
import samrock.converters.extras.Utils;

public class FilesChecker {
	Path MANGA_DIR, DATA_FOLDER;
	final  int mcount;
	final  int dcount;

	private final int MAX_FILE_NUMBER;
	private boolean DONT_SKIP_NUMBER_MISSINGS_CHECK;

	public static class CheckResult {
		private final CharSequence errors;
		private final CheckedFile[] files;

		public CheckResult(CheckedFile[] files) {
			this.errors = null;
			this.files = files;
		}
		private CheckResult(CharSequence error) {
			this.errors = error;
			this.files = null;
		}
		public boolean hasErrors() {
			return errors != null;
		}
		public CharSequence getErrors() {
			return errors;
		}
		public CheckedFile[] getFiles() {
			return files;
		}
	}

	public FilesChecker(ConvertConfig config, MangaDirs mangaDirs) {
		this.MAX_FILE_NUMBER = config.getMaxFileNumber();
		this.DONT_SKIP_NUMBER_MISSINGS_CHECK = !config.skipMissingNumberCheck();
		this.MANGA_DIR = mangaDirs.getMangaDir();
		this.DATA_FOLDER = mangaDirs.getDataFolder();
		this.mcount = MANGA_DIR.getNameCount() + 1;
		this.dcount = DATA_FOLDER.getNameCount() + 1;
	}
	public CheckResult check(Path src, Path target) throws IOException {
		if(!Files.isDirectory(src)) 
			return new CheckResult((Files.notExists(src) ? "Folder not found: " : "not a folder: ") + Utils.subpath(src));

		checkBadDir(src);
		if(target != null && !target.equals(src) && Files.exists(target))
			checkBadDir(target);

		return checkDir(src);
	}
	private void checkBadDir(Path path) throws IOException {
		if(path.equals(MANGA_DIR) || Files.isSameFile(path, MANGA_DIR))
			throw new IllegalArgumentException("trying to convert manga_folder: "+MANGA_DIR);
		if(path.equals(DATA_FOLDER) || Files.isSameFile(path, DATA_FOLDER))
			throw new IllegalArgumentException("trying to convert data_folder: "+DATA_FOLDER);
		if(path.startsWith(MANGA_DIR) && path.getNameCount() == mcount)
			throw new IllegalArgumentException("trying to convert manga_dir in manga_folder: "+path);
		if(path.startsWith(DATA_FOLDER) && path.getNameCount() == dcount)
			throw new IllegalArgumentException("trying to convert data_dir in data_folder: "+path);
	}
	private CheckResult checkDir(Path source) {
		StringBuilder sb = new StringBuilder().append(Utils.subpath(source)).append('\n');
		int len = sb.length();

		Path[] paths = new Path[30];
		int max = 0;
		int fileCount = 0;

		try(DirectoryStream<Path> stream = Files.newDirectoryStream(source);) {
			for (Path path : stream) {
				if(Files.isDirectory(path))
					sb.append("  Directory inside: ").append(path.getFileName()).append('\n');
				else {
					String name = path.getFileName().toString();
					try {
						int n = name.indexOf('.');
						n = Integer.parseInt(n < 0 ? name : name.substring(0, n));
						if(n > MAX_FILE_NUMBER) {
							sb.append("  max file number(").append(MAX_FILE_NUMBER).append(") exceeded: ").append(name).append('\n');
							return new CheckResult(sb);
						}
						if(n >= paths.length) 
							paths = Arrays.copyOf(paths, n + 30);

						if(paths[n] != null)
							sb.append("  number conflits: ").append(paths[n].getFileName()).append(" -> ").append(name).append('\n');
						else 
							paths[n] = path;

						if(n > max)
							max = n;
						fileCount++;
					} catch (NumberFormatException e) {
						sb.append("  bad file number: ").append(name).append("  error: ").append(e).append('\n');
					}
				}
			}            
		} catch (IOException e1) {
			sb.append("dir walk failed: ").append(" error: ").append(e1).append('\n');
			return new CheckResult(sb);
		}
		if(fileCount == 0) {
			sb.append("Empty dir").append('\n');
			return new CheckResult(sb);
		}
		int[] missings = !DONT_SKIP_NUMBER_MISSINGS_CHECK ? null : missings(paths);
		if(missings != null && missings.length != 0) 
			sb.append("Missing Image Files: ").append(Arrays.toString(missings)).append('\n');

		if(sb.length() != len) 
			new CheckResult(sb);

		return new CheckResult(Stream.of(paths).limit(max+1).map(f -> f == null ? null : new CheckedFile(f)).toArray(CheckedFile[]::new));
	}
	private int[] missings(Path[] paths) {
		BitSet b = null;
		int  t = 0;
		for (int j = 0; j < paths.length; j++) {
			if(paths[j] == null) {
				if(b == null)
					b = new BitSet(paths.length);
				b.set(j);
				t++;
			}
		}

		if(t == 0)
			return null;

		int[] ret = new int[t];
		int n = 0;
		for (int i = 0; i < t; i++) {
			if(b.get(i))
				ret[n++] = i; 
		}

		return ret;
	}
}
