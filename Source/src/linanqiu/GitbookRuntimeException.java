package linanqiu;

public class GitbookRuntimeException extends Exception
{
	/**
	 * Dummy UID
	 */
	private static final long serialVersionUID = 1L;
	
	public GitbookRuntimeException()
	{
		super();
	}
	
	public GitbookRuntimeException(Throwable t)
	{
		super(t);
	}
	
	public static class NoHeaderFileException extends GitbookRuntimeException
	{
		/**
		 * Dummy UID
		 */
		private static final long serialVersionUID = 1L;

		@Override
		public String getMessage()
		{
			return "The file " + GitbookToPandoc.s_headerFilename + " cannot be found in the source folder";
		}
	}
	
	public static class CopyException extends GitbookRuntimeException
	{
		/**
		 * Dummy UID
		 */
		private static final long serialVersionUID = 1L;
		
		protected String m_inDir;
		protected String m_outDir;
		
		public CopyException(String in_dir, String out_dir)
		{
			super();
			m_inDir = in_dir;
			m_outDir = out_dir;
		}

		@Override
		public String getMessage()
		{
			return "An error occurred when copying the contents of " + m_inDir + " to " + m_outDir;
		}
	}

}
