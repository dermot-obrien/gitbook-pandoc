package linanqiu;

import java.util.LinkedList;
import java.util.List;
import java.util.Scanner;
import java.util.regex.Pattern;

/**
 * Performs a batch of search-replace based on regexes.
 */
public class RegexReplace implements LatexHack 
{
	protected List<String[]> m_replacements;
	
	protected boolean m_useRegex = true;
	
	public RegexReplace(List<String[]> replacements)
	{
		super();
		m_replacements = replacements;
	}
	
	public void useRegex(boolean b)
	{
		m_useRegex = b;
	}
	
	public RegexReplace(Scanner scanner)
	{
		super();
		m_replacements = new LinkedList<String[]>();
		int line_cnt = 0;
		String filename = "", pattern = "", replace = "";
		while (scanner.hasNextLine())
		{
			String line = scanner.nextLine();
			if (line_cnt % 3 == 0)
			{
				filename = line;
			}
			else if (line_cnt % 3 == 1)
			{
				pattern = line;
			}
			else
			{
				replace = line;
				m_replacements.add(new String[]{filename, pattern, replace});
			}
			line_cnt++;
		}
	}

	@Override
	public String hack(String filename, String contents)
	{
		for (String[] entry : m_replacements)
		{
			if (!filename.matches(entry[0]))
				continue;
			if (m_useRegex)
			{
				contents = contents.replaceAll(Pattern.quote(entry[1]), Pattern.quote(entry[2]));
			}
			else
			{
				contents = contents.replace(entry[1], entry[2]);
			}
		}
		return contents;
	}
}
