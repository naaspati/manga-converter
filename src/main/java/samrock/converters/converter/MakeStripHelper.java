package samrock.converters.converter;

public interface MakeStripHelper {
	boolean skipFishyCheck();
	boolean skipPageSizeCheck();
	boolean skipDoublePageCheck();
	boolean isCanceled();
}
