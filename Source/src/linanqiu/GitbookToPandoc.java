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

	private String in_directory;
	private String out_directory;
	private String header;

	private LinkedHashMap<File, Integer> index;

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
	 * @throws IOException
	 * @throws InterruptedException
	 */
	public GitbookToPandoc(String in_directory, String out_directory)
			throws IOException, InterruptedException {
		this.in_directory = in_directory;
		this.out_directory = out_directory;

	}
	
	public void run() throws IOException, InterruptedException
	{
		index = new LinkedHashMap<File, Integer>();

		// read in the header file
		header = FileHelper.readToString(new File("header.tex"));

		// copy the source to destination
		FileHelper.copyFolder(new File(in_directory), new File(out_directory));

		// find the summary file in the source folder
		findSummary();

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

	/**
	 * Finds the summary.md file in the gitbook directory. Ignores case.
	 */
	private void findSummary() 
	{
		File out_dir = new File(out_directory);
		File[] listOfFiles = out_dir.listFiles();
		for (File file : listOfFiles) {
			if (file.getName().equalsIgnoreCase(s_summaryFilename)) {
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
			File markdownFile = new File(out_directory + matcher.group(1));
			if (matcher.group().toLowerCase().indexOf("readme") > -1) 
			{
				index.put(markdownFile, CHAPTER);
			}
			else 
			{
				index.put(markdownFile, SUBCHAPTER);
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
	private void markdownToLatex() throws IOException, InterruptedException {

		for (File markdown : index.keySet()) 
		{
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
				index.put(file, CHAPTER);
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
		for (File markdown : index.keySet()) 
		{
			File converted = new File(markdown.getAbsolutePath().replaceAll(".md", ".tex"));
			if (index.get(markdown) == SUBCHAPTER) 
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
	 * @param args
	 * @throws IOException
	 * @throws InterruptedException
	 */
	public static void main(String[] args) throws IOException, InterruptedException 
	{
		if (args.length != 2) 
		{
			System.out
			.println("Usage: java GitbookToPandoc sourcefolder destinationfolder");
			System.exit(1);
		}
		String in_directory = args[0];
		String out_directory = args[1];

		// adds slash behind directory
		if (in_directory.charAt(in_directory.length() - 1) != '/') 
		{
			in_directory = in_directory + '/';
		}
		if (out_directory.charAt(out_directory.length() - 1) != '/') 
		{
			out_directory = out_directory + '/';
		}
		GitbookToPandoc gtp = new GitbookToPandoc(in_directory, out_directory);
		gtp.run();
	}
}