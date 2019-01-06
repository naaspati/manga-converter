package samrock.converters.doublepagesplitter;

import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;
import static java.nio.file.StandardOpenOption.CREATE;
import static java.nio.file.StandardOpenOption.READ;
import static java.nio.file.StandardOpenOption.TRUNCATE_EXISTING;
import static java.nio.file.StandardOpenOption.WRITE;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import javax.imageio.ImageIO;

import sam.collection.Pair;
import sam.console.ANSI;
import sam.logging.MyLoggerFactory;
import samrock.converters.app.main.ConvertConfig;
import samrock.converters.cleanupupdate.MangaDirs;
import samrock.converters.extras.Utils;
import samrock.converters.filechecker.CheckedFile;
import samrock.converters.filechecker.FilesChecker;
import samrock.converters.filechecker.FilesChecker.CheckResult;

public class DoublePageSplitter implements Callable<Void> {
	private static final Logger LOGGER = MyLoggerFactory.logger(DoublePageSplitter.class);
	private final ConvertConfig config;
	private final AtomicInteger counter = new AtomicInteger(0);
	private final boolean  rightToLeft;

	public DoublePageSplitter(ConvertConfig config, boolean  rightToLeft) {
		this.config = config;
		this.rightToLeft = rightToLeft;
	}

	@Override
	public Void call() throws IOException {
		Path path = Paths.get(".").normalize().toAbsolutePath();
		if(!Files.isSameFile(path.getParent(), Utils.MANGA_DIR))
			throw new IllegalArgumentException(String.format("path.getParent() != Utils.MANGA_DIR\npath.getParent():%s\nUtils.MANGA_DIR: %s", path.getParent(), Utils.MANGA_DIR));
		
		List<Path> dirs = Arrays.stream(path.toFile().list())
				.map(path::resolve)
				.filter(Files::isDirectory)
				.collect(Collectors.toList());

		if(dirs.isEmpty()) {
			LOGGER.info(ANSI.red("no dirs dound"));
			return null;
		}

		MangaDirs mdirs = new MangaDirs();
		FilesChecker checker = new FilesChecker(config, mdirs);
		StringBuilder sb = new StringBuilder();
		ANSI.red(sb, "check errors");

		List<Pair<Path, CheckedFile[]>> files = new ArrayList<>();

		for (Path p : dirs) {
			CheckResult result = checker.check(p, p);
			if(result.hasErrors())
				sb.append(result.getErrors());
			else
				files.add(new Pair<>(p, result.getFiles()));
		}

		if(dirs.size() != files.size()) {
			ANSI.red(sb, "\n\ncancelling conversion");
			LOGGER.severe(sb.toString());
			return null;
		}
		
		List<Pair<Path, List<Path>>> converted = new ArrayList<>();

		for (Pair<Path,CheckedFile[]> pair : files) {
			List<Path> list = new ArrayList<>();
			converted.add(new Pair<>(pair.key, list));

			LOGGER.info(ANSI.yellow(Utils.subpath(pair.key)));

			for (CheckedFile file : pair.value) {
				try(InputStream is = Files.newInputStream(file.getPath(), READ)) {
					BufferedImage m = ImageIO.read(is);
					
					if (m.getWidth() < 1000) {
			            list.add(Files.copy(file.getPath(), temp(), REPLACE_EXISTING));
			            LOGGER.warning("\tpossibly a single page: " + file.getPath().getFileName());
			            continue;
			        }
					if(rightToLeft) {
						list.add(split(m, false));
						list.add(split(m, true));
					} else {
						list.add(split(m, true));
				        list.add(split(m, false));	
					}
				}
			}
		}
		
		Path backdir = mdirs.createBackupFolder(DoublePageSplitter.class);

		LOGGER.info(ANSI.yellow("backing up: "));
		for (Pair<Path,List<Path>> pair : converted)  {
			Path p = mdirs.backupMove(pair.key, backdir);
			LOGGER.info(Utils.subpath(pair.key) + ANSI.yellow(" -> ")+Utils.subpath(p));
		}
		
		LOGGER.info(ANSI.yellow("moving: "));
		
		for (Pair<Path, List<Path>> pair : converted) {
			final Path p = pair.key;
			Files.createDirectories(p);
			int n = 0;
			LOGGER.info(ANSI.yellow("  "+p.getFileName()));	
			
			for (Path file : pair.value) {
				Files.move(file, p.resolve(String.valueOf(n++)));
				LOGGER.info("    "+file.getFileName()+ANSI.yellow(" -> ")+(n - 1));	
			}
		}
		
		LOGGER.info(ANSI.yellow("DONE"));
		return null;

	}
    private Path temp() {
		return Utils.TEMP_DIR.resolve("double-page-"+counter.incrementAndGet());
	}

	private Path split(BufferedImage m, boolean firstHalf) throws IOException {
        BufferedImage img = new BufferedImage(m.getWidth() / 2, m.getHeight(), m.getType());

        if(firstHalf)
            img.createGraphics().drawImage(m, 0, 0, img.getWidth(), img.getHeight(), m.getWidth() / 2, 0, m.getWidth(), m.getHeight(), null);
        else
            img.createGraphics().drawImage(m, 0, 0, img.getWidth(), img.getHeight(), 0, 0, m.getWidth() / 2, m.getHeight(), null);
        
        Path temp = temp();
        try(OutputStream os = Files.newOutputStream(temp, CREATE, TRUNCATE_EXISTING, WRITE)) {
            ImageIO.write(img, "jpeg", os);
            return temp;
        }
    }

}
