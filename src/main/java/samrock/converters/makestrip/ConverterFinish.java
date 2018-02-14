package samrock.converters.makestrip;

import java.nio.file.Path;

import sam.myutils.onmany.OneOrMany;
import samrock.converters.extras.ConvertTask;
import samrock.converters.extras.Errors;

@FunctionalInterface
public interface ConverterFinish {
    public void finish(Errors errors, ConvertTask task, OneOrMany<Path> convertedFiles);
}
