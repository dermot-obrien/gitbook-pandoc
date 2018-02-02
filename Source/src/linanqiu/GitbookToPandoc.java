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
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Scanner;
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
	public static final String s_headerFilename = "body.tex";
	
	/**
	 * The name of the big Markdown file
	 */
	public static final String s_bigFilenameMarkdown = "all.temp.md";
	
	/**
	 * The name of the big LaTeX file
	 */
	public static final String s_bigFilenameLatex = "all.temp.tex";
	
	/**
	 * The name of the generated header file with all Pandoc declarations
	 */
	public static final String s_pandocIncludeFilename = "pandoc.inc.tex";

	private String in_directory;
	private String out_directory;
	private LinkedHashMap<String,Integer> index;
	private String m_outPrefix = "";

	private File summary;
	
	/**
	 * A list of LaTeX hacks
	 */
	protected List<LatexHack> m_latexHacks;
	
	/**
	 * A list of Markdown hacks
	 */
	protected List<MarkdownHack> m_markdownHacks;

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
	public GitbookToPandoc(String in_directory, String out_directory, String out_prefix)
	{
		super();
		this.in_directory = in_directory;
		this.out_directory = addSlash(out_directory + out_prefix);
		m_outPrefix = out_prefix;
		m_latexHacks = new LinkedList<LatexHack>();
		m_latexHacks.add(PromoteTitles.instance);
		m_latexHacks.add(FlattenImageLinks.instance);
		m_latexHacks.add(new RepositionImageUrls(out_directory, m_outPrefix));
		m_latexHacks.add(new InlineRegexReplace());
		m_latexHacks.add(new InlineRegexReplace());
		m_markdownHacks = new LinkedList<MarkdownHack>();
		m_markdownHacks.add(IndexReplace.instance);
		List<String[]> replacements = new ArrayList<String[]>();
		replacements.add(new String[]{".*", "GPGP\\index", "\\index"});
		RegexReplace rr = new RegexReplace(replacements);
		rr.useRegex(false);
		m_latexHacks.add(rr);
	}
	
	/**
	 * Adds a LaTeX hack to the list of post-processing objects
	 * @param hack The hack
	 */
	public void addLatexHack(LatexHack hack)
	{
		m_latexHacks.add(hack);
	}
	
	public void run() throws GitbookRuntimeException
	{
		index = new LinkedHashMap<String,Integer>();
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
			//buildForeword();

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
	protected void buildIndex() throws IOException 
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
		StringBuilder big_file = new StringBuilder();
		int total_files = index.size();
		int cur_file = 0;
		System.out.println();
		for (String filename : index.keySet()) 
		{
			cur_file++;
			File f = new File(filename);
			if (!f.exists())
			{
				System.err.println("File " + filename + " not found");
				continue;
			}
			big_file.append(FileHelper.readToString(f));
			big_file.append("\n");
			File markdown = new File(filename);
			superscriptSubscript(markdown);
			for (MarkdownHack h : m_markdownHacks)
			{
				h.hack(markdown);
			}
			String latex_filename = markdown.getAbsolutePath().replaceAll(".md", ".tex");
			String[] command = new String[] { s_pandocPath, "-o",
					latex_filename,
					markdown.getAbsolutePath() };
			System.out.print("\r " + cur_file + "/" + total_files + "  " + filename + "    ");
			CommandRunner runner = new CommandRunner(command);
			runner.run();
			String file_contents = FileHelper.readToString(new File(latex_filename));
			for (LatexHack hack : m_latexHacks)
			{
				file_contents = hack.hack(filename, file_contents);
			}
			FileHelper.writeFromString(new File(latex_filename), file_contents);
		}
		System.out.println();
		// Call pandoc one last time with the big file to get the headers
		writeHeaders(big_file);
	}
	
	protected void writeHeaders(StringBuilder big_file_contents) throws IOException
	{
		FileWriter fw = new FileWriter(new File(out_directory + s_bigFilenameMarkdown));
		fw.write(big_file_contents.toString());
		fw.close();
		CommandRunner pandoc_runner = new CommandRunner(new String[]{s_pandocPath, "-o", out_directory + s_bigFilenameLatex, "--standalone", out_directory + s_bigFilenameMarkdown});
		pandoc_runner.run();
		Scanner scan = new Scanner(new File(out_directory + s_bigFilenameLatex));
		StringBuilder out = new StringBuilder();
		while (scan.hasNextLine())
		{
			String line = scan.nextLine();
			if (line.contains("documentclass"))
			{
				continue;
			}
			if (line.contains("\\begin{document}"))
			{
				break;
			}
			out.append(line).append("\n");
		}
		scan.close();
		fw = new FileWriter(new File(out_directory + s_pandocIncludeFilename));
		fw.write(out.toString());
		fw.close();
		System.out.println("Wrote headers to " + out_directory + s_pandocIncludeFilename);
	}

	/**
	 * Now the tricky thing about gitbook is that there is a readme.md in the
	 * gitbook directory itself (not as any chapter). It is sort of like an
	 * introduction. A chapter 0. We extract that markdown file and add that to
	 * the top of our book.
	 * 
	 * @throws IOException
	 */
	protected void buildForeword() throws IOException 
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
		File latex = new File(out_directory + s_headerFilename);
		FileWriter writer = new FileWriter(latex);
		StringBuilder includes = new StringBuilder();
		StringBuilder graphicspath = new StringBuilder();
		graphicspath.append("\\graphicspath{");
		for (String filename : index.keySet()) 
		{
			if (filename.contains("README.md"))
			{
				String n_filename = filename.replaceAll("\\\\", "/");
				int pos = n_filename.indexOf(out_directory);
				graphicspath.append("{").append(n_filename.substring(pos + out_directory.length()).replace("/README.md", "")).append("}");
			}
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
			includes.append("\\subimport{" + addSlash(m_outPrefix) + "}{" + relative.replaceAll(".tex", "") + "}" + "\n");
		}
		graphicspath.append("}\n");
		writer.write(graphicspath.toString());
		writer.write(includes.toString());
		writer.close();
	}

	/**
	 * Now gitbook demands that even subchapters are titled using #Title (H1),
	 * hence if we convert naively using pandoc, each subchapter will become
	 * <tt>\section</tt>, which is screwed up. So we have to push each section in the
	 * subchapters down by one. We do that by replacing <tt>section{</tt> with
	 * <tt>subsection{</tt>
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
		PrintStream ps = new PrintStream(new FileOutputStream(markdown));
		ps.print(file);
		ps.close();
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
		System.out.println("gitbook-pandoc - Converts GitBook directory to LaTeX using Pandoc\n(C) 2017 Sylvain Hallé and linanqiu\n");
		String in_directory = addSlash(map.getOptionValue("source"));
		String out_directory = addSlash(map.getOptionValue("dest"));
		String out_prefix = "";
		if (map.hasOption("prefix"))
		{
			out_prefix = map.getOptionValue("prefix");
		}
		GitbookToPandoc gtp = new GitbookToPandoc(in_directory, out_directory, out_prefix);
		if (map.hasOption("replace-from"))
		{
			String filename = map.getOptionValue("replace-from");
			try
			{
				Scanner sc = new Scanner(new File(filename));
				gtp.addLatexHack(new RegexReplace(sc));
				sc.close();
				System.out.println("Using replacements from " + filename);
			}
			catch (FileNotFoundException e) 
			{
				System.err.println("Replacement file " + filename + " not found");
				System.exit(2);
			}
		}
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
		if (s.isEmpty())
		{
			return "/";
		}
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
		parser.addArgument(new Argument().withLongName("prefix").withShortName("p").withArgument("prefix").withDescription("Folder where the LaTeX files will be copied"));
		parser.addArgument(new Argument().withLongName("replace-from").withShortName("r").withArgument("file").withDescription("Apply regex replacements taken from file"));
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