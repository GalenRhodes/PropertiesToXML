package com.projectgalen.utils.apps.prop2xml;

// ===========================================================================
//     PROJECT: PGUtils
//    FILENAME: PropertiesToXML.java
//         IDE: IntelliJ IDEA
//      AUTHOR: Galen Rhodes
//        DATE: February 01, 2023
//
// Copyright © 2023 Project Galen. All rights reserved.
//
// Permission to use, copy, modify, and distribute this software for any
// purpose with or without fee is hereby granted, provided that the above
// copyright notice and this permission notice appear in all copies.
//
// THE SOFTWARE IS PROVIDED "AS IS" AND THE AUTHOR DISCLAIMS ALL WARRANTIES
// WITH REGARD TO THIS SOFTWARE INCLUDING ALL IMPLIED WARRANTIES OF
// MERCHANTABILITY AND FITNESS. IN NO EVENT SHALL THE AUTHOR BE LIABLE FOR ANY
// SPECIAL, DIRECT, INDIRECT, OR CONSEQUENTIAL DAMAGES OR ANY DAMAGES
// WHATSOEVER RESULTING FROM LOSS OF USE, DATA OR PROFITS, WHETHER IN AN
// ACTION OF CONTRACT, NEGLIGENCE OR OTHER TORTIOUS ACTION, ARISING OUT OF OR
// IN CONNECTION WITH THE USE OR PERFORMANCE OF THIS SOFTWARE.
// ===========================================================================

import com.projectgalen.lib.utils.PGProperties;
import com.projectgalen.lib.utils.PGResourceBundle;
import org.jetbrains.annotations.NotNull;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@SuppressWarnings("SameParameterValue")
public final class PropertiesToXML {
    private static final Map<String, String> xmlChars              = new TreeMap<>();
    private static final PGResourceBundle    msgs                  = PGResourceBundle.getPGBundle("com.projectgalen.utils.apps.prop2xml.pg_messages");
    private static final PGProperties        xmlProps              = PGProperties.getXMLProperties("pg_props2xml.xml", PropertiesToXML.class);
    private static final Pattern             commentPattern        = Pattern.compile(xmlProps.getProperty("xml.comment.regexp"));
    private static final boolean             quotesInValues        = "true".equals(xmlProps.getProperty("xml.do.quotes.in.values"));
    private static final String              xmlQuote              = xmlProps.getProperty("xml.quote");
    private static final String              lineFormat            = xmlProps.getProperty("xml.format.line");
    private static final String              keyFormat             = xmlProps.getProperty("xml.format.key");
    private static final String              doubleDash            = xmlProps.getProperty("xml.comment.double.dash");
    private static final String              doubleDashReplacement = String.valueOf((char)Integer.parseInt(xmlProps.getProperty("xml.comment.dash.replacement", "8211"))).repeat(2);
    private static final String              tab                   = " ".repeat(Math.max(0, Integer.parseInt(xmlProps.getProperty("xml.indent.width", "4"))));

    public PropertiesToXML() { }

    public int run(String @NotNull ... args) {
        int errno = 0;

        File         outputDir = null;
        File         srcDir    = null;
        List<String> files     = new ArrayList<>();
        int          i         = 0;

        while(i < args.length) {
            String a = args[i++];

            switch(a) {
                case "--src-dir": {
                    String dir = ((i < args.length) ? args[i++].trim() : "");

                    if(dir.length() == 0) {
                        System.err.println(msgs.getString("msg.err.p2x.missing_src_dir"));
                        return 1;
                    }

                    srcDir = new File(dir);
                    break;
                }
                case "--dest-dir": {
                    String dir = ((i < args.length) ? args[i++].trim() : "");

                    if(dir.length() == 0) {
                        System.err.println(msgs.getString("msg.err.p2x.missing_dest_dir"));
                        return 1;
                    }

                    outputDir = new File(dir);
                    break;
                }
                case "--file": {
                    String file = ((i < args.length) ? args[i++].trim() : "");

                    if(file.length() == 0) {
                        System.err.println(msgs.getString("msg.err.p2x.missing_filename"));
                        return 1;
                    }

                    files.add(file);
                    break;
                }
                case "--": {
                    // Get the remaining arguments as files.
                    while(i < args.length) {
                        String file = args[i++].trim();
                        if(file.length() > 0) files.add(file);
                    }
                    break;
                }
                default:
                    System.err.println(msgs.format("msg.err.p2x.invalid_arg", a));
                    return 1;
            }
        }

        if(srcDir == null) srcDir = new File(".");

        if(files.isEmpty()) {
            System.err.println(msgs.getString("msg.err.p2x.no.props.file.specified"));
            return 1;
        }

        for(String inFilename : files) {
            String outFilename = String.format("%s.xml", getNameWithoutExt(inFilename));
            File   fileIn      = new File(srcDir, inFilename);
            File   fileOut     = new File(((outputDir == null) ? fileIn.getParentFile() : outputDir), outFilename);
            System.out.printf(msgs.getString("msg.convert1"), inFilename, outFilename);
            int e = convertFile(fileIn, fileOut);
            System.out.printf(msgs.getString("msg.convert2"), e);
            if(errno == 0) errno = e;
        }

        return errno;
    }

    private int convertFile(File fileIn, File fileOut) {
        Properties vals         = loadPropertyValues(fileIn);
        int        entryCount   = 0;
        int        commentCount = 0;

        try(BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(fileIn), StandardCharsets.ISO_8859_1))) {
            String line = reader.readLine();

            if(line != null) {
                try(PrintWriter writer = new PrintWriter(new BufferedWriter(new OutputStreamWriter(new FileOutputStream(fileOut), StandardCharsets.UTF_8)))) {
                    writeMultipleLines(writer, "xml.header", "");

                    do {
                        Matcher m = commentPattern.matcher(line);

                        if(m.matches()) {
                            writeComment(writer, m.group(1));
                            commentCount++;
                        }
                        else {
                            String[] z = line.split("\\s*=", 2);
                            if(z.length == 2) {
                                writeValue(writer, z[0].trim(), vals, quotesInValues);
                                entryCount++;
                            }
                            else {
                                writeComment(writer, line.trim());
                                commentCount++;
                            }
                        }

                        line = reader.readLine();
                    }
                    while(line != null);

                    writeMultipleLines(writer, "xml.footer", "");
                    writer.flush();
                }
            }

            System.out.printf(msgs.getString("msg.counts"), entryCount, commentCount);

            return 0;
        }
        catch(Exception e) {
            System.err.printf(msgs.getString("msg.err.p2x.error"), e);
            return 1;
        }
    }

    private @NotNull String escapeSpecialChars(@NotNull String str, boolean doQuotes) {
        StringBuilder sb = new StringBuilder();

        for(int i = 0, j = str.length(); i < j; i++) {
            char ch = str.charAt(i);
            if(ch == '"' && doQuotes) {
                sb.append(xmlQuote);
            }
            else if(ch < ' ') {
                sb.append(String.format("&#%d;", (int)ch));
            }
            else {
                String cs = (String.valueOf(ch));
                sb.append(Objects.toString(xmlChars.get(cs), cs));
            }
        }

        return sb.toString();
    }

    private @NotNull Properties loadPropertyValues(File fileIn) {
        Properties vals = new Properties();
        try(BufferedInputStream inputStream = new BufferedInputStream(new FileInputStream(fileIn))) {
            vals.load(inputStream);
        }
        catch(Exception e) {
            System.err.printf(msgs.getString("msg.err.p2x.error"), e);
            System.exit(1);
        }
        return vals;
    }

    private void writeComment(@NotNull PrintWriter writer, @NotNull String str) {
        writer.printf(lineFormat, tab, String.format(xmlProps.getProperty("xml.comment.format"), str.replace(doubleDash, doubleDashReplacement)));
    }

    private void writeMultipleLines(@NotNull PrintWriter writer, @NotNull String pfx, @NotNull String tab) {
        for(int i = 1; i < 100; i++) {
            String s = xmlProps.getProperty(String.format(keyFormat, pfx, i));
            if(s == null) return;
            writer.printf(lineFormat, tab, s);
        }
    }

    private void writeValue(@NotNull PrintWriter writer, @NotNull String rawKey, @NotNull Properties vals, boolean doQuotesInValues) {
        String key = escapeSpecialChars(rawKey, true);
        String val = vals.getProperty(key);
        if(val == null) writeComment(writer, key);
        else writer.printf(lineFormat, tab, String.format(xmlProps.getProperty((val.length() == 0) ? "xml.entry.novalue" : "xml.entry.value"), key, escapeSpecialChars(val, doQuotesInValues)));
    }

    public static void main(String[] args) {
        try {
            System.exit(new PropertiesToXML().run(args));
        }
        catch(Throwable t) {
            t.printStackTrace(System.err);
            System.exit(1);
        }
    }

    private static @NotNull String getNameWithoutExt(@NotNull String fn) {
        int i = fn.lastIndexOf('.');
        return ((i <= 0) ? fn : fn.substring(0, i));
    }

    static {
        for(String s : xmlProps.getProperty("xml.special.repl").split("\\s*,\\s*")) {
            String[] t = s.split("\\s*:\\s*", 2);
            if(t.length == 2) xmlChars.put(t[0], t[1]);
        }
    }
}
