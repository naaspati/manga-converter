package samrock.converters.converter;
import static java.nio.file.StandardOpenOption.CREATE_NEW;
import static java.nio.file.StandardOpenOption.READ;
import static java.nio.file.StandardOpenOption.WRITE;
import static sam.console.ANSI.red;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import javax.imageio.ImageIO;

import sam.io.fileutils.FileOpener;
import sam.io.serilizers.StringWriter2;
import sam.logging.MyLoggerFactory;
import samrock.converters.converter.CExceptions.ConversionException;
import samrock.converters.converter.CExceptions.DoublePagesException;
import samrock.converters.converter.CExceptions.StopProcessException;
import samrock.converters.extras.Utils;
import samrock.converters.filechecker.CheckedFile;
/**
 * to convert a double paged manga (reported wrong or is actually a double paged and want to convert is as it is, the set MangaConvert.forceDoublePagedConvert = true;) 
 * 
 *
 */
public class MakeStrip implements Callable<MakeStripResult> {
	private static final int MAX_IMAGE_HEIGHT = 65500;
	private static final Path MY_DIR;
	private static final AtomicInteger COUNTER;

	static {
		MY_DIR = Utils.TEMP_DIR.resolve(MakeStrip.class.getName());

		int c = Optional.ofNullable(MY_DIR.toFile().list())
				.filter(s -> s.length != 0)
				.map(names -> Stream.of(names).mapToInt(s -> s.matches("\\d+") ? Integer.parseInt(s) : 0).max().orElse(0))
				.orElse(0);

		try {
			Files.createDirectories(MY_DIR);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
		COUNTER = new AtomicInteger(c+1);
	}

	private final ConvertTask task;
	private final boolean  DONT_SKIP_FISHY_CHECK;
	private final boolean  DONT_SKIP_PAGE_SIZE_CHECK;
	private final boolean  DONT_SKIP_DOUBLE_PAGE_CHECK;
	private final MakeStripHelper helper;
	private MakeStripResult result;

	/**
	 * 
	 * @param files 
	 * @param f
	 * @throws IOException
	 */
	public MakeStrip(ConvertTask task, MakeStripHelper helper) {
		Objects.requireNonNull(task);
		this.task = task;
		this.helper = helper;

		DONT_SKIP_FISHY_CHECK = !helper.skipFishyCheck();
		DONT_SKIP_PAGE_SIZE_CHECK = !helper.skipPageSizeCheck();
		DONT_SKIP_DOUBLE_PAGE_CHECK = !helper.skipDoublePageCheck();
	}

	@Override
	public MakeStripResult call() throws IOException, ConversionException, StopProcessException, DoublePagesException {
		if(helper.isCanceled())
			throw new CancellationException();

		try {
			call2();
			return result;
		} finally {
			if(images != null) {
				images.clear();
				images = null;
			}
		}
	}
	private LinkedList<BufferedImage> images;
	private boolean imageError;

	public void setImageError() {
		this.imageError = true;
		if(images != null)
			images.clear();
		images = null;
		if(result != null)
			result = null;
	}
	private void call2() throws IOException, ConversionException, StopProcessException, DoublePagesException {
		if(helper.isCanceled()) return;

		this.result = new MakeStripResult(task);
		this.images = new LinkedList<>();

		List<String> doublePages = DONT_SKIP_DOUBLE_PAGE_CHECK ? new ArrayList<>() : null;
		int width = 0;
		int height = 0;
		String imageErrorString = "";

		for (CheckedFile t : task.getFiles()) {
			if(helper.isCanceled()) return;

			if(t == null)
				continue;
			Path path = t.getPath();

			BufferedImage img = null;

			try(InputStream is = Files.newInputStream(path, READ)) {
				img = ImageIO.read(is);

				//check non images and nullPointers
				int w = img.getWidth();
				int h = img.getHeight();

				if(DONT_SKIP_FISHY_CHECK && h > MAX_IMAGE_HEIGHT/3){
					MyLoggerFactory.logger(MakeStrip.class).severe(red("something is fishy, image height found to be: "+h)+"\t"+task.getSource());
					Path p = Utils.APP_DATA.resolve("fish.txt");
					String s = "something is fishy, image height found to be: "+h+"\t"+task.getSource();
					StringWriter2.setText(p, s);
					FileOpener.openFile(p.toFile());
					throw new StopProcessException("FISHY_CHECK: "+s);
				}
				if(DONT_SKIP_PAGE_SIZE_CHECK && (w < 100 || h < 100)) {
					imageErrorString += String.format("\n  %s: Image Size Error(%sX%s)", path.getFileName(), w, h);	
					setImageError(); 
				}
				if(DONT_SKIP_DOUBLE_PAGE_CHECK && w > 1000)
					doublePages.add(path.getFileName().toString());

				if(height + h > MAX_IMAGE_HEIGHT) {
					if(!imageError)
						convert(width, height);
					width = 0;
					height = 0;
				}

				width = Math.max(w, width);
				height += h;

				if(!imageError)
					images.add(img);
			}
		}

		int doublePageCount = doublePages == null ? 0 : doublePages.size();

		if(doublePageCount > 0  && doublePageCount > task.getFiles().length  - task.getFiles().length/4) {
			if(imageErrorString.isEmpty())
				throw new DoublePagesException(doublePages);
			else
				throw new ConversionException(imageErrorString+"\nDoublePages:"+doublePages+"\n");
		}
		
		if(!imageErrorString.isEmpty())
			throw new ConversionException(imageErrorString+"\n");

		if(!imageError && !images.isEmpty())
			convert(width, height);
	}

	private boolean convert(int width, int height) throws IOException, ConversionException {
		if(helper.isCanceled()) return false;

		Path temp = createStrip(width, height);

		if(temp != null){
			result.add(temp);
			return true;
		}
		setImageError();
		return false;

	}
	private Path createStrip(final int width, final int height) throws IOException, ConversionException {
		if(helper.isCanceled()) return null;

		Path temp = MY_DIR.resolve(String.valueOf(COUNTER.incrementAndGet()));

		BufferedImage finalImage = new BufferedImage(width, height, images.get(0).getType());
		Graphics2D g = finalImage.createGraphics();

		int totalHeight = 0;

		while(!images.isEmpty()) {
			if(helper.isCanceled()) return null;

			BufferedImage m = images.removeFirst();

			g.drawImage(m, (width - m.getWidth()) / 2, totalHeight, null);
			totalHeight += m.getHeight();
			m.flush();
		}
		g.dispose();

		if(totalHeight != height) 
			throw new ConversionException(String.format("converted height(%s) is not equal to expected height(%s)", totalHeight, height));

		try(OutputStream os = Files.newOutputStream(temp, CREATE_NEW, WRITE)) {
			ImageIO.write(finalImage, "jpeg", os);
			return temp;
		} catch (IOException e) {
			throw new IOException("Unable to save Image(in temp)", e);
		}
	}
}
