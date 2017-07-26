package linanqiu;
import java.io.File;
import java.io.FileNotFoundException;
import java.util.Scanner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


public class InlineRegexReplace implements LatexHack 
{
	Pattern m_pattern = Pattern.compile("<!-- replace (.*?) (with|by) (.*?) -->");
	
	@Override
	public String hack(String filename, String file_contents) 
	{
		String md_filename = filename.replace(".tex", ".md");
		Scanner scan;
		try 
		{
			scan = new Scanner(new File(md_filename));
			while (scan.hasNextLine())
			{
				String line = scan.nextLine().trim();
				if (line.startsWith("<!-- replace"))
				{
					Matcher mat = m_pattern.matcher(line);
					if (mat.find())
					{
						String find_pat = mat.group(1);
						String replace_pat = mat.group(3);
						file_contents = file_contents.replaceAll(find_pat, replace_pat);
					}
				}
			}
			scan.close();
		}
		catch (FileNotFoundException e) 
		{
			// Do nothing
		}
		return file_contents;
	}

}
