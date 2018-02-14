package samrock.converters.app.main;

import com.beust.jcommander.Parameter;

class SkipOption {
    private static void set(String key, boolean value) {
        System.setProperty(key, String.valueOf(value));
    }

    @Parameter(names="missings", description="skip checking missing images", order=20)
    private void skip_number_missings_check(boolean value) {
        set("SKIP_NUMBER_MISSINGS_CHECK", value);
    }

    @Parameter(names="double", description="skip checking double pages", order=21)
    private void skip_double_page_check(boolean value) {
        set("SKIP_DOUBLE_PAGE_CHECK", value);
    }

    @Parameter(names="size", description="skip checking image dimesions", order=22)
    private void skip_page_size_check(boolean value) {
        set("SKIP_PAGE_SIZE_CHECK", value);
    }

    @Parameter(names="fishy", description="skip checking images abnormally large", order=23)
    private void skip_fishy_check(boolean value) {
        set("SKIP_FISHY_CHECK", value);
    }

    public SkipOption() {
        for(String s: new String[] {
                "SKIP_NUMBER_MISSINGS_CHECK",
                "SKIP_DOUBLE_PAGE_CHECK",
                "SKIP_PAGE_SIZE_CHECK",
        "SKIP_FISHY_CHECK"}
                ) {
            String env = System.getenv(s);
            set(s, env == null ? false : env.trim().equalsIgnoreCase("true"));            
        }
    }

}
