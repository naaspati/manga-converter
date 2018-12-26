package samrock.converters.cleanupupdate;

import java.io.DataInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.Month;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.stream.Collectors;

import sam.config.MyConfig;
import sam.io.serilizers.ObjectReader;
import sam.io.serilizers.ObjectWriter;
import sam.logging.MyLoggerFactory;
import sam.myutils.MyUtilsException;
import sam.myutils.MyUtilsPath;
import samrock.converters.extras.Utils;

public class MangaDirs {
	private static final Path mangaDir = Utils.MANGA_DIR;
	private static final Path thumbsFolder = Paths.get(MyConfig.SAMROCK_THUMBS_DIR);
	private static final Path dataFolder = Paths.get(MyConfig.MANGA_DATA_DIR);
	private static final String dataFolderName = dataFolder.getFileName().toString();
	private static final Path MY_DIR = Utils.APP_DATA.resolve(MangaDirs.class.getName());
	
	static {
		MyUtilsException.hideError(() -> Files.createDirectories(MY_DIR));
	}

	private final int mangaDirNamecount = mangaDir.getNameCount();

	public Path getMangaDir() { return mangaDir; }
	public Path getThumbsFolder() { return thumbsFolder; }
	public Path getDataFolder() { return dataFolder; }
	public int getMangaDirNamecount() { return mangaDirNamecount; }
	
	/**
	 * move given file or folder to backupRoot
	 * @param file
	 * @param backupRoot
	 * @return
	 * @throws IOException
	 */
	public Path backupMove(Path file, Path backupRoot) throws IOException {
		if(Files.notExists(file))
			return null;

		Path backup; 

		if(file.startsWith(mangaDir))
			backup = backupRoot.resolve(MyUtilsPath.subpath(file, mangaDir));
		else if(file.getNameCount() > 2)
			backup = backupRoot.resolve(file.subpath(file.getNameCount() - 2, file.getNameCount()));
		else 
			backup = backupRoot.resolve(file.getFileName());

		Files.createDirectories(backup.getParent());

		if(Files.exists(backup))
			backup = backup.resolveSibling(backup.getFileName()+""+System.currentTimeMillis());
		Files.move(file,backup, StandardCopyOption.REPLACE_EXISTING);

		return backup;
	}
	public Path createBackupFolder(Class<?> cls) {
		Month currentMonth = LocalDate.now().getMonth();
		try {
			Path path = Utils.CACHE_DIR.resolve(currentMonth.toString().toLowerCase()).resolve(cls.getName());
			Files.createDirectories(path);

			return path;
		} catch (IOException e) {
			MyLoggerFactory.logger(Utils.class).log(Level.SEVERE,"failed to create dirs ", e); 
			throw new RuntimeException("failed to create dirs ", e);
		}
	}
	
	public static class Dir {
		public final String name;
		private final String fileString;
		public final File file;
		public final long actual;
		public long current;
		
		private Dir(String fileString, long actual, long current) {
			this.fileString = fileString;
			this.file = new File(fileString);
			this.name = file.getName();
			this.actual = actual;
			this.current = current;
		}
		private Dir(String name, File file) {
			this.name = name;
			this.fileString = file.toString();
			this.file = file;
			this.actual = file.lastModified();
		}
		private Dir(Dir dir, long current) {
			this.name = dir.name;
			this.fileString = dir.fileString;
			this.file = dir.file;
			this.actual = dir.actual;
			this.current = current;
		}
		public void markUpdated() {
			this.current = actual;
		}
		public boolean isModified() {
			return current != actual;
		}
	}
	
	private Dir[] dirs;
	
	public List<Dir> getDirs(@SuppressWarnings("rawtypes") Class owner) throws IOException {
		if(dirs == null) {
			File root = mangaDir.toFile();
			String[] array = root.list();
			dirs = new Dir[array.length];

			int index = 0;
			for (String s : array) {
				if(dataFolderName.equals(s))
					continue;
				
				File file = new File(root, s);
				if(file.isDirectory())
					dirs[index++] = new Dir(s, file);
			}
			if(index != dirs.length)
				dirs = Arrays.copyOf(dirs, index);
		}
		
		Path p = owner == null ? null : MY_DIR.resolve(owner.getName());
		if(p == null || Files.notExists(p))
			return Arrays.stream(dirs).map(d -> new Dir(d, -1)).collect(Collectors.toList());
		
		List<Dir> list = new ArrayList<>(dirs.length + 10);
		Map<String, Long> old = ObjectReader.readMap(p, dos -> dos.readUTF(), DataInputStream::readLong);
		
		for (Dir dir : dirs) {
			Long s = old.remove(dir.fileString);
			
			if(s == null)
				list.add(new Dir(dir, -1));
			else 
				list.add(new Dir(dir, s.longValue()));
		}
		
		/* TODO if old is required
		 if(!old.isEmpty())
			old.forEach((s,t) -> list.add(new Dir(s, -1, -1, DirStatus.REMOVED)));
		 */
		return list;
	}
	
	public void save(List<Dir> dirs, @SuppressWarnings("rawtypes") Class owner) throws IOException {
		Path p = MY_DIR.resolve(owner.getName());
		
		ObjectWriter.writeList(p, dirs, (dir, dos) -> {
			dos.writeUTF(dir.fileString);
			dos.writeLong(dir.current);
		});
		System.out.println("saved: " + Utils.subpath(p));
	}
	public Set<String> dirNames() {
		return Arrays.stream(dirs).map(d -> d.name).collect(Collectors.toSet());
	}
}
