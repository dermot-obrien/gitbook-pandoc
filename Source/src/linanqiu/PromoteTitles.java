package linanqiu;

/**
 * Moves all section titles one level in the hierarchy, so that
 * level 1 headers become chapters instead of sections.
 */
public class PromoteTitles implements LatexHack 
{
	public static PromoteTitles instance = new PromoteTitles();
	
	private PromoteTitles()
	{
		super();
	}

	@Override
	public String hack(String contents)
	{
		contents = contents.replaceAll("\\\\section\\{", "\\\\chapter{");
		contents = contents.replaceAll("\\\\subsection\\{", "\\\\section{");
		contents = contents.replaceAll("\\\\subsubsection\\{", "\\\\subsection{");
		return contents;
	}
}
