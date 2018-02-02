package linanqiu;

import java.io.File;

/**
 * Modifies the Markdown code before sending it to Pandoc
 * @author Sylvain Hallé
 */
public interface MarkdownHack 
{
	/**
	 * Takes the contents of a Markdown file and modifies it in some way
	 * @param f The file to hack
	 */
	public void hack(File f);
}
