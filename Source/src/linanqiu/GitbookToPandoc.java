/*
  gitbook-pandoc
  Copyright (C) 2014-2017 linanqiu
  Copyright (C) 2017 Sylvain Hallé

  This program is free software: you can redistribute it and/or modify
  it under the terms of the GNU General Public License as published by
  the Free Software Foundation, either version 3 of the License, or
  (at your option) any later version.

  This program is distributed in the hope that it will be useful,
  but WITHOUT ANY WARRANTY; without even the implied warranty of
  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
  GNU General Public License for more details.

  You should have received a copy of the GNU General Public License
  along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package linanqiu;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import ca.uqac.lif.labpal.CliParser;
import ca.uqac.lif.labpal.CliParser.Argument;
import ca.uqac.lif.labpal.CliParser.ArgumentMap;
import ca.uqac.lif.labpal.CommandRunner;
import ca.uqac.lif.labpal.FileHelper;

/**
 * Takes a Gitbook directory and a output directory, and converts all markdowns
 * inside the Gitbook directory into LaTeX, and creates a master book.tex that
 * includes all the converted latex files. Essentially, a book.
 * 
 * @author linanqiu
 */
public class GitbookToPandoc 
{
	/**
	 * The level of chapters in the summary
	 */
	protected static final int CHAPTER = 1;
	
	/**
	 * The level of subchapters in the summary
	 */
	protected static final int SUBCHAPTER = 2;
	
	/**
	 * The path to the pandoc executable. If pandoc is in the path, then
	 * just "{@code pandoc}" should be sufficient.
	 */
	public static final String s_pandocPath = "pandoc";
	
	/**
	 * The name of the file containing the summary in the directory
	 * structure
	 */
	public static final String s_summaryFilename = "summary.md";
	
	/**
	 * The name of the files containing the chapters
	 */
	public static final String s_chapterFilename = "readme.md";
	
	/**
	 * The name of the header LaTeX file
	 */
	public static final String s_headerFilename = "header.tex";

	private String in_directory;
	private String out_directory;
	private String header;

	private LinkedHashMap<String,Integer> index;

	private File summary;

	/**
	 * The class that does most of the grunt work
	 * 
	 * @param in_directory
	 *            directory that contains 'summary.md'. The original gitbook
	 *            directory
	 * @param out_directory
	 *            the output directory where all contents of in_directory will
	 *            be copied to, then converted, then new book.tex created
	 */
	public GitbookToPandoc(String in_directory, String out_directory)
	{
		this.in_directory = in_directory;
		this.out_directory = out_directory;
	}
	
	public void run() throws GitbookRuntimeException
	{
		index = new LinkedHashMap<String,Integer>();

		// read in the header file
		File header_f = new File(s_headerFilename);
		if (!header_f.exists())
		{
			throw new GitbookRuntimeException.NoHeaderFileException();
		}
		header = FileHelper.readToString(header_f);
		// copy the source to destination
		try 
		{
			FileHelper.copyFolder(new File(in_directory), new File(out_directory));
		}
		catch (IOException e) 
		{
			throw new GitbookRuntimeException.CopyException(in_directory, out_directory);
		}

		// find the summary file in the source folder
		findSummary();

		try
		{
			// add in the extra README.md from the gitbook folder itself (usually
			// serves as introduction or foreword or whatever
			buildForeword();

			// indexes all the markdown files based on the summary.md
			buildIndex();

			// converts markdown files to LaTeX using pandoc
			markdownToLatex();

			// outputs LaTeX file
			outputLatex();					
		}
		catch (IOException e)
		{
			throw new GitbookRuntimeException(e);
		}
	}

	/**
	 * Finds the summary.md file in the gitbook directory. Ignores case.
	 */
	private void findSummary() 
	{
		File out_dir = new File(out_directory);
		File[] listOfFiles = out_dir.listFiles();
		for (File file : listOfFiles)
		{
			if (file.getName().equalsIgnoreCase(s_summaryFilename)) 
			{
				summary = file;
			}
		}
	}

	/**
	 * Builds a LinkedHashMap of markdown files from the summary.md by
	 * extracting the relative file paths from between the () brackets in
	 * summary.md. Places a File class wrapper on each of these for convenience
	 * later.
	 * 
	 * @throws IOException
	 */
	private void buildIndex() throws IOException 
	{
		String summaryString = FileHelper.readToString(summary);
		Pattern pattern = Pattern.compile("[(](.*)[)]");
		Matcher matcher = pattern.matcher(summaryString);
		while (matcher.find()) 
		{
			String filename = out_directory + matcher.group(1);
			filename = filename.replaceAll("#.*", "");
			if (!index.containsKey(filename))
			{
				if (matcher.group().toLowerCase().indexOf("readme") > -1) 
				{
					index.put(filename, CHAPTER);
				}
				else 
				{
					index.put(filename, SUBCHAPTER);
				}				
			}
		}
	}

	/**
	 * Converts each of these markdown files into LaTeX using pandoc. Assumes
	 * that the directory pandoc resides in is /usr/local/bin/pandoc. To
	 * override that, change the static declaration at the top.
	 * 
	 * @throws IOException
	 * @throws InterruptedException
	 */
	private void markdownToLatex() throws IOException
	{
		for (String filename : index.keySet()) 
		{
			File markdown = new File(filename);
			superscriptSubscript(markdown);
			String[] command = new String[] { s_pandocPath, "-o",
					markdown.getAbsolutePath().replaceAll(".md", ".tex"),
					markdown.getAbsolutePath() };
			for (String param : command) 
			{
				System.out.print(param + " ");
			}
			System.out.println();
			CommandRunner runner = new CommandRunner(command);
			runner.run();
			System.out.println(runner.getString());
		}
	}

	/**
	 * Now the tricky thing about gitbook is that there is a readme.md in the
	 * gitbook directory itself (not as any chapter). It is sort of like an
	 * introduction. A chapter 0. We extract that markdown file and add that to
	 * the top of our book.
	 * 
	 * @throws IOException
	 */
	private void buildForeword() throws IOException 
	{
		File out_dir = new File(out_directory);
		File[] listOfFiles = out_dir.listFiles();
		for (File file : listOfFiles) 
		{
			if (file.getName().equalsIgnoreCase(s_chapterFilename)) 
			{
				index.put(file.getAbsolutePath(), CHAPTER);
			}
		}
	}

	/**
	 * Output the book.tex file that references each of the converted markdown
	 * files by using \include{filename} in LaTex
	 * 
	 * @throws IOException
	 */
	private void outputLatex() throws IOException 
	{
		File latex = new File(out_directory + "book.tex");
		FileWriter writer = new FileWriter(latex);
		String includes = "";
		for (String filename : index.keySet()) 
		{
			File markdown = new File(filename);
			File converted = new File(markdown.getAbsolutePath().replaceAll(".md", ".tex"));
			if (index.get(filename) == SUBCHAPTER) 
			{
				shift(converted);
			}
			// Make relative paths
			String path = converted.getAbsolutePath();
			String base = new File(out_directory).getAbsolutePath();
			String relative = new File(base).toURI().relativize(new File(path).toURI()).getPath();
			includes = includes + "\\\\include{" + relative.replaceAll(".tex", "") + "}" + "\n";
		}
		header = header.replaceAll("<CONTENT>", includes);
		writer.write(header);
		writer.close();
	}

	/**
	 * Now gitbook demands that even subchapters are titled using #Title (H1),
	 * hence if we convert naively using pandoc, each subchapter will become
	 * \section, which is screwed up. So we have to push each section in the
	 * subchapters down by one. We do that by replacing section{ with
	 * subsection{
	 * 
	 * @param converted
	 * @throws IOException
	 */
	private static void shift(File converted) throws IOException 
	{
		String file = FileHelper.readToString(converted);
		file = file.replaceAll("section\\{", "subsection\\{");
		FileWriter writer = new FileWriter(converted);
		writer.write(file);
		writer.close();
	}

	/**
	 * Gitbook and pandoc handles superscripts and subscripts differently (this
	 * is mainly for my own project). While Gitbook demands <sub>lorem</sub> as
	 * subscripts, Pandoc takes only ~lorem~. Hence, we replace accordingly.
	 * Same for superscript.
	 * 
	 * @param markdown
	 * @throws IOException
	 */
	private void superscriptSubscript(File markdown) throws IOException 
	{
		String file = FileHelper.readToString(markdown);
		file = file.replaceAll("<sub>", "~");
		file = file.replaceAll("</sub>", "~");
		file = file.replaceAll("<sup>", "^");
		file = file.replaceAll("</sup>", "^");
		FileWriter writer = new FileWriter(markdown);
		writer.write(file);
		writer.close();
	}

	/**
	 * Execute GitbookToPandoc
	 * 
	 * @param args The command line arguments
	 */
	public static void main(String[] args) 
	{
		if (!isPandocPresent())
		{
			System.err.println("Pandoc cannot be found on this system");
			System.exit(2);
		}
		CliParser parser = setupCli();
		ArgumentMap map = parser.parse(args);
		if (!map.hasOption("source") || !map.hasOption("dest"))
		{
			parser.printHelp("gitbook-pandoc - Converts Gitbook directory to LaTeX using Pandoc\nUsage: java -jar gitbook-pandoc.jar [options]\n\nOptions:", System.err);
			System.exit(1);
		}
		String in_directory = addSlash(map.getOptionValue("source"));
		String out_directory = addSlash(map.getOptionValue("dest"));
		GitbookToPandoc gtp = new GitbookToPandoc(in_directory, out_directory);
		try
		{
			gtp.run();
		}
		catch (GitbookRuntimeException e)
		{
			System.err.println(e.getMessage());
		}
	}
	
	/**
	 * Adds a trailing slash to a string, if the last character is not already
	 * a slash
	 * @param s The string
	 * @return
	 */
	protected static String addSlash(String s)
	{
		if (s.charAt(s.length() - 1) != '/') 
		{
			s = s + '/';
		}
		return s;
	}
	
	/**
	 * Sets up the command line parser
	 * @return The parser
	 */
	protected static CliParser setupCli()
	{
		CliParser parser = new CliParser();
		parser.addArgument(new Argument().withLongName("source").withShortName("s").withArgument("folder").withDescription("Folder containing the source files"));
		parser.addArgument(new Argument().withLongName("dest").withShortName("d").withArgument("folder").withDescription("Folder where the LaTeX files will be copied"));
		return parser;
	}
	
	/**
	 * Checks if pandoc is present by attempting to run it
	 * @return
	 */
	protected static boolean isPandocPresent()
	{
		CommandRunner runner = new CommandRunner(new String[] {s_pandocPath, "--version"});
		runner.run();
		return runner.getErrorCode() == 0;
	}
}