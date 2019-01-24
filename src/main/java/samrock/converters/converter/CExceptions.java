package samrock.converters.converter;

import java.util.List;

interface CExceptions {
	public class StopProcessException extends Exception {
		private static final long serialVersionUID = 1L;

		public StopProcessException(String string) {
			super(string);
		}
	}
	public class DoublePagesException extends Exception {
		private static final long serialVersionUID = -5644908523798260049L;
		@SuppressWarnings("unused")
		private final List<String> doublePages;

		public DoublePagesException(List<String> doublePages) {
			super(doublePages.toString());
			this.doublePages = doublePages;	
		}
	}
	
	public class ConversionException extends Exception {
		public ConversionException(String msg) {
			super(msg);
		}
		public ConversionException(String msg, Throwable e) {
			super(msg, e);
		}
		private static final long serialVersionUID = 1L;

	}



}
