package linanqiu;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Scanner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


public class IndexReplace implements MarkdownHack 
{
	Pattern m_pattern = Pattern.compile("<!--(\\\\index.*?)-->.*?<!--/i-->");
	
	protected static final String s_crlf = System.getProperty("line.separator");
	
	public static final IndexReplace instance = new IndexReplace();
	
	IndexReplace()
	{
		super();
	}

	@Override
	public void hack(File f) 
	{
		StringBuilder out = new StringBuilder();
		Scanner scan;
		try 
		{
			scan = new Scanner(f);
			while (scan.hasNextLine())
			{
				String line = scan.nextLine();
				Matcher mat = m_pattern.matcher(line);
				while (mat.find())
				{
					String index_pat = mat.group(1);
					line = line.replace(mat.group(0), "GPGP" + index_pat);
					//line = line.replaceAll(Pattern.quote(mat.group(0)), Pattern.quote("GPGP" + index_pat));
				}
				out.append(line).append(s_crlf);
			}
			scan.close();
			FileWriter fw = new FileWriter(f);
			fw.write(out.toString());
			fw.close();
		}
		catch (FileNotFoundException e) 
		{
			// Do nothing
		} 
		catch (IOException e) 
		{
			// Do nothing
		}
	}

}
