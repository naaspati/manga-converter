package samrock.converters.converter;

import static sam.console.ANSI.FAILED_BANNER;
import static sam.console.ANSI.createBanner;
import static sam.console.ANSI.cyan;
import static sam.console.ANSI.green;
import static sam.console.ANSI.red;
import static sam.console.ANSI.yellow;
import static samrock.converters.extras.Utils.confirm;
import static samrock.converters.extras.Utils.subpath;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import sam.console.ANSI;
import sam.io.serilizers.StringWriter2;
import sam.logging.MyLoggerFactory;
import sam.myutils.MyUtilsPath;
import sam.string.SubSequence;
import samrock.converters.app.main.ConvertConfig;
import samrock.converters.cleanupupdate.MangaDirs;
import samrock.converters.cleanupupdate.MangaDirs.Dir;
import samrock.converters.converter.CExceptions.DoublePagesException;
import samrock.converters.converter.CExceptions.StopProcessException;
import samrock.converters.extras.Utils;

public class Converter implements MakeStripHelper {

	private static final Logger LOGGER = MyLoggerFactory.logger(Converter.class);

	private final Path BACKUP_DIR;
	private final Path IMAGES_BACKUP_DIR;
	public final Path ERRORS_DIR;

	private final ConvertConfig config;
	private final MangaDirs mangaDirs;
	private ExecutorService executorService;
	private List<MakeStripResult> result;

	public Converter(MangaDirs mangaDirs, ConvertConfig config) throws IOException {
		this.config = config;
		this.mangaDirs = mangaDirs;

		BACKUP_DIR = mangaDirs.createBackupFolder(Converter.class);
		IMAGES_BACKUP_DIR = BACKUP_DIR.resolve("images_backup");
		ERRORS_DIR = MyUtilsPath.TEMP_DIR.resolve("errors");

		Files.createDirectories(BACKUP_DIR);
		Files.createDirectories(ERRORS_DIR);
	}

	public static class ConvertTaskWrap {
		public final ConvertTask tasks;
		public final Path[] result;

		public ConvertTaskWrap(ConvertTask tasks, Path[] result) {
			this.tasks = tasks;
			this.result = result;
		}
	}

	/**
	 * 
	 * @param tasks
	 * @return successful tasks
	 * @throws InterruptedException
	 */
	public List<ConvertTaskWrap> convert(final List<ConvertTask> tasks) throws InterruptedException {
		if(tasks.isEmpty())
			return Collections.emptyList();

		result = Collections.synchronizedList(new ArrayList<>(tasks.size() + 10));
		Map<ConvertTask, DoublePagesException> doublePagesException = Collections.synchronizedMap(new IdentityHashMap<>());
		Map<ConvertTask, Exception> errors = Collections.synchronizedMap(new IdentityHashMap<>());
		AtomicBoolean failed = new AtomicBoolean(false);

		int nThreads = tasks.size() < config.getThreadCount() ? tasks.size() : config.getThreadCount();
		executorService = Executors.newFixedThreadPool(nThreads);
		AtomicInteger progress = new AtomicInteger(1);
		final String START = yellow("START: ");
		final String SUCCESS = green("SUCCESS: ");
		final String FAILED = red("FAILED: ");

		for (ConvertTask c : tasks) {
			executorService.execute(() -> {
				boolean success = false;
				String n = ANSI.cyan(progress.incrementAndGet());
				Path subpath = subpath(c.source);


				try {
					LOGGER.info(n+START+ subpath);
					MakeStrip m = new MakeStrip(c, this);
					MakeStripResult r = m.call();
					result.add(r);

					success = true;
					LOGGER.info(n+SUCCESS+yellow("("+r.size()+"): ") + subpath);
				} catch (StopProcessException e1) {
					failed.set(true);
					System.err.println("STOPPING THE CONVERSION PROCESS");
					e1.printStackTrace();
					cancel();
				} catch (DoublePagesException e1) {
					doublePagesException.put(c, e1);
				}  catch (Exception e1) {
					errors.put(c, e1);
				}
				if(!success)
					LOGGER.info(n+FAILED+ subpath);
			});
		}

		executorService.shutdown();
		executorService.awaitTermination(3, TimeUnit.DAYS);

		if(failed.get())
			return Collections.emptyList();

		if(canceled.get()) {
			LOGGER.info(FAILED_BANNER);
			return Collections.emptyList();
		}

		LOGGER.info("\n");

		StringBuilder sb = new StringBuilder("\n\n");
		int len = sb.length();
		append(errors, sb, "ERRORS");
		append(doublePagesException, sb, "DoublePages");

		if(len != sb.length())
			LOGGER.info(sb.toString());
		sb.setLength(0);

		Stream.concat(errors.keySet().stream(), doublePagesException.keySet().stream())
		.map(c -> c.source)
		.collect(Collectors.toCollection(TreeSet::new))
		.forEach(s -> sb.append(s).append('\n'));

		try {
			StringWriter2.setText(ERRORS_DIR.resolve("failed-dirs.txt"), sb);
		} catch (IOException e1) {
			LOGGER.log(Level.SEVERE, ERRORS_DIR.resolve("failed-dirs.txt").toString(), e1);
		}

		if(result.isEmpty()) {
			LOGGER.info(red("\n\nNOTHING TO MOVE"));
			return Collections.emptyList();
		}

		sb.append('\n')
		.append(createBanner("SUCCESS"))
		.append('\n');

		Map<Dir, List<MakeStripResult>> map = result.stream().collect(Collectors.groupingBy(c -> c.task.dir, IdentityHashMap::new, Collectors.toList()));

		map.forEach((dir, list) -> {
			yellow(sb, dir.file.getName()).append('\n');
			list.forEach(c -> sb.append("  ").append(c.task.source.getFileName()).append(cyan("("+c.size()+")")).append('\n'));
		});

		if(!confirm("Confirm to move?")) {
			LOGGER.info(red("CANCELLED (\"by user\")"));
			return Collections.emptyList();
		}

		LOGGER.info("\n");
		List<ConvertTaskWrap> success = new ArrayList<>(tasks.size());
		sb.setLength(0);
		String MOVING = green("moving: ");
		List<Path> moved = new ArrayList<>();

		map.forEach((dir, list) -> {
			moved.clear();
			int start = sb.length();
			sb.append("---------------\n");
			yellow(sb, dir.file.getName());
			sb.append("\n---------------\n");
			
			LOGGER.info(sb.substring(start));
			
			for (MakeStripResult m : list) {
				final Path source = m.task.source;
				final Path[] paths = new Path[m.size()];
				start = sb.length();
				sb.append(MOVING).append(subpath(source));
				LOGGER.info(sb.substring(start));
				sb.append('\n');
				
				try {
					if(paths.length == 0)
						throw new IOException("no conversion took place");
					if(m.size() == 1) {
						paths[0] = move(m.getResult(), path(source, ".jpeg"), sb);
					} else {
						int n = 1;
						int index = 0;
						for (Path src : m.getResults()) {
							Path mp = move(src, path(source, " - "+(n++)+".jpeg"), sb);
							paths[index++] = mp;
							moved.add(mp);
						}
					}
					backup(source, sb);
					success.add(new ConvertTaskWrap(m.task, paths));
				} catch (Exception e) {
					LOGGER.log(Level.SEVERE, "failed to process:"+source, e);
					sb.append("failed to process:"+source);
					e.printStackTrace(new PrintWriter(new sam.string.StringWriter2(sb)));
					sb.append('\n');
				}
			}
		});
		
		Path p = Utils.TEMP_DIR.resolve(getClass().getName()+"-move-log-"+MyUtilsPath.pathFormattedDateTime()+".txt");
		try {
			StringWriter2.setText(p, sb);
			LOGGER.info("created: "+subpath(p));
		} catch (IOException e) {
			LOGGER.log(Level.SEVERE, "failed-writing: "+subpath(p), e);
		}
		return success;
	}
	
	private Path path(Path p, String append) {
		return p.resolveSibling(p.getFileName()+append);
	}

	private static final String MOVE_SRC =       green(  "  src      : ");
	private static final String MOVE_TARGET =  yellow("\n  target    : ");
	private static final String MOVE_GARBAGED =    red(  "  garbaged : ");
	private static final String MOVE_YELLOW_ARROW =    yellow(" -> ");
	
	private Path move(Path src, Path target, StringBuilder log) throws IOException {
		Path backup = backup(target, log);
		if(backup != null) {
			int n = log.length();
			log.append(MOVE_GARBAGED)
			.append(subpath(target))
			.append(MOVE_YELLOW_ARROW)
			.append(subpath(backup));
			LOGGER.info(log.substring(n));
			log.append('\n');
		}

		Path p = Files.move(src, target, StandardCopyOption.REPLACE_EXISTING);
		
		int len = log.length();
		log.append(MOVE_SRC).append(subpath(src)).append(MOVE_TARGET).append(subpath(target));
		LOGGER.info(log.substring(len));
		log.append('\n');
		return p;
	}
	
	private static final String _BACKUP = ANSI.cyan("  BACKUP");
	private Path backup(Path p, StringBuilder logs) throws IOException {
		Path t = mangaDirs.backupMove(p, IMAGES_BACKUP_DIR);
		logs.append(_BACKUP).append(subpath(p)).append(MOVE_YELLOW_ARROW).append(subpath(t)).append('\n');
		return t;
	}

	private void append(Map<ConvertTask, ?> errors, StringBuilder sb, String title) {
		if(!errors.isEmpty()) {
			sb.append(createBanner(title)).append('\n');
			Map<Dir, List<ConvertTask>> map = errors.keySet().stream().collect(Collectors.groupingBy(c -> c.dir, IdentityHashMap::new, Collectors.toList()));
			map.forEach((dir, list) -> {
				yellow(sb, "\n-------------------------------\n");
				yellow(sb, dir.file.getName()).append('\n');
				int start = sb.length();
				list.forEach(c -> sb.append(ANSI.cyan(c.source.getFileName())).append("\n").append(errors.get(c)).append('\n'));
				write(sb, start, dir);
			});
		}
	}

	private void write(StringBuilder sb, int start, Dir dir) {
		Path p = ERRORS_DIR.resolve(subpath(dir.file.toPath())+".txt");
		try {
			Files.createDirectories(p.getParent());
			StringWriter2.appendText(p, new SubSequence(sb, start, sb.length()));
		} catch (Exception e) {
			LOGGER.log(Level.SEVERE, p.toString(), e);
		}
	}

	@Override public boolean skipFishyCheck() { return config.skipFishyCheck(); }
	@Override public boolean skipPageSizeCheck() { return config.skipPageSizeCheck(); }
	@Override public boolean skipDoublePageCheck() { return config.skipDoublePageCheck(); }

	private final AtomicBoolean canceled = new AtomicBoolean(false);

	@Override
	public boolean isCanceled() {
		return canceled.get();
	}
	private void cancel() {
		canceled.set(true);
		if(executorService != null) {
			executorService.shutdownNow();
		}
	}
}
