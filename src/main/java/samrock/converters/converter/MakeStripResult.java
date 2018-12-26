package samrock.converters.converter;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.Objects;

class MakeStripResult {
    public final ConvertTask task;
    private Path result;
    private Path[] results = null;
    
    public MakeStripResult(ConvertTask task) {
        this.task = task;
    }
	public void add(Path converted) {
		Objects.requireNonNull(converted);
		if(results == null) {
			results = new Path[]{converted};
		} else {
			results = Arrays.copyOf(results, results.length + 1);
			results[results.length - 1] = converted;	
		}
	}
	public int size() {
		return results.length;
	}
	public Path getResult() {
		return result;
	}
	public Path[] getResults() {
		return results;
	}
}
