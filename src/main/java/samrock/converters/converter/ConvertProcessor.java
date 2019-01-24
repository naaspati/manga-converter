package samrock.converters.converter;

import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.mapping;
import static java.util.stream.Collectors.toList;
import static sam.console.ANSI.createBanner;
import static sam.console.ANSI.cyan;
import static sam.console.ANSI.green;
import static sam.console.ANSI.red;
import static sam.console.ANSI.yellow;
import static sam.myutils.Checker.isEmpty;
import static sam.myutils.Checker.notExists;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.logging.Logger;

import sam.downloader.db.DownloaderDB;
import sam.downloader.db.DownloaderDBFactory;
import sam.downloader.db.entities.meta.DStatus;
import sam.downloader.db.entities.meta.IDChapter;
import sam.downloader.db.entities.meta.IDManga;
import sam.downloader.db.entities.meta.IDPage;
import sam.logging.MyLoggerFactory;
import sam.manga.samrock.chapters.MinimalChapter;
import sam.manga.samrock.mangas.MinimalManga;
import sam.myutils.ThrowException;
import sam.nopkg.Junk;
import sam.tsv.Column;
import sam.tsv.Row;
import sam.tsv.Tsv;
import samrock.converters.app.main.ConvertConfig;
import samrock.converters.cleanupupdate.CheckupsAndUpdates;
import samrock.converters.cleanupupdate.MangaDirs;
import samrock.converters.cleanupupdate.MangaDirs.Dir;
import samrock.converters.converter.Converter.ConvertTaskWrap;
import samrock.converters.extras.Utils;
import samrock.converters.filechecker.FilesChecker;
import samrock.converters.filechecker.FilesChecker.CheckResult;
public class ConvertProcessor {
	private final ConvertConfig config;
	private final MangaDirs mangaDirs;
	private static final Logger LOGGER = MyLoggerFactory.logger(ConvertProcessor.class);


	public ConvertProcessor(ConvertConfig config) {
		this.config = config;
		this.mangaDirs = new MangaDirs();
	}
	/**
	 * FIXME needed for partial conversion and for those manga chapters outside manga_dir
	 * 
	 * public void process(List<DChapterImpl> chapters, boolean onlyMove)  {
		Progressor progress = progresser(chapters.size());
		Converter converter = new Converter(progress, config);
		if(onlyMove) {
			// converter.move(); 
			throw new IllegalAccessError("converter.move() not yet implemented");
		}
		else {
			List<ChapterWithMangaId> chs = converter.convert(Collections.unmodifiableList(chapters));
			Path mp = mangaDirs.getMangaDir(); 
			if(chs != null && chapters.stream().anyMatch(c -> c.getTarget() != null && c.getTarget().startsWith(mp))) {
				CheckupsAndUpdates c = new CheckupsAndUpdates(chs, progress);
				c.run();
			}
			progress.dispose();
		}
	}
	 * @param size
	 * @return
	 * @throws Exception 
	 */

	public void onlyUpdate() throws Exception {
		new CheckupsAndUpdates(mangaDirs, ddb(load())).call();
	}
	public void full() throws Exception {
		List<Dir> dirs = mangaDirs.getDirs(ConvertProcessor.class);
		List<Dir> modified = dirs.stream().filter(Dir::isModified).collect(toList());

		if(modified.isEmpty()) {
			LOGGER.info(yellow("not modified dirs found"));
			return;
		}

		StringBuilder sb = new StringBuilder(green("modified dirs found\n"));
		modified.forEach(d -> sb.append("  ").append(d.file.getName()).append('\n'));

		sb.append("\n--------------\n");
		cyan(sb, "total: "+modified.size());
		sb.append("\n--------------\n");

		LOGGER.info(sb.toString());
		sb.setLength(0);

		FilesChecker checker = new FilesChecker(config, mangaDirs);
		List<ConvertTask> tasks = new ArrayList<>();
		int failed = 0;

		LOGGER.info(createBanner("FILES CHECKING"));
		int dirCount = 0;
		int total = 0;
		boolean modi = false;

		for (Dir d : modified) {
			File dir = d.file;
			int count = 0;
			sb.setLength(0);

			yellow(sb, dir.getName());
			int len = sb.length();

			for (String s : dir.list()) {
				File file = new File(dir, s);
				if(!file.isDirectory())
					continue;

				count++;

				sb.append("  ").append(file.getName());

				Path p = file.toPath();
				CheckResult result = checker.check(p, p);
				if(result.hasErrors()) {
					failed++;
					sb.append("\n");
					sb.append(result.getErrors());
					sb.append("\n");
				} else {
					tasks.add(new ConvertTask(p, result.getFiles(), d));
					cyan(sb, " ("+result.getFiles().length+")\n");
				}
			}
			if(count == 0) {
				d.markUpdated();
				modi = true;
			} else {
				sb.insert(len, cyan(" ("+count+")\n"));
				System.out.println(sb);
				dirCount++;
				total += count;
			}
		}

		sb.setLength(0);
		cyan(sb, "\n------------------------\n");
		yellow(sb, "manga-dir-scanned   : ").append(modified.size()).append('\n');
		yellow(sb, "modified-manga-dirs : ").append(dirCount).append('\n');
		yellow(sb, "chapter-dirs        : ").append(total).append('\n');
		cyan(sb, "------------------------\n");

		LOGGER.info(sb.toString());
		sb.setLength(0);

		if(failed != 0) {
			LOGGER.info(red("failed Count")+failed);
			LOGGER.info("cancelling convert");
			return;
		}

		if(config.isDryRun()) {
			if(modi) {
				mangaDirs.save(dirs, getClass());
				modi = true;
			}
			LOGGER.info(yellow("\n\nNOT PROCEEDING TO CONVERT"));
			LOGGER.info(cyan("DRY_RUN"));
			return;
		}

		if(tasks.isEmpty()) {
			if(modi) {
				mangaDirs.save(dirs, getClass());
				modi = true;
			}
			LOGGER.info(green("noting found to convert"));
			return;
		}

		if(!Utils.confirm("Confirm to proceed")){
			LOGGER.info(yellow("cancelling convert (user requested)"));
			return;
		}

		Converter cnvtr = new Converter(mangaDirs, config);
		List<ConvertTaskWrap> list = cnvtr.convert(tasks);

		if(isEmpty(list)){
			LOGGER.info(red("NO CONVERSION TOOK PLACE"));
			LOGGER.info(yellow("SKIPPING DB UPDATE"));
			return;
		}

		Tsv tsv = save(list);

		if(new CheckupsAndUpdates(mangaDirs, ddb(tsv)).call()) {
			list.stream()
			.map(d -> d.tasks.dir)
			.distinct()
			.filter(d -> {
				File file = d.file;
				String[] ss = file.list();
				if(ss == null)
					return false;
				return Arrays.stream(ss).noneMatch(s -> new File(file, s).isDirectory());
			})
			.forEach(d -> d.current = d.file.lastModified());
			
			mangaDirs.save(dirs, getClass());	
		}
	}
	private Tsv save(List<ConvertTaskWrap> list) throws IOException {
		Tsv tsv = new Tsv("source", "target", "dir");
		list.forEach(c -> {
			for (Path p : c.result) 
				tsv.addRow(c.tasks.source.toString(), p.toString(), c.tasks.dir.name);
		});
		Path p = Utils.TEMP_DIR.resolve("ConvertTaskWrap.tsv");
		tsv.save(p);
		LOGGER.info("created: "+p);

		return tsv;
	}
	private Tsv load() throws IOException {
		Path p = Utils.TEMP_DIR.resolve("ConvertTaskWrap.tsv");
		return Files.exists(p) ? Tsv.parse(p) : null; 
	}
	private Map<String, MinimalManga> ddb(Tsv tsv) throws SQLException {
		if(tsv == null)
			return Collections.emptyMap();
		
		Path ddb = config.downloaderDB();
		if(notExists(ddb))
			return Collections.emptyMap();

		Column dirCol = tsv.getColumn("dir");
		Column srcCol = tsv.getColumn("source");
		Column targetCol = tsv.getColumn("target");
		
		// dirname -> chap_src -> List(chap_target)
		Map<String, Map<String, List<String>>> rows = tsv.stream().collect(groupingBy(dirCol::get, groupingBy(wrapReplaceSlash(srcCol::get), mapping(wrapReplaceSlash(targetCol::get), toList()))));
		Map<String, MinimalManga> mangas = new HashMap<>(); 
		
		new DownloaderDB(ddb)
				.read(new DownloaderDBFactory() {
					@Override
					public IDPage createPage(IDChapter chapter, int order, String page_url, String img_url, String error, String status) {
						return ThrowException.illegalAccessError();
					}
					@Override
					public IDManga createManga(int manga_id, String dir_name, String manga_name, String url, String error, String status) {
						TempManga m;
						if(rows.containsKey(dir_name))
							mangas.put(dir_name, m = new TempManga(new ArrayList<>(), manga_id, dir_name, manga_name));
						else 
							m = new TempManga(null, manga_id, dir_name, manga_name);
						
						return m;
					}
					@Override
					public IDChapter createChapter(IDManga m, double number, String title, String volume, String source, String target, String url, String error, String status) {
						if(source == null)
							return null;
						
						Map<String, List<String>> map = rows.get(m.getDirName());
						if(isEmpty(map))
							return null;
						
						List<String> list = map.get(replaceSlash(source));
						if(isEmpty(list))
							return null;
						
						TempManga manga = (TempManga) m; 
						list.forEach(t -> manga.list.add(new TempChapter(title, filename(t), number, source, t)));
						
						return null;
					}
					@Override
					public boolean loadPages() {
						return false;
					}
				});

		return mangas;

	}

	protected String filename(String t) {
		return t == null ? null : t.substring(t.lastIndexOf("\\")+1);
	}
	private Function<Row, String> wrapReplaceSlash(Function<Row, String> function) {
		return r -> replaceSlash(function.apply(r));
	}

	private String replaceSlash(String s) {
		if(s == null)
			return s;
		
		if(s.indexOf('/') >= 0) 
			s = s.replace('/', '\\');
		
		if(s.charAt(s.length() - 1) == '\\')
			s = s.substring(0, s.length() - 1);
		
		return s;
	}

	static class TempManga implements IDManga {
		final List<IDChapter> list;
		final int id;
		final String dirname, manganame;

		public TempManga(List<IDChapter> list, int id, String dirname, String manganame) {
			this.id = id;
			this.dirname = dirname;
			this.manganame = manganame;
			this.list = list;
		}
		@Override
		public int getMangaId() {
			return id;
		}
		@Override
		public String getDirName() {
			return dirname;
		}

		@Override
		public Path getDirPath() {
			return Junk.notYetImplemented();
		}
		@Override
		public String getMangaName() {
			return manganame;
		}
		@Override
		public Iterable<? extends MinimalChapter> getChapterIterable() {
			return list;
		}

		@Override
		public Iterator<IDChapter> iterator() {
			return list.iterator();
		}
		@Override
		public String getUrl() {
			return ThrowException.illegalAccessError();
		}

		@Override
		public String getError() {
			return ThrowException.illegalAccessError();
		}

		@Override
		public DStatus getStatus() {
			return ThrowException.illegalAccessError();
		}

		@Override
		public IDChapter addChapter(IDChapter c) {
			return null;
		}
		@Override
		public int size() {
			return list.size();
		}
		@Override
		public String toString() {
			return "TempManga [id=" + id + ", dirname=" + dirname + ", manganame=" + manganame + "]";
		}
	}

	static class TempChapter implements IDChapter {
		final String title, filename;
		final double number;
		final String source;
		String target;

		public TempChapter(String title, String filename, double number, String source, String target) {
			this.title = title;
			this.filename = filename;
			this.number = number;
			this.source = source;
			this.target = target;
		}
		@Override
		public double getNumber() {
			return number;
		}
		@Override
		public String getTitle() {
			return title;
		}
		@Override
		public String getFileName() {
			return filename;
		}
		@Override
		public Iterator<IDPage> iterator() {
			return ThrowException.illegalAccessError();
		}
		@Override
		public Object getSource() {
			return source;
		}
		@Override
		public Object getTarget() {
			return target;
		}
		@Override
		public String getUrl() {
			return ThrowException.illegalAccessError();
		}
		@Override
		public String getError() {
			return ThrowException.illegalAccessError();
		}
		@Override
		public IDPage addPage(IDPage page) {
			return ThrowException.illegalAccessError();
		}
		@Override
		public DStatus getStatus() {
			return ThrowException.illegalAccessError();
		}

		@Override
		public String getVolume() {
			return ThrowException.illegalAccessError();
		}
		@Override
		public int size() {
			return ThrowException.illegalAccessError();
		}
		@Override
		public String toString() {
			return "title: "+title+
					"\nfilename: "+filename+
					"\nnumber: "+number+
					"\nsource: "+source+
					"\ntarget: "+target;
		}
	}
}
