package linanqiu;

/**
 * Makes all image links that contain paths links with only the filename
 */
public class FlattenImageLinks implements LatexHack 
{
	public static FlattenImageLinks instance = new FlattenImageLinks();
	
	private FlattenImageLinks()
	{
		super();
	}

	@Override
	public String hack(String filename, String contents)
	{
		contents = contents.replaceAll("\\\\includegraphics\\{\\.\\./", "\\\\includegraphics{");
		return contents;
	}
}
