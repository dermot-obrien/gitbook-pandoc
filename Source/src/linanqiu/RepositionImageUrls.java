package linanqiu;

import java.util.regex.Pattern;

/**
 * Makes all image links that contain paths links with only the filename
 */
public class RepositionImageUrls implements LatexHack 
{
	protected final String m_outDirectory;
	
	protected final String m_outPrefix;
	
	public RepositionImageUrls(String out_directory, String out_prefix)
	{
		super();
		m_outDirectory = out_directory;
		m_outPrefix = out_prefix;
	}

	@Override
	public String hack(String filename, String contents)
	{
		String prefix = filename.substring(m_outDirectory.length(), filename.lastIndexOf("/"));
		contents = contents.replaceAll(Pattern.quote("\\includegraphics{"), "\\\\includegraphics{" + prefix + "/");
		return contents;
	}
}
