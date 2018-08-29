package samrock.converters.app.main;

import com.beust.jcommander.Parameter;

class ConvertOption extends SkipOption {
    private static final String MAX_FILE_NUMBER = "MAX_FILE_NUMBER";
    
    public ConvertOption() {
        String env = System.getenv(MAX_FILE_NUMBER);
        System.setProperty(MAX_FILE_NUMBER, env == null ? "100" : env.trim());
    }
    @Parameter(names="max-file-number", description="maximum number allowed a file can be", order=10)
    public void max_file_number(int number) {
        System.setProperty(MAX_FILE_NUMBER, String.valueOf(number));
    }
    @Parameter(names="--skip-check", description="skips given checks (any or many of the following)", order=11)
    private boolean skipChecks;
}
